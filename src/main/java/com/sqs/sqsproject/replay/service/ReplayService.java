package com.sqs.sqsproject.replay.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sqs.sqsproject.replay.config.ReplayProperties;
import com.sqs.sqsproject.replay.transform.MessageTransformer;
import com.sqs.sqsproject.replay.util.ClassLoaderUtil;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service for replaying messages from a DLQ to a target queue.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReplayService {

    private final SqsAsyncClient sqsAsyncClient;
    private final SqsTemplate sqsTemplate;
    private final ObjectMapper objectMapper;
    private final ClassLoaderUtil classLoaderUtil;
    private final ReplayProperties replayProperties;

    /**
     * Replays messages from a source queue (DLQ) to a target queue.
     *
     * @param queueConfigId the ID of the queue configuration to use
     * @param maxMessages   the maximum number of messages to replay (optional, defaults to 10)
     * @return the number of messages replayed
     */
    public int replayMessages(String queueConfigId, Integer maxMessages) {
        if (!replayProperties.isEnabled()) {
            log.warn("Replay functionality is disabled");
            return 0;
        }

        ReplayProperties.QueueConfig queueConfig = replayProperties.getQueues().get(queueConfigId);
        if (queueConfig == null) {
            log.error("Queue configuration not found for ID: {}", queueConfigId);
            throw new IllegalArgumentException("Queue configuration not found: " + queueConfigId);
        }

        boolean replayAll = maxMessages != null && maxMessages <= 0;
        int limit = replayAll ? Integer.MAX_VALUE : (maxMessages != null ? maxMessages : queueConfig.getBatchSize());
        if (replayAll) {
            log.info("Replaying ALL available messages from {} to {}", queueConfig.getSourceQueue(), queueConfig.getTargetQueue());
        } else {
            log.info("Replaying up to {} messages from {} to {}", limit, queueConfig.getSourceQueue(), queueConfig.getTargetQueue());
        }

        try {
            // Load the message type class
            Class<?> messageTypeClass = classLoaderUtil.loadClass(queueConfig.getMessageType());
            
            // Get the appropriate transformer (either by bean name or by class name)
            MessageTransformer<?> transformer = classLoaderUtil.getTransformer(
                    queueConfig.getTransformerClass(), messageTypeClass);
            
            // Process the messages with the correct types
            return processMessages(queueConfig, messageTypeClass, transformer, limit);
        } catch (ClassNotFoundException e) {
            log.error("Failed to load message type class: {}", queueConfig.getMessageType(), e);
            throw new RuntimeException("Failed to load message type class", e);
        }
    }

    /**
     * Process messages with the specified type and transformer.
     *
     * @param queueConfig     the queue configuration
     * @param messageTypeClass the class of the message type
     * @param transformer     the transformer to use
     * @param maxMessages     the maximum number of messages to process
     * @param <T>             the message type
     * @return the number of messages processed
     */
    @SuppressWarnings("unchecked")
    private <T> int processMessages(ReplayProperties.QueueConfig queueConfig, Class<?> messageTypeClass,
                                   MessageTransformer<?> transformer, int maxMessages) {
        try {
            int processedTotal = 0;
            int remaining = maxMessages;

            while (processedTotal < maxMessages) {
                int perRequest = Math.min(remaining, 10); // SQS hard limit
                if (perRequest <= 0) {
                    break;
                }

                // Receive a single batch from the source queue (streaming, not accumulating)
                List<Message> batch = receiveMessages(queueConfig.getSourceQueue(), perRequest);
                if (batch == null || batch.isEmpty()) {
                    if (processedTotal == 0) {
                        log.info("No messages found in source queue: {}", queueConfig.getSourceQueue());
                    }
                    break; // queue drained for now
                }

                List<String> processedReceiptHandles = new ArrayList<>();

                // Process each message in the batch
                for (Message message : batch) {
                    try {
                        // Convert the message body to the target type
                        T typedMessage = (T) objectMapper.readValue(message.body(), messageTypeClass);

                        // Transform the message
                        T transformedMessage = (T) ((MessageTransformer<T>) transformer).transform(typedMessage);

                        // Send the transformed message to the target queue
                        sqsTemplate.send(builder ->
                                builder.queue(queueConfig.getTargetQueue())
                                        .payload(transformedMessage)
                        );

                        // Mark as processed
                        processedReceiptHandles.add(message.receiptHandle());
                        processedTotal++;
                        remaining = Math.max(0, maxMessages - processedTotal);

                        if (processedTotal % 1000 == 0) {
                            log.info("Replayed {} messages so far from {} to {}", processedTotal, queueConfig.getSourceQueue(), queueConfig.getTargetQueue());
                        } else {
                            log.debug("Successfully replayed message: {}", message.messageId());
                        }

                        // Stop early if we've reached the requested limit within this batch
                        if (processedTotal >= maxMessages) {
                            // We will delete processed ones and exit the outer loop
                            continue;
                        }
                    } catch (Exception e) {
                        log.error("Failed to process message: {}", message.messageId(), e);
                    }
                }

                // Delete successfully processed messages from the source queue (per batch)
                if (!processedReceiptHandles.isEmpty()) {
                    deleteMessages(queueConfig.getSourceQueue(), batch, processedReceiptHandles);
                }

                // If we received fewer messages than requested, the queue is likely drained for now
                if (batch.size() < perRequest) {
                    break;
                }
            }

            log.info("Successfully replayed {} message(s) from {} to {}", processedTotal, queueConfig.getSourceQueue(), queueConfig.getTargetQueue());
            return processedTotal;
        } catch (Exception e) {
            log.error("Failed to process messages", e);
            throw new RuntimeException("Failed to process messages", e);
        }
    }

    /**
     * Receive messages from the specified queue.
     *
     * @param queueName   the name of the queue
     * @param maxMessages the maximum number of messages to receive
     * @return the list of received messages
     */
    private List<Message> receiveMessages(String queueName, int maxMessages) {
        List<Message> allMessages = new ArrayList<>();
        try {
            // SQS returns up to 10 messages per ReceiveMessage call. Loop until we collect the requested amount
            // or there are no more messages available at the moment.
            while (allMessages.size() < maxMessages) {
                int remaining = maxMessages - allMessages.size();
                int perRequest = Math.min(remaining, 10); // SQS hard limit per request

                if (perRequest <= 0) {
                    break;
                }

                ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                        .queueUrl(queueName)
                        .maxNumberOfMessages(perRequest)
                        .waitTimeSeconds(5)
                        .build();

                List<Message> batch = sqsAsyncClient.receiveMessage(receiveRequest)
                        .get()
                        .messages();

                if (batch == null || batch.isEmpty()) {
                    // No more messages available right now
                    break;
                }

                allMessages.addAll(batch);

                // If fewer than requested came back, likely queue is drained for now
                if (batch.size() < perRequest) {
                    break;
                }
            }

            return allMessages;
        } catch (Exception e) {
            log.error("Failed to receive messages from queue: {}", queueName, e);
            throw new RuntimeException("Failed to receive messages", e);
        }
    }

    /**
     * Delete messages from the specified queue.
     *
     * @param queueName         the name of the queue
     * @param messages          the messages to delete
     * @param receiptHandles    the receipt handles of messages to delete
     */
    private void deleteMessages(String queueName, List<Message> messages, List<String> receiptHandles) {
        try {
            List<DeleteMessageBatchRequestEntry> entries = new ArrayList<>();
            
            for (int i = 0; i < receiptHandles.size(); i++) {
                String receiptHandle = receiptHandles.get(i);
                String messageId = messages.stream()
                        .filter(m -> m.receiptHandle().equals(receiptHandle))
                        .map(Message::messageId)
                        .findFirst()
                        .orElse("unknown-" + i);
                
                entries.add(DeleteMessageBatchRequestEntry.builder()
                        .id(messageId)
                        .receiptHandle(receiptHandle)
                        .build());
            }
            
            if (!entries.isEmpty()) {
                // SQS DeleteMessageBatch supports up to 10 entries per request. Send in chunks.
                int chunkSize = 10;
                for (int start = 0; start < entries.size(); start += chunkSize) {
                    int end = Math.min(start + chunkSize, entries.size());
                    List<DeleteMessageBatchRequestEntry> chunk = entries.subList(start, end);

                    DeleteMessageBatchRequest deleteRequest = DeleteMessageBatchRequest.builder()
                            .queueUrl(queueName)
                            .entries(chunk)
                            .build();
                    
                    CompletableFuture<Void> future = sqsAsyncClient.deleteMessageBatch(deleteRequest)
                            .thenAccept(response -> {
                                if (!response.failed().isEmpty()) {
                                    log.warn("Failed to delete {} messages", response.failed().size());
                                    response.failed().forEach(failed -> 
                                        log.warn("Failed to delete message: {} - {}", failed.id(), failed.code())
                                    );
                                }
                            });
                    
                    // Wait for deletion of this chunk to complete before moving to next
                    future.join();
                }
            }
        } catch (Exception e) {
            log.error("Failed to delete messages from queue: {}", queueName, e);
            // Continue execution even if deletion fails
        }
    }
}