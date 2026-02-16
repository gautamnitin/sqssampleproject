package com.sqs.sqsproject.replay.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Configuration properties for the SQS replay functionality.
 */
@Data
@ConfigurationProperties(prefix = "app.sqs.replay")
public class ReplayProperties {
    /**
     * Enable/disable the replay functionality
     */
    private boolean enabled = true;
    
    /**
     * Scheduled replay configuration
     */
    private ScheduledReplayConfig scheduled = new ScheduledReplayConfig();
    
    /**
     * Map of queue configurations where key is a unique identifier and value is the queue configuration
     */
    private Map<String, QueueConfig> queues = new HashMap<>();
    
    /**
     * Configuration for scheduled replay
     */
    @Data
    public static class ScheduledReplayConfig {
        /**
         * Enable/disable scheduled replay
         */
        private boolean enabled = false;
        
        /**
         * Fixed rate in milliseconds for scheduled replay
         */
        private long fixedRateMs = 300000; // Default: 5 minutes
        
        /**
         * Initial delay in milliseconds before first execution
         */
        private long initialDelayMs = 60000; // Default: 1 minute
        
        /**
         * Maximum number of messages to process in each scheduled run
         */
        private int maxMessages = 50;
        
        /**
         * When true, the scheduled job will attempt to replay all available messages
         * in the eligible queues (drain the DLQ) instead of limiting to maxMessages.
         */
        private boolean replayAll = false;
        
        /**
         * Set of queue configuration IDs that are eligible for scheduled replay
         */
        private Set<String> eligibleQueueIds = new HashSet<>();
        
        /**
         * Setter for eligibleQueueIds that accepts a comma-separated string
         * This allows setting the property using kebab-case in application.properties
         * 
         * @param queueIds comma-separated list of queue IDs
         */
        public void setEligibleQueueIds(String queueIds) {
            if (queueIds != null && !queueIds.isBlank()) {
                String[] ids = queueIds.split(",");
                for (String id : ids) {
                    this.eligibleQueueIds.add(id.trim());
                }
            }
        }
    }
    
    /**
     * Configuration for a queue pair (DLQ and target queue)
     */
    @Data
    public static class QueueConfig {
        /**
         * Source DLQ name
         */
        private String sourceQueue;
        
        /**
         * Target queue name where messages will be replayed
         */
        private String targetQueue;
        
        /**
         * Fully qualified class name of the message type
         */
        private String messageType;
        
        /**
         * Fully qualified class name of the transformer to use (optional)
         * If not specified, messages will be replayed without transformation
         */
        private String transformerClass;
        
        /**
         * Maximum number of messages to process in a single batch
         */
        private int batchSize = 10;
    }
}