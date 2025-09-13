package com.sqs.sqsproject.employee;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByEmail(String email);
    List<Employee> findAllByEmailIn(Iterable<String> emails);

    // Native PostgreSQL UPSERT by unique email (single row)
    @Modifying
    @Query(value = "INSERT INTO employees (id, first_name, last_name, email, department, hired_at)\n" +
            "VALUES (nextval('employees_id_seq'), :firstName, :lastName, :email, :department, :hiredAt)\n" +
            "ON CONFLICT (email) DO UPDATE SET\n" +
            " first_name = EXCLUDED.first_name,\n" +
            " last_name = EXCLUDED.last_name,\n" +
            " department = EXCLUDED.department,\n" +
            " hired_at = EXCLUDED.hired_at",
            nativeQuery = true)
    void upsertByEmail(
            @Param("firstName") String firstName,
            @Param("lastName") String lastName,
            @Param("email") String email,
            @Param("department") String department,
            @Param("hiredAt") Instant hiredAt
    );

    // Bulk UPSERT using one DB call; arrays must have same length.
    @Modifying
    @Query(value = "INSERT INTO employees (id, first_name, last_name, email, department, hired_at) \n" +
            "SELECT nextval('employees_id_seq'), t.first_name, t.last_name, t.email, t.department, t.hired_at \n" +
            "FROM unnest( \n" +
            "  cast(:firstNames as text[]), \n" +
            "  cast(:lastNames as text[]), \n" +
            "  cast(:emails as text[]), \n" +
            "  cast(:departments as text[]), \n" +
            "  cast(:hiredAts as timestamp[])\n" +
            ") AS t(first_name, last_name, email, department, hired_at) \n" +
            "ON CONFLICT (email) DO UPDATE SET \n" +
            " first_name = EXCLUDED.first_name, \n" +
            " last_name = EXCLUDED.last_name, \n" +
            " department = EXCLUDED.department, \n" +
            " hired_at = EXCLUDED.hired_at",
            nativeQuery = true)
    int bulkUpsertByEmail(
            @Param("firstNames") List<String> firstNames,
            @Param("lastNames") List<String> lastNames,
            @Param("emails") List<String> emails,
            @Param("departments") List<String> departments,
            @Param("hiredAts") List<Instant> hiredAts
    );
}
