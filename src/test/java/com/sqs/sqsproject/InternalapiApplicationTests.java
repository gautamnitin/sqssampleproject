package com.sqs.sqsproject;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        // Disable SQS during tests
        "spring.cloud.aws.sqs.listener.auto-startup=false",
        "app.sqs.enabled=false",
        // Use in-memory H2 database for tests only
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class InternalapiApplicationTests {

    @Test
    void contextLoads() {
    }

}
