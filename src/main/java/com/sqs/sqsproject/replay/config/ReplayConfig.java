package com.sqs.sqsproject.replay.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration for the SQS replay functionality.
 * Enables scheduling for automatic replay at fixed intervals.
 */
@Configuration
@EnableConfigurationProperties(ReplayProperties.class)
@EnableScheduling
public class ReplayConfig {
    // Additional beans will be added as needed
}