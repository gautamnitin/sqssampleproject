package com.sqs.sqsproject.sqs;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClientBuilder;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(SqsProperties.class)
public class SqsConfig {

    @Bean
    public SqsAsyncClient sqsAsyncClient(SqsProperties props) {
        SqsAsyncClientBuilder builder = SqsAsyncClient.builder()
                .region(Region.of(props.getRegion()));

        boolean hasExplicitCreds = hasExplicitCreds(props);

        if (props.getEndpoint() != null && !props.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(props.getEndpoint()));
            // For LocalStack: prefer provided creds if set; otherwise use test/test
            builder.credentialsProvider(hasExplicitCreds ? buildExplicitProvider(props) : staticTestCredentials());
        } else {
            // Real AWS: prefer explicit creds if set; otherwise fall back to default provider chain
            builder.credentialsProvider(hasExplicitCreds ? buildExplicitProvider(props) : DefaultCredentialsProvider.create());
        }
        return builder.build();
    }

    private boolean hasExplicitCreds(SqsProperties props) {
        return props.getAccessKeyId() != null && !props.getAccessKeyId().isBlank()
                && props.getSecretAccessKey() != null && !props.getSecretAccessKey().isBlank();
    }

    private AwsCredentialsProvider buildExplicitProvider(SqsProperties props) {
        if (props.getSessionToken() != null && !props.getSessionToken().isBlank()) {
            AwsSessionCredentials sessionCreds = AwsSessionCredentials.create(
                    props.getAccessKeyId(), props.getSecretAccessKey(), props.getSessionToken());
            return StaticCredentialsProvider.create(sessionCreds);
        }
        AwsBasicCredentials basicCreds = AwsBasicCredentials.create(props.getAccessKeyId(), props.getSecretAccessKey());
        return StaticCredentialsProvider.create(basicCreds);
    }

    private AwsCredentialsProvider staticTestCredentials() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"));
    }
}
