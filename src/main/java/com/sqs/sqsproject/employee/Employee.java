package com.sqs.sqsproject.employee;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "employees", indexes = {
        @Index(name = "idx_emp_email", columnList = "email", unique = true),
        @Index(name = "idx_emp_lastname", columnList = "lastName")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SequenceGenerator(name = "employee_seq", sequenceName = "employees_id_seq", allocationSize = 1)
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "employee_seq")
    private Long id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    private String department;

    private Instant hiredAt;
}
