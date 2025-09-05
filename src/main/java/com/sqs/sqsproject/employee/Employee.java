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
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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
