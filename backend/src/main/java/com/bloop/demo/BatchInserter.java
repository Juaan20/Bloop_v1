package com.bloop.demo;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Acumula filas por sentencia INSERT y las envia a PostgreSQL por lotes (batchUpdate), que es
 * mucho mas rapido que insertar una a una. Solo lo usa el generador de historico demo.
 *
 * Las sentencias se registran al crear el objeto en el orden de las claves foraneas
 * (padres antes que hijos) y flush() las envia en ese mismo orden.
 */
final class BatchInserter {

    private final JdbcTemplate jdbc;
    private final Map<String, List<Object[]>> pending = new LinkedHashMap<>();
    private long total;

    BatchInserter(JdbcTemplate jdbc, List<String> statementsInForeignKeyOrder) {
        this.jdbc = jdbc;
        statementsInForeignKeyOrder.forEach(sql -> pending.put(sql, new ArrayList<>()));
    }

    void add(String sql, Object... values) {
        List<Object[]> rows = pending.get(sql);
        if (rows == null) {
            throw new IllegalArgumentException("Sentencia no registrada: " + sql);
        }
        rows.add(values);
    }

    /** Envia todo lo acumulado respetando el orden de registro. */
    void flush() {
        for (Map.Entry<String, List<Object[]>> entry : pending.entrySet()) {
            List<Object[]> rows = entry.getValue();
            for (int from = 0; from < rows.size(); from += 1000) {
                jdbc.batchUpdate(entry.getKey(), rows.subList(from, Math.min(rows.size(), from + 1000)));
            }
            total += rows.size();
            rows.clear();
        }
    }

    long totalRows() {
        return total;
    }
}
