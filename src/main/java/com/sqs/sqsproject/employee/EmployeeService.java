package com.sqs.sqsproject.employee;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository repository;

    // Batch upsert by email for idempotency
    @Transactional
    public void upsertEmployees(List<Employee> incoming) {
        if (incoming == null || incoming.isEmpty()) return;

        // Map incoming by email
        Map<String, Employee> byEmail = incoming.stream()
                .filter(e -> e.getEmail() != null)
                .collect(Collectors.toMap(Employee::getEmail, Function.identity(), (a, b) -> b));

        // Load existing by emails
        List<String> emails = new ArrayList<>(byEmail.keySet());
        if (emails.isEmpty()) return;

        List<Employee> toSave = new ArrayList<>();

        // Find existing and update fields using single IN query
        repository.findAllByEmailIn(emails).forEach(existing -> {
            Employee incomingEmp = byEmail.remove(existing.getEmail());
            if (incomingEmp != null) {
                existing.setFirstName(incomingEmp.getFirstName());
                existing.setLastName(incomingEmp.getLastName());
                existing.setDepartment(incomingEmp.getDepartment());
                existing.setHiredAt(incomingEmp.getHiredAt());
                toSave.add(existing);
            }
        });

        // New ones
        toSave.addAll(byEmail.values());

        if (!toSave.isEmpty()) {
            repository.saveAll(toSave);
        }
    }
}
