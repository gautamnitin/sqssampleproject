package com.sqs.sqsproject.replay.transform;

import org.springframework.stereotype.Component;

/**
 * Default implementation of MessageTransformer that doesn't modify messages.
 * This transformer simply returns the original message without any changes.
 *
 * @param <T> the type of message
 */
@Component
public class NoOpMessageTransformer<T> implements MessageTransformer<T> {
    
    /**
     * Returns the original message without any transformation.
     *
     * @param message the message to transform
     * @return the original message
     */
    @Override
    public T transform(T message) {
        return message;
    }
}