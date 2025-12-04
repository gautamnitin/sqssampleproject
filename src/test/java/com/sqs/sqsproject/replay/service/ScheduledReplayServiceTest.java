package com.sqs.sqsproject.replay.service;

import com.sqs.sqsproject.replay.config.ReplayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduledReplayServiceTest {

    @Mock
    private ReplayService replayService;

    @Mock
    private ReplayProperties replayProperties;

    private ScheduledReplayService scheduledReplayService;
    private ReplayProperties.ScheduledReplayConfig scheduledConfig;
    private Map<String, ReplayProperties.QueueConfig> queuesMap;

    @BeforeEach
    void setUp() {
        scheduledConfig = new ReplayProperties.ScheduledReplayConfig();
        queuesMap = new HashMap<>();
        
        // Configure the mock properties with lenient stubs to avoid UnnecessaryStubbingException
        Mockito.lenient().when(replayProperties.getScheduled()).thenReturn(scheduledConfig);
        Mockito.lenient().when(replayProperties.getQueues()).thenReturn(queuesMap);
        
        scheduledReplayService = new ScheduledReplayService(replayService, replayProperties);
    }

    @Test
    void shouldSkipWhenReplayDisabled() {
        // Given
        when(replayProperties.isEnabled()).thenReturn(false);
        scheduledConfig.setEnabled(true);
        
        // When
        scheduledReplayService.replayEligibleQueues();
        
        // Then
        verify(replayService, never()).replayMessages(anyString(), anyInt());
    }

    @Test
    void shouldSkipWhenScheduledReplayDisabled() {
        // Given
        when(replayProperties.isEnabled()).thenReturn(true);
        scheduledConfig.setEnabled(false);
        
        // When
        scheduledReplayService.replayEligibleQueues();
        
        // Then
        verify(replayService, never()).replayMessages(anyString(), anyInt());
    }

    @Test
    void shouldSkipWhenNoEligibleQueues() {
        // Given
        when(replayProperties.isEnabled()).thenReturn(true);
        scheduledConfig.setEnabled(true);
        scheduledConfig.setEligibleQueueIds("");
        
        // When
        scheduledReplayService.replayEligibleQueues();
        
        // Then
        verify(replayService, never()).replayMessages(anyString(), anyInt());
    }

    @Test
    void shouldProcessEligibleQueues() {
        // Given
        when(replayProperties.isEnabled()).thenReturn(true);
        scheduledConfig.setEnabled(true);
        scheduledConfig.setMaxMessages(10);
        
        // Add eligible queues
        scheduledConfig.setEligibleQueueIds("employee,order");
        
        // Add queue configurations
        ReplayProperties.QueueConfig employeeConfig = new ReplayProperties.QueueConfig();
        ReplayProperties.QueueConfig orderConfig = new ReplayProperties.QueueConfig();
        queuesMap.put("employee", employeeConfig);
        queuesMap.put("order", orderConfig);
        
        when(replayService.replayMessages("employee", 10)).thenReturn(5);
        when(replayService.replayMessages("order", 10)).thenReturn(3);
        
        // When
        scheduledReplayService.replayEligibleQueues();
        
        // Then
        verify(replayService).replayMessages("employee", 10);
        verify(replayService).replayMessages("order", 10);
    }

    @Test
    void shouldHandleExceptionForOneQueueAndContinueWithOthers() {
        // Given
        when(replayProperties.isEnabled()).thenReturn(true);
        scheduledConfig.setEnabled(true);
        scheduledConfig.setMaxMessages(10);
        
        // Add eligible queues
        scheduledConfig.setEligibleQueueIds("employee,order");
        
        // Add queue configurations
        ReplayProperties.QueueConfig employeeConfig = new ReplayProperties.QueueConfig();
        ReplayProperties.QueueConfig orderConfig = new ReplayProperties.QueueConfig();
        queuesMap.put("employee", employeeConfig);
        queuesMap.put("order", orderConfig);
        
        // First queue throws exception, second one succeeds
        when(replayService.replayMessages("employee", 10)).thenThrow(new RuntimeException("Test exception"));
        when(replayService.replayMessages("order", 10)).thenReturn(3);
        
        // When
        scheduledReplayService.replayEligibleQueues();
        
        // Then - should continue processing after exception
        verify(replayService).replayMessages("employee", 10);
        verify(replayService).replayMessages("order", 10);
    }

    @Test
    void shouldSkipNonExistentQueueConfigurations() {
        // Given
        when(replayProperties.isEnabled()).thenReturn(true);
        scheduledConfig.setEnabled(true);
        
        // Add eligible queues including one that doesn't exist
        scheduledConfig.setEligibleQueueIds("employee,nonexistent");
        
        // Only add one queue configuration
        ReplayProperties.QueueConfig employeeConfig = new ReplayProperties.QueueConfig();
        queuesMap.put("employee", employeeConfig);
        
        // When
        scheduledReplayService.replayEligibleQueues();
        
        // Then
        verify(replayService, times(1)).replayMessages(anyString(), anyInt());
        verify(replayService).replayMessages("employee", scheduledConfig.getMaxMessages());
    }
}