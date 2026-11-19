package com.se2026.ai03.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.se2026.ai03.auth.AuthUser;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WorkflowDatabaseTest {
    private static final AuthUser PARTICIPANT = new AuthUser("u-participant", "林岚", "PARTICIPANT");
    private static final AuthUser OWNER = new AuthUser("u-owner", "林澜", "OWNER");

    @Autowired
    private WorkflowService workflow;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private CleanupWorker cleanupWorker;

    @Test
    void approvalCreatesConsentAndAuditRecord() {
        WorkflowService.SearchRequestDto request =
                workflow.createRequest(PARTICIPANT, "album-cabin", 7);
        WorkflowService.SearchRequestDto approved =
                workflow.decide(OWNER, request.id(), "APPROVE", 7);

        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(approved.consentId()).isNotBlank();
        assertThat(count("SELECT COUNT(*) FROM consents WHERE id=:id", approved.consentId()))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM audit_events WHERE action='REQUEST_APPROVED'"))
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void revokePersistsCleanupJobAndOutboxEvent() {
        WorkflowService.SearchRequestDto request =
                workflow.createRequest(PARTICIPANT, "album-cabin", 7);
        WorkflowService.SearchRequestDto approved =
                workflow.decide(OWNER, request.id(), "APPROVE", 7);

        var revoked = workflow.revokeConsent(PARTICIPANT, approved.consentId());
        String jobId = String.valueOf(revoked.get("cleanupJobId"));
        WorkflowService.DeletionJobDto job = workflow.getDeletionJob(PARTICIPANT, jobId);

        assertThat(job.status()).isEqualTo("PENDING");
        assertThat(job.consentId()).isEqualTo(approved.consentId());
        assertThat(count("SELECT COUNT(*) FROM outbox_events WHERE event_type='CONSENT_REVOKED'"))
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void repeatedRevokeReusesExistingCleanupJob() {
        WorkflowService.SearchRequestDto request =
                workflow.createRequest(PARTICIPANT, "album-cabin", 7);
        WorkflowService.SearchRequestDto approved =
                workflow.decide(OWNER, request.id(), "APPROVE", 7);

        var first = workflow.revokeConsent(PARTICIPANT, approved.consentId());
        var second = workflow.revokeConsent(PARTICIPANT, approved.consentId());

        assertThat(second.get("cleanupJobId")).isEqualTo(first.get("cleanupJobId"));
        assertThat(count("SELECT COUNT(*) FROM deletion_jobs WHERE consent_id=:id", approved.consentId()))
                .isEqualTo(1);
    }

    @Test
    void cleanupWorkerCompletesPendingJobAndPublishesOutbox() {
        WorkflowService.SearchRequestDto request =
                workflow.createRequest(PARTICIPANT, "album-cabin", 7);
        WorkflowService.SearchRequestDto approved =
                workflow.decide(OWNER, request.id(), "APPROVE", 7);
        var revoked = workflow.revokeConsent(PARTICIPANT, approved.consentId());
        String jobId = String.valueOf(revoked.get("cleanupJobId"));

        assertThat(cleanupWorker.runOnce(10)).isGreaterThanOrEqualTo(1);
        WorkflowService.DeletionJobDto job = workflow.getDeletionJob(PARTICIPANT, jobId);

        assertThat(job.status()).isEqualTo("COMPLETED");
        assertThat(count("SELECT COUNT(*) FROM outbox_events WHERE status='PUBLISHED'"))
                .isGreaterThanOrEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM audit_events WHERE action='CLEANUP_COMPLETED'"))
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void cleanupWorkerReclaimsStaleRunningJob() {
        WorkflowService.SearchRequestDto request =
                workflow.createRequest(PARTICIPANT, "album-cabin", 7);
        WorkflowService.SearchRequestDto approved =
                workflow.decide(OWNER, request.id(), "APPROVE", 7);
        var revoked = workflow.revokeConsent(PARTICIPANT, approved.consentId());
        String jobId = String.valueOf(revoked.get("cleanupJobId"));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        jdbc.sql("""
                UPDATE deletion_jobs
                SET status='RUNNING', attempts=1, claimed_at=:stale, due_at=:now
                WHERE id=:id
                """)
                .param("stale", now.minusDays(1))
                .param("now", now)
                .param("id", jobId)
                .update();

        assertThat(cleanupWorker.runOnce(10)).isGreaterThanOrEqualTo(1);
        assertThat(workflow.getDeletionJob(PARTICIPANT, jobId).status()).isEqualTo("COMPLETED");
    }

    private int count(String sql, Object... ignored) {
        String normalized = sql;
        if (sql.contains(":id")) {
            return jdbc.sql(sql).param("id", ignored[0]).query(Integer.class).single();
        }
        return jdbc.sql(normalized).query(Integer.class).single();
    }
}
