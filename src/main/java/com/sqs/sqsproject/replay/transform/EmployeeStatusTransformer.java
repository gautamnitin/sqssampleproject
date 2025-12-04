package com.sqs.sqsproject.replay.transform;

import com.sqs.sqsproject.employee.Employee;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Sample transformer for Employee messages that adds a status prefix to the employee name.
 * This demonstrates how to implement a custom transformer for specific message types.
 */
@Component
@Slf4j
public class EmployeeStatusTransformer implements MessageTransformer<Employee> {

    /**
     * Transforms an Employee message by adding a "[REPLAYED]" prefix to the employee name.
     * This helps identify messages that have been replayed from a DLQ.
     *
     * @param employee the employee message to transform
     * @return the transformed employee message
     */
    @Override
    public Employee transform(Employee employee) {
        if (employee == null) {
            log.warn("Received null employee message, returning as-is");
            return null;
        }

        log.info("Transforming employee message: {}", employee.getId());
        
        // Create a new Employee with modified firstName to indicate it was replayed
        return Employee.builder()
                .id(employee.getId())
                .firstName(employee.getFirstName() != null ? "[REPLAYED] " + employee.getFirstName() : "[REPLAYED]")
                .lastName(employee.getLastName())
                .email(employee.getEmail())
                .department(employee.getDepartment())
                .hiredAt(employee.getHiredAt())
                .build();
    }
}