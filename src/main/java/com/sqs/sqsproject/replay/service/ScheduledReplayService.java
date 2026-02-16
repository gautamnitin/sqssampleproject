package com.sqs.sqsproject.replay.service;

import com.sqs.sqsproject.replay.config.ReplayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Service that automatically replays messages from DLQs at fixed intervals.
 * Only queue configurations that are explicitly enabled for scheduled replay will be processed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledReplayService {

    private final ReplayService replayService;
    private final ReplayProperties replayProperties;

    /**
     * Scheduled method that runs at a fixed rate to replay messages from eligible DLQs.
     * The rate, initial delay, and eligible queues are configurable via properties.
     */
    @Scheduled(
            fixedRateString = "${app.sqs.replay.scheduled.fixed-rate-ms:300000}",
            initialDelayString = "${app.sqs.replay.scheduled.initial-delay-ms:60000}"
    )
    public void replayEligibleQueues() {
        ReplayProperties.ScheduledReplayConfig config = replayProperties.getScheduled();
        
        // Skip if replay functionality or scheduled replay is disabled
        if (!replayProperties.isEnabled() || !config.isEnabled()) {
            log.debug("Scheduled replay is disabled, skipping execution");
            return;
        }

        // Skip if no eligible queues are configured
        if (config.getEligibleQueueIds().isEmpty()) {
            log.debug("No eligible queues configured for scheduled replay");
            return;
        }

        log.info("Starting scheduled replay for {} eligible queue(s)", config.getEligibleQueueIds().size());
        
        // Process each eligible queue
        for (String queueId : config.getEligibleQueueIds()) {
            try {
                // Skip if queue configuration doesn't exist
                if (!replayProperties.getQueues().containsKey(queueId)) {
                    log.warn("Queue configuration not found for ID: {}, skipping", queueId);
                    continue;
                }
                
                log.info("Processing scheduled replay for queue: {}", queueId);
                Integer max = config.isReplayAll() ? -1 : config.getMaxMessages();
                int processedCount = replayService.replayMessages(queueId, max);
                log.info("Scheduled replay for queue {} processed {} message(s)", queueId, processedCount);
                
            } catch (Exception e) {
                // Log error but continue with next queue
                log.error("Failed to process scheduled replay for queue: {}", queueId, e);
            }
        }
        
        log.info("Completed scheduled replay execution");
    }
}