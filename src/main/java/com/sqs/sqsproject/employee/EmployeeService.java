package com.sqs.sqsproject.employee;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeJdbcRepository jdbcRepository;

    // Bulk upsert by email for idempotency using one DB connection via JdbcTemplate.batchUpdate
    @Transactional
    public void bulkUpsert(List<Employee> employees) {
        if (employees == null || employees.isEmpty()) return;

        // Filter invalid entries (require email, firstName, lastName per schema constraints)
        List<Employee> valid = new ArrayList<>();
        for (Employee e : employees) {
            if (e == null) continue;
            if (e.getEmail() == null || e.getFirstName() == null || e.getLastName() == null) continue;
            valid.add(e);
        }
        if (valid.isEmpty()) return;

        jdbcRepository.bulkUpsert(valid);
    }

    // Backward compatibility: delegate to the new method
    @Transactional
    public void upsertEmployees(List<Employee> incoming) {
        bulkUpsert(incoming);
    }
}
