package com.sqs.sqsproject.jdbc;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Generic JDBC bulk upsert utility for PostgreSQL using ON CONFLICT.
 *
 * It allows mixing SQL expressions (e.g., nextval('seq')) and JDBC placeholders ("?") in the VALUES list.
 * The provided ParamBinder will be invoked once per row and should bind parameters in the exact order of
 * appearance of "?" entries in the valueExpressions list.
 */
public final class JdbcBulkUpsertUtil {

    private JdbcBulkUpsertUtil() {}

    @FunctionalInterface
    public interface ParamBinder<T> {
        void bind(PreparedStatement ps, T item) throws SQLException;
    }

    /**
     * Executes a batch upsert.
     * @param jdbcTemplate JdbcTemplate to use
     * @param items items to upsert (null or empty -> returns empty array)
     * @param table table name
     * @param columns full list of target columns for INSERT (size must equal valueExpressions)
     * @param valueExpressions list parallel to columns: each element either "?" for JDBC parameter or a raw SQL expression (e.g., nextval('seq'))
     * @param conflictColumns list of columns that form the conflict target (unique key)
     * @param updateColumns list of columns to update on conflict (usually excludes id)
     * @param binder binds the values for placeholders in the order of appearance in valueExpressions
     * @param batchSize number of rows per batch
     * @return update counts from the last batch executed
     */
    public static <T> int[] bulkUpsert(
            JdbcTemplate jdbcTemplate,
            List<T> items,
            String table,
            List<String> columns,
            List<String> valueExpressions,
            List<String> conflictColumns,
            List<String> updateColumns,
            ParamBinder<T> binder,
            int batchSize
    ) {
        if (items == null || items.isEmpty()) return new int[0];
        Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(columns, "columns");
        Objects.requireNonNull(valueExpressions, "valueExpressions");
        Objects.requireNonNull(conflictColumns, "conflictColumns");
        Objects.requireNonNull(updateColumns, "updateColumns");
        Objects.requireNonNull(binder, "binder");

        if (columns.size() != valueExpressions.size()) {
            throw new IllegalArgumentException("columns and valueExpressions must have the same size");
        }
        if (batchSize <= 0) batchSize = 1000;

        String sql = buildSql(table, columns, valueExpressions, conflictColumns, updateColumns);

        int[] last = new int[0];
        for (int start = 0; start < items.size(); start += batchSize) {
            int end = Math.min(start + batchSize, items.size());
            List<T> batch = items.subList(start, end);
            last = jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    binder.bind(ps, batch.get(i));
                }
                @Override
                public int getBatchSize() { return batch.size(); }
            });
        }
        return last;
    }

    private static String buildSql(String table,
                                   List<String> columns,
                                   List<String> valueExpressions,
                                   List<String> conflictColumns,
                                   List<String> updateColumns) {
        String colList = String.join(", ", columns);

        // Build VALUES expression by joining provided expressions as-is
        List<String> vals = new ArrayList<>(valueExpressions);
        String valuesSql = String.join(", ", vals);

        String conflict = String.join(", ", conflictColumns);

        List<String> updates = new ArrayList<>(updateColumns.size());
        for (String col : updateColumns) {
            updates.add(" " + col + " = EXCLUDED." + col);
        }
        String updateSql = String.join(", ", updates);

        return "INSERT INTO " + table + " (" + colList + ") " +
               "VALUES (" + valuesSql + ") " +
               "ON CONFLICT (" + conflict + ") DO UPDATE SET" +
               updateSql;
    }
}
