package com.sqs.sqsproject.jdbc;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class JdbcBulkUpsertUtilTest {

    @Test
    void returnsEmptyArrayWhenItemsNullOrEmpty() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        int[] res1 = JdbcBulkUpsertUtil.bulkUpsert(
                jdbcTemplate,
                null,
                "t",
                List.of("id"),
                List.of("?"),
                List.of("id"),
                List.of(),
                (ps, it) -> {},
                100
        );
        assertNotNull(res1);
        assertEquals(0, res1.length);
        verifyNoInteractions(jdbcTemplate);

        int[] res2 = JdbcBulkUpsertUtil.bulkUpsert(
                jdbcTemplate,
                Collections.emptyList(),
                "t",
                List.of("id"),
                List.of("?"),
                List.of("id"),
                List.of(),
                (ps, it) -> {},
                100
        );
        assertNotNull(res2);
        assertEquals(0, res2.length);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void throwsWhenColumnsAndValuesSizeMismatch() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                JdbcBulkUpsertUtil.bulkUpsert(
                        jdbcTemplate,
                        List.of(1),
                        "t",
                        List.of("id", "a"),
                        List.of("?"),
                        List.of("id"),
                        List.of("a"),
                        (ps, it) -> {},
                        10
                )
        );
        assertTrue(ex.getMessage().toLowerCase().contains("same size"));
    }

    @Test
    void buildsSqlAndBindsParametersInOrder_singleBatch() throws SQLException {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<BatchPreparedStatementSetter> setterCaptor = ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);

        when(jdbcTemplate.batchUpdate(sqlCaptor.capture(), setterCaptor.capture()))
                .thenAnswer(invocation -> {
                    // Simulate driver calling setters
                    BatchPreparedStatementSetter setter = invocation.getArgument(1);
                    assertEquals(2, setter.getBatchSize());
                    PreparedStatement ps = mock(PreparedStatement.class);

                    // Call setValues for each index
                    setter.setValues(ps, 0);
                    setter.setValues(ps, 1);

                    // Verify binding order for both items
                    InOrder inOrder = Mockito.inOrder(ps);
                    inOrder.verify(ps).setString(1, "A1");
                    inOrder.verify(ps).setString(2, "B1");
                    inOrder.verify(ps).setString(1, "A2");
                    inOrder.verify(ps).setString(2, "B2");

                    return new int[]{1, 1};
                });

        List<String> columns = Arrays.asList("id", "a", "b");
        List<String> values = Arrays.asList("nextval('seq')", "?", "?");
        List<String> conflict = List.of("a");
        List<String> updates = List.of("b");

        class Row { final String a; final String b; Row(String a, String b){this.a=a;this.b=b;} }
        List<Row> items = Arrays.asList(new Row("A1", "B1"), new Row("A2", "B2"));

        int[] res = JdbcBulkUpsertUtil.bulkUpsert(
                jdbcTemplate,
                items,
                "t",
                columns,
                values,
                conflict,
                updates,
                (ps, r) -> {
                    ps.setString(1, r.a);
                    ps.setString(2, r.b);
                },
                100
        );

        assertArrayEquals(new int[]{1,1}, res);

        String expectedSql = "INSERT INTO t (id, a, b) " +
                "VALUES (nextval('seq'), ?, ?) " +
                "ON CONFLICT (a) DO UPDATE SET b = EXCLUDED.b";
        assertEquals(expectedSql, sqlCaptor.getValue());
        verify(jdbcTemplate, times(1)).batchUpdate(any(String.class), any(BatchPreparedStatementSetter.class));
    }

    @Test
    void processesInBatchesAndReturnsLastBatchCounts() throws SQLException {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<BatchPreparedStatementSetter> setterCaptor = ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);

        // First call (2 items)
        when(jdbcTemplate.batchUpdate(sqlCaptor.capture(), setterCaptor.capture()))
                .thenAnswer(invocation -> {
                    BatchPreparedStatementSetter setter = invocation.getArgument(1);
                    assertEquals(2, setter.getBatchSize());
                    PreparedStatement ps = mock(PreparedStatement.class);
                    setter.setValues(ps, 0);
                    setter.setValues(ps, 1);
                    return new int[]{1, 1};
                })
                // Second call (1 item)
                .thenAnswer(invocation -> {
                    BatchPreparedStatementSetter setter = invocation.getArgument(1);
                    assertEquals(1, setter.getBatchSize());
                    PreparedStatement ps = mock(PreparedStatement.class);
                    setter.setValues(ps, 0);
                    return new int[]{1};
                });

        List<String> columns = Arrays.asList("id", "a");
        List<String> values = Arrays.asList("nextval('seq')", "?");
        List<String> conflict = List.of("a");
        List<String> updates = List.of("a"); // trivial update

        List<String> items = Arrays.asList("X1", "X2", "X3");

        int[] res = JdbcBulkUpsertUtil.bulkUpsert(
                jdbcTemplate,
                items,
                "t",
                columns,
                values,
                conflict,
                updates,
                (ps, a) -> ps.setString(1, a),
                2 // batchSize
        );

        // Should return the last batch update counts
        assertArrayEquals(new int[]{1}, res);

        String expectedSql = "INSERT INTO t (id, a) " +
                "VALUES (nextval('seq'), ?) " +
                "ON CONFLICT (a) DO UPDATE SET a = EXCLUDED.a";
        // SQL captured twice; verify both identical
        List<String> capturedSql = sqlCaptor.getAllValues();
        assertEquals(2, capturedSql.size());
        assertEquals(expectedSql, capturedSql.get(0));
        assertEquals(expectedSql, capturedSql.get(1));

        verify(jdbcTemplate, times(2)).batchUpdate(any(String.class), any(BatchPreparedStatementSetter.class));
    }
}
