package com.se2026.ai03.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class CleanupWorker {
    private static final Logger LOG = LoggerFactory.getLogger(CleanupWorker.class);
    private static final String POLICY_VERSION = "privacy-2026.10";

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final boolean simulation;
    private final int maxAttempts;
    private final long retryBaseSeconds;

    public CleanupWorker(
            JdbcClient jdbc,
            ObjectMapper objectMapper,
            @Value("${app.cleanup.worker.enabled:false}") boolean enabled,
            @Value("${app.cleanup.worker.simulation:true}") boolean simulation,
            @Value("${app.cleanup.worker.max-attempts:5}") int maxAttempts,
            @Value("${app.cleanup.worker.retry-base-seconds:30}") long retryBaseSeconds) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.simulation = simulation;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryBaseSeconds = Math.max(1, retryBaseSeconds);
    }

    @Scheduled(fixedDelayString = "${app.cleanup.worker.poll-interval-ms:30000}")
    @Transactional
    public void scheduledRun() {
        if (enabled) {
            runOnce(10);
        }
    }

    @Transactional
    public int runOnce(int limit) {
        List<String> pending = jdbc.sql("""
                SELECT id FROM deletion_jobs
                WHERE status='PENDING' AND due_at <= :now
                ORDER BY due_at LIMIT :limit
                """)
                .param("now", OffsetDateTime.now(ZoneOffset.UTC))
                .param("limit", limit)
                .query(String.class)
                .list();

        int processed = 0;
        for (String jobId : pending) {
            int claimed = jdbc.sql("""
                    UPDATE deletion_jobs
                    SET status='RUNNING', attempts=attempts+1
                    WHERE id=:id AND status='PENDING'
                    """)
                    .param("id", jobId)
                    .update();
            if (claimed == 0) {
                continue;
            }
            try {
                if (!simulation) {
                    throw new IllegalStateException(
                            "Production cleanup adapters for object storage and Milvus are not configured");
                }
                complete(jobId);
                processed++;
            } catch (Exception exception) {
                fail(jobId, exception);
            }
        }
        return processed;
    }

    private void complete(String jobId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.sql("""
                UPDATE deletion_jobs
                SET status='COMPLETED', completed_at=:now, last_error=NULL
                WHERE id=:id
                """)
                .param("now", now)
                .param("id", jobId)
                .update();
        jdbc.sql("""
                UPDATE outbox_events
                SET status='PUBLISHED', attempts=attempts+1
                WHERE aggregate_id=(
                  SELECT consent_id FROM deletion_jobs WHERE id=:id
                ) AND event_type='CONSENT_REVOKED' AND status='PENDING'
                """)
                .param("id", jobId)
                .update();
        writeAudit("CLEANUP_COMPLETED", jobId, Map.of("simulation", true));
        LOG.info("Cleanup job {} completed in simulation mode", jobId);
    }

    private void fail(String jobId, Exception exception) {
        int attempts = jdbc.sql("SELECT attempts FROM deletion_jobs WHERE id=:id")
                .param("id", jobId)
                .query(Integer.class)
                .optional()
                .orElse(maxAttempts);
        boolean exhausted = attempts >= maxAttempts;
        OffsetDateTime nextDueAt = OffsetDateTime.now(ZoneOffset.UTC)
                .plusSeconds(retryBaseSeconds * (1L << Math.min(attempts, 8)));
        String error = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
        jdbc.sql("""
                UPDATE deletion_jobs
                SET status=:status, due_at=:dueAt, last_error=:error
                WHERE id=:id
                """)
                .param("status", exhausted ? "FAILED" : "PENDING")
                .param("dueAt", nextDueAt)
                .param("error", error)
                .param("id", jobId)
                .update();
        if (exhausted) {
            jdbc.sql("""
                    UPDATE outbox_events
                    SET status='FAILED', attempts=attempts+1
                    WHERE aggregate_id=(
                      SELECT consent_id FROM deletion_jobs WHERE id=:id
                    ) AND event_type='CONSENT_REVOKED' AND status='PENDING'
                    """)
                    .param("id", jobId)
                    .update();
        }
        writeAudit("CLEANUP_FAILED", jobId, Map.of(
                "error", error,
                "attempt", attempts,
                "retryScheduled", !exhausted));
        LOG.warn("Cleanup job {} failed on attempt {} (retry={})", jobId, attempts, !exhausted, exception);
    }

    private void writeAudit(String action, String resourceId, Map<String, Object> metadata) {
        try {
            jdbc.sql("""
                    INSERT INTO audit_events (
                      id, album_id, actor_user_id, action, resource_type, resource_id,
                      policy_version, metadata, occurred_at
                    )
                    SELECT
                      :id, c.album_id, NULL, :action, 'DELETION_JOB', :resourceId,
                      :policyVersion, :metadata, :now
                    FROM deletion_jobs d
                    JOIN consents c ON c.id = d.consent_id
                    WHERE d.id = :resourceId
                    """)
                    .param("id", "audit-" + UUID.randomUUID())
                    .param("action", action)
                    .param("resourceId", resourceId)
                    .param("policyVersion", POLICY_VERSION)
                    .param("metadata", objectMapper.writeValueAsString(metadata))
                    .param("now", OffsetDateTime.now(ZoneOffset.UTC))
                    .update();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize cleanup audit", exception);
        }
    }
}
