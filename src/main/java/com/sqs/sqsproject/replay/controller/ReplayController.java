package com.sqs.sqsproject.replay.controller;

import com.sqs.sqsproject.replay.config.ReplayProperties;
import com.sqs.sqsproject.replay.service.ReplayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for SQS message replay operations.
 */
@RestController
@RequestMapping("/api/sqs/replay")
@RequiredArgsConstructor
@Slf4j
public class ReplayController {

    private final ReplayService replayService;
    private final ReplayProperties replayProperties;

    /**
     * Get all available queue configurations for replay.
     *
     * @return a map of queue configuration IDs to their details
     */
    @GetMapping("/queues")
    public ResponseEntity<Map<String, Map<String, String>>> getQueueConfigurations() {
        if (!replayProperties.isEnabled()) {
            return ResponseEntity.ok(Map.of("message", Map.of("status", "Replay functionality is disabled")));
        }

        Map<String, Map<String, String>> result = new HashMap<>();
        
        replayProperties.getQueues().forEach((id, config) -> {
            Map<String, String> details = new HashMap<>();
            details.put("sourceQueue", config.getSourceQueue());
            details.put("targetQueue", config.getTargetQueue());
            details.put("messageType", config.getMessageType());
            details.put("transformerClass", config.getTransformerClass() != null ? 
                    config.getTransformerClass() : "Default (No transformation)");
            details.put("batchSize", String.valueOf(config.getBatchSize()));
            
            result.put(id, details);
        });
        
        return ResponseEntity.ok(result);
    }

    /**
     * Replay messages from a source queue to a target queue.
     *
     * @param queueConfigId the ID of the queue configuration to use
     * @param maxMessages   the maximum number of messages to replay (optional)
     * @return the result of the replay operation
     */
    @PostMapping("/{queueConfigId}")
    public ResponseEntity<Map<String, Object>> replayMessages(
            @PathVariable String queueConfigId,
            @RequestParam(required = false) Integer maxMessages) {
        
        if (!replayProperties.isEnabled()) {
            return ResponseEntity.ok(Map.of("status", "error", "message", "Replay functionality is disabled"));
        }

        if (!replayProperties.getQueues().containsKey(queueConfigId)) {
            return ResponseEntity.badRequest().body(
                    Map.of("status", "error", 
                           "message", "Queue configuration not found: " + queueConfigId,
                           "availableConfigs", replayProperties.getQueues().keySet())
            );
        }

        try {
            int processedCount = replayService.replayMessages(queueConfigId, maxMessages);
            
            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("queueConfigId", queueConfigId);
            result.put("messagesProcessed", processedCount);
            
            ReplayProperties.QueueConfig config = replayProperties.getQueues().get(queueConfigId);
            result.put("sourceQueue", config.getSourceQueue());
            result.put("targetQueue", config.getTargetQueue());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to replay messages", e);
            return ResponseEntity.internalServerError().body(
                    Map.of("status", "error", 
                           "message", "Failed to replay messages: " + e.getMessage())
            );
        }
    }
}