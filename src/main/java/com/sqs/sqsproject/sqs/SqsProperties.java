package com.sqs.sqsproject.sqs;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.sqs")
public class SqsProperties {
    /** AWS region, e.g. us-east-1 */
    private String region = "us-east-1";
    /** Optional endpoint override for LocalStack, e.g. http://localhost:4566 */
    private String endpoint;
    /** Employee queue name */
    private String employeeQueue;

    /** Optional explicit credentials (primarily for dev/testing). If not set, DefaultCredentialsProvider will be used. */
    private String accessKeyId;
    private String secretAccessKey;
    private String sessionToken;
}
