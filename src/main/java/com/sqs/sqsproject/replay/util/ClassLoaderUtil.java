package com.sqs.sqsproject.replay.util;

import com.sqs.sqsproject.replay.transform.MessageTransformer;
import com.sqs.sqsproject.replay.transform.NoOpMessageTransformer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Utility class for dynamically loading classes based on their fully qualified names.
 */
@Slf4j
@Component
public class ClassLoaderUtil {

    /**
     * Loads a class by its fully qualified name.
     *
     * @param className the fully qualified class name
     * @param <T>       the type of class to load
     * @return the loaded class
     * @throws ClassNotFoundException if the class cannot be found
     */
    @SuppressWarnings("unchecked")
    public <T> Class<T> loadClass(String className) throws ClassNotFoundException {
        try {
            return (Class<T>) Class.forName(className);
        } catch (ClassNotFoundException e) {
            log.error("Failed to load class: {}", className, e);
            throw e;
        }
    }

    /**
     * Creates a transformer instance for the specified class.
     *
     * @param transformerClassName the fully qualified class name of the transformer
     * @param messageType         the message type class
     * @param <T>                 the type of message
     * @return the transformer instance
     */
    @SuppressWarnings("unchecked")
    public <T> MessageTransformer<T> createTransformer(String transformerClassName, Class<T> messageType) {
        if (transformerClassName == null || transformerClassName.isBlank()) {
            log.info("No transformer specified, using NoOpMessageTransformer");
            return new NoOpMessageTransformer<>();
        }

        try {
            Class<?> transformerClass = loadClass(transformerClassName);
            if (!MessageTransformer.class.isAssignableFrom(transformerClass)) {
                log.error("Class {} does not implement MessageTransformer interface", transformerClassName);
                return new NoOpMessageTransformer<>();
            }

            return (MessageTransformer<T>) transformerClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            log.error("Failed to create transformer instance: {}", transformerClassName, e);
            return new NoOpMessageTransformer<>();
        }
    }
}