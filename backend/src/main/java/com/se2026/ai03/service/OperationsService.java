package com.se2026.ai03.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import com.se2026.ai03.api.ApiException;
import com.se2026.ai03.auth.AuthUser;
import com.se2026.ai03.model.ModelClient;
import com.se2026.ai03.vector.VectorIndex;

@Service
public class OperationsService {
    private final JdbcClient jdbc;
    private final ModelClient modelClient;
    private final VectorIndex vectorIndex;

    public OperationsService(JdbcClient jdbc, ModelClient modelClient, VectorIndex vectorIndex) {
        this.jdbc = jdbc;
        this.modelClient = modelClient;
        this.vectorIndex = vectorIndex;
    }

    public Map<String, Object> dependencyHealth() {
        Map<String, Object> database = new LinkedHashMap<>();
        try {
            int value = jdbc.sql("SELECT 1").query(Integer.class).single();
            database.put("status", value == 1 ? "UP" : "DEGRADED");
            database.put("check", "SELECT 1");
        } catch (Exception exception) {
            database.put("status", "DOWN");
            database.put("error", exception.getClass().getSimpleName());
        }

        Map<?, ?> model = modelClient.health();
        boolean modelUp = "UP".equals(String.valueOf(model.get("status")));
        boolean databaseUp = "UP".equals(database.get("status"));
        return Map.of(
                "status", databaseUp && modelUp ? "UP" : "DEGRADED",
                "database", database,
                "modelService", model,
                "vectorProvider", vectorIndex.provider());
    }

    public CleanupSummary cleanupSummary(AuthUser user) {
        if (!user.isOwner() && !"ADMIN".equals(user.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "Cleanup summary is restricted");
        }
        return new CleanupSummary(
                statusCounts("deletion_jobs"),
                statusCounts("outbox_events"));
    }

    private Map<String, Integer> statusCounts(String table) {
        if (!List.of("deletion_jobs", "outbox_events").contains(table)) {
            throw new IllegalArgumentException("Unsupported table");
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        jdbc.sql("SELECT status, COUNT(*) AS total FROM " + table + " GROUP BY status ORDER BY status")
                .query((rs, rowNum) -> Map.entry(rs.getString("status"), rs.getInt("total")))
                .list()
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    public record CleanupSummary(
            Map<String, Integer> deletionJobs,
            Map<String, Integer> outboxEvents) {
    }
}
