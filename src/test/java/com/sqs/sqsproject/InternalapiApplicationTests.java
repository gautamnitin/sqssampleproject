package com.sqs.sqsproject;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.cloud.aws.sqs.listener.auto-startup=false",
        "app.sqs.enabled=false"
})
class InternalapiApplicationTests {

	@Test
	void contextLoads() {
	}

}
