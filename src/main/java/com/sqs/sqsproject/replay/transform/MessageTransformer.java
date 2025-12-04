package com.sqs.sqsproject.replay.transform;

/**
 * Interface for transforming messages before replaying them to the target queue.
 * Implementations can modify messages as needed before they are sent to the target queue.
 *
 * @param <T> the type of message to transform
 */
public interface MessageTransformer<T> {
    
    /**
     * Transform a message before replaying it to the target queue.
     *
     * @param message the message to transform
     * @return the transformed message
     */
    T transform(T message);
}