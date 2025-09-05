package com.sqs.sqsproject.sqs;

import com.sqs.sqsproject.employee.Employee;
import com.sqs.sqsproject.employee.EmployeeService;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.sqs.enabled", havingValue = "true", matchIfMissing = true)
public class EmployeeMessageConsumer {

    private final EmployeeService employeeService;

    // Listen to the queue and process in batches for higher throughput
    @SqsListener(queueNames = "${app.sqs.employee-queue}", maxConcurrentMessages = "${app.sqs.max-concurrent-messages:10}")
    public void handleMessages(@Payload List<Employee> employees) {
        try {
            if (employees == null || employees.isEmpty()) {
                return;
            }
            employeeService.upsertEmployees(employees);
            log.info("Processed {} employee message(s)", employees.size());
        } catch (Exception ex) {
            log.error("Failed processing employee messages", ex);
            throw ex; // let the container handle retry / DLQ
        }
    }
}
