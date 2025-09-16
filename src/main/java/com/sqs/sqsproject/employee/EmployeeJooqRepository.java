package com.sqs.sqsproject.employee;

import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Query;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.stream.Collectors;

import static org.jooq.impl.DSL.*;

/**
 * Generic jOOQ upsert repository using JPA entity reflection.
 *
 * - Derives table name from @Table or class simple name.
 * - Determines ID/sequence from @Id/@GeneratedValue and @SequenceGenerator.
 * - Maps fields to snake_case column names (unless @Column(name=...) is present).
 * - Allows specifying conflict fields by entity field names.
 */
@Repository
@RequiredArgsConstructor
public class EmployeeJooqRepository {

    private final DSLContext dsl;

    // ---------------- Employee-specific convenience ----------------
    public int[] bulkUpsert(List<Employee> employees) {
        if (employees == null || employees.isEmpty()) return new int[0];
        // Conflict on unique email; update all but id and email
        List<String> updateFields = Arrays.asList("firstName", "lastName", "department", "hiredAt");
        return bulkUpsertGeneric(employees, Employee.class, new String[]{"email"}, updateFields, 1000);
    }

    // ---------------- Generic reflection-based upsert ----------------
    public <T> int[] bulkUpsertGeneric(List<T> items,
                                       Class<T> type,
                                       String[] conflictFieldNames,
                                       List<String> updateFieldNames,
                                       int batchSize) {
        if (items == null || items.isEmpty()) return new int[0];

        // Determine table and columns
        String tableName = resolveTableName(type);
        org.jooq.Table<?> table = table(tableName);

        EntityMeta meta = analyzeEntity(type);

        // Build list of insert columns and a function to get values per item
        List<String> allFieldNames = meta.orderedPersistentFieldNames;

        // Determine columns to include in INSERT
        List<String> insertFieldNames = new ArrayList<>();
        for (String f : allFieldNames) {
            if (meta.idFieldName != null && f.equals(meta.idFieldName) && meta.sequenceName == null) {
                // Skip ID if no sequence (assume DB default or identity)
                continue;
            }
            insertFieldNames.add(f);
        }

        // Map to jOOQ Fields
        List<Field<Object>> insertColumns = insertFieldNames.stream()
                .map(fn -> field(name(meta.columnName(fn))))
                .map(f -> (Field<Object>) f)
                .collect(Collectors.toList());

        // Resolve conflict columns
        List<Field<?>> conflictColumns = Arrays.stream(conflictFieldNames)
                .map(meta::columnName)
                .map(cn -> (Field<?>) field(name(cn)))
                .collect(Collectors.toList());

        // Determine update fields if not provided
        Set<String> conflictSet = new HashSet<>(Arrays.asList(conflictFieldNames));
        List<String> effectiveUpdateFields = (updateFieldNames != null && !updateFieldNames.isEmpty())
                ? updateFieldNames
                : allFieldNames.stream()
                    .filter(fn -> !fn.equals(meta.idFieldName) && !conflictSet.contains(fn))
                    .collect(Collectors.toList());

        // Construct queries per row
        List<Query> queries = new ArrayList<>(items.size());
        for (T item : items) {
            if (item == null) continue;

            List<org.jooq.QueryPart> values = new ArrayList<>();
            for (String f : insertFieldNames) {
                if (meta.idFieldName != null && f.equals(meta.idFieldName) && meta.sequenceName != null) {
                    values.add(field("nextval('" + meta.sequenceName + "')"));
                } else {
                    Object v = meta.read(item, f);
                    values.add(toJooqValue(v));
                }
            }

            org.jooq.QueryPart[] valuesArr = values.toArray(new org.jooq.QueryPart[0]);

            // Build INSERT ... ON CONFLICT ... DO UPDATE
            var step = dsl.insertInto(table)
                    .columns(insertColumns)
                    .values(valuesArr)
                    .onConflict(conflictColumns.toArray(new Field<?>[0]))
                    .doUpdate();

            // Prepare update map
            Map<Field<?>, Object> updateMap = new LinkedHashMap<>();
            for (String uf : effectiveUpdateFields) {
                Field<Object> col = (Field<Object>) field(name(meta.columnName(uf)));
                Object v = meta.read(item, uf);
                updateMap.put(col, (v instanceof Instant) ? Timestamp.from((Instant) v) : v);
            }
            step.set(updateMap);

            queries.add((Query) step);
        }

        if (queries.isEmpty()) return new int[0];
        return dsl.batch(queries).execute();
    }

