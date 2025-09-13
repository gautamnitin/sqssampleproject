package com.sqs.sqsproject.employee;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class EmployeeJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Performs bulk upsert (insert/update) by unique email using JDBC batchUpdate for efficiency.
     * Uses Postgres ON CONFLICT for idempotency. Returns per-row update counts from the last batch.
     */
    public int[] bulkUpsert(List<Employee> employees) {
        if (employees == null || employees.isEmpty()) return new int[0];

        // Filter invalid rows according to NOT NULL constraints in schema
        List<Employee> valid = new ArrayList<>();
        for (Employee e : employees) {
            if (e == null) continue;
            if (e.getEmail() == null || e.getFirstName() == null || e.getLastName() == null) continue;
            valid.add(e);
        }
        if (valid.isEmpty()) return new int[0];

        final String sql = "INSERT INTO employees (id, first_name, last_name, email, department, hired_at) " +
                "VALUES (nextval('employees_id_seq'), ?, ?, ?, ?, ?) " +
                "ON CONFLICT (email) DO UPDATE SET " +
                " first_name = EXCLUDED.first_name, " +
                " last_name = EXCLUDED.last_name, " +
                " department = EXCLUDED.department, " +
                " hired_at = EXCLUDED.hired_at";

        final int batchSize = 1000;
        int[] lastBatchCounts = new int[0];
        for (int start = 0; start < valid.size(); start += batchSize) {
            int end = Math.min(start + batchSize, valid.size());
            List<Employee> batch = valid.subList(start, end);

            lastBatchCounts = jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    Employee e = batch.get(i);
                    ps.setString(1, e.getFirstName());
                    ps.setString(2, e.getLastName());
                    ps.setString(3, e.getEmail());
                    // department may be null
                    if (e.getDepartment() != null) {
                        ps.setString(4, e.getDepartment());
                    } else {
                        ps.setNull(4, java.sql.Types.VARCHAR);
                    }
                    // hiredAt may be null
                    Instant hiredAt = e.getHiredAt();
                    if (hiredAt != null) {
                        ps.setTimestamp(5, Timestamp.from(hiredAt));
                    } else {
                        ps.setNull(5, java.sql.Types.TIMESTAMP);
                    }
                }

                @Override
                public int getBatchSize() {
                    return batch.size();
                }
            });
        }
        return lastBatchCounts;
    }
}