    // ---------------- Helpers ----------------
    private static org.jooq.QueryPart toJooqValue(Object v) {
        if (v == null) return val((Object) null);
        if (v instanceof Instant inst) return val(Timestamp.from(inst));
        if (v instanceof Temporal) return val(v.toString()); // fallback textual
        return val(v);
    }

    private static String resolveTableName(Class<?> type) {
        jakarta.persistence.Table tableAnn = type.getAnnotation(jakarta.persistence.Table.class);
        if (tableAnn != null && tableAnn.name() != null && !tableAnn.name().isBlank()) {
            return tableAnn.name();
        }
        Entity entityAnn = type.getAnnotation(Entity.class);
        if (entityAnn != null && entityAnn.name() != null && !entityAnn.name().isBlank()) {
            return toSnakeCase(entityAnn.name());
        }
        return toSnakeCase(type.getSimpleName()) + "s"; // naive plural fallback
    }

    private static String toSnakeCase(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) sb.append('_');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static class EntityMeta {
        final Class<?> type;
        final Map<String, java.lang.reflect.Field> fieldsByName;
        final Map<String, String> columnByField;
        final List<String> orderedPersistentFieldNames;
        final String idFieldName;
        final String sequenceName; // may be null

        EntityMeta(Class<?> type,
                   Map<String, java.lang.reflect.Field> fieldsByName,
                   Map<String, String> columnByField,
                   List<String> orderedPersistentFieldNames,
                   String idFieldName,
                   String sequenceName) {
            this.type = type;
            this.fieldsByName = fieldsByName;
            this.columnByField = columnByField;
            this.orderedPersistentFieldNames = orderedPersistentFieldNames;
            this.idFieldName = idFieldName;
            this.sequenceName = sequenceName;
        }

        Object read(Object instance, String fieldName) {
            try {
                java.lang.reflect.Field f = fieldsByName.get(fieldName);
                f.setAccessible(true);
                return f.get(instance);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to read field '" + fieldName + "'", e);
            }
        }

        String columnName(String fieldName) {
            String cn = columnByField.get(fieldName);
            return (cn != null && !cn.isBlank()) ? cn : toSnakeCase(fieldName);
        }
    }

    private static <T> EntityMeta analyzeEntity(Class<T> type) {
        Map<String, java.lang.reflect.Field> fieldsByName = new LinkedHashMap<>();
        Map<String, String> columnByField = new HashMap<>();
        String idFieldName = null;
        String generatorName = null;

        for (java.lang.reflect.Field f : type.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            String fn = f.getName();
            fieldsByName.put(fn, f);

            Column col = f.getAnnotation(Column.class);
            if (col != null && col.name() != null && !col.name().isBlank()) {
                columnByField.put(fn, col.name());
            } else {
                columnByField.put(fn, toSnakeCase(fn));
            }

            if (f.getAnnotation(Id.class) != null) {
                idFieldName = fn;
                GeneratedValue gv = f.getAnnotation(GeneratedValue.class);
                if (gv != null && gv.generator() != null && !gv.generator().isBlank()) {
                    generatorName = gv.generator();
                }
            }
        }

        // Resolve sequence name if any
        String sequenceName = null;
        if (generatorName != null) {
            SequenceGenerator sg = findSequenceGenerator(type, generatorName);
            if (sg != null && sg.sequenceName() != null && !sg.sequenceName().isBlank()) {
                sequenceName = sg.sequenceName();
            }
        }

        List<String> orderedNames = new ArrayList<>(fieldsByName.keySet());
        return new EntityMeta(type, fieldsByName, columnByField, orderedNames, idFieldName, sequenceName);
    }

    private static SequenceGenerator findSequenceGenerator(Class<?> type, String generatorName) {
        SequenceGenerator onClass = type.getAnnotation(SequenceGenerator.class);
        if (onClass != null && generatorName.equals(onClass.name())) return onClass;
        // Look for any @SequenceGenerator annotations declared multiple times (repeatable not used here)
        return null;
    }
}
