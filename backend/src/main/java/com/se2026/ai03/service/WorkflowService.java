package com.se2026.ai03.service;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.se2026.ai03.api.ApiException;
import com.se2026.ai03.auth.AuthUser;
import com.se2026.ai03.model.ModelClient;
import com.se2026.ai03.model.ModelClient.ModelFeatures;
import com.se2026.ai03.vector.VectorIndex;
import com.se2026.ai03.vector.VectorIndex.ScoredPhoto;

@Service
public class WorkflowService {
    private static final String POLICY_VERSION = "privacy-2026.10";

    private final JdbcClient jdbc;
    private final ModelClient modelClient;
    private final VectorIndex vectorIndex;
    private final ObjectMapper objectMapper;

    public WorkflowService(JdbcClient jdbc, ModelClient modelClient, VectorIndex vectorIndex, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.modelClient = modelClient;
        this.vectorIndex = vectorIndex;
        this.objectMapper = objectMapper;
    }

    public List<AlbumDto> listAlbums(AuthUser user) {
        return jdbc.sql("""
                SELECT a.id, a.name, a.photo_count, a.index_status, m.role
                FROM albums a
                JOIN album_members m ON m.album_id = a.id
                WHERE m.user_id = :userId AND m.status = 'ACTIVE'
                ORDER BY a.name
                """)
                .param("userId", user.id())
                .query((rs, rowNum) -> new AlbumDto(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getInt("photo_count"),
                        rs.getString("index_status"),
                        rs.getString("role")))
                .list();
    }

    @Transactional
    public SearchRequestDto createRequest(AuthUser user, String albumId, int requestedDays) {
        requireMember(user, albumId);
        if (requestedDays < 1 || requestedDays > 14) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_DURATION", "Requested days must be 1..14");
        }
        String id = "request-" + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.sql("""
                INSERT INTO search_requests (
                  id, album_id, requester_user_id, purpose_code, status, requested_days, created_at
                ) VALUES (:id, :albumId, :userId, 'FIND_MY_PHOTOS', 'REQUESTED', :days, :now)
                """)
                .param("id", id)
                .param("albumId", albumId)
                .param("userId", user.id())
                .param("days", requestedDays)
                .param("now", now)
                .update();
        audit(user.id(), "REQUEST_SUBMITTED", "SEARCH_REQUEST", id, Map.of("albumId", albumId));
        return getRequest(id);
    }

    public List<SearchRequestDto> listRequests(AuthUser user, String albumId) {
        if (user.isOwner() || "ADMIN".equals(user.role())) {
            requireAlbumOwner(user, albumId);
            return jdbc.sql("""
                    SELECT id FROM search_requests WHERE album_id = :albumId ORDER BY created_at DESC
                    """)
                    .param("albumId", albumId)
                    .query((rs, rowNum) -> getRequest(rs.getString("id")))
                    .list();
        }
        requireMember(user, albumId);
        return jdbc.sql("""
                SELECT id FROM search_requests
                WHERE album_id = :albumId AND requester_user_id = :userId
                ORDER BY created_at DESC
                """)
                .param("albumId", albumId)
                .param("userId", user.id())
                .query((rs, rowNum) -> getRequest(rs.getString("id")))
                .list();
    }

    @Transactional
    public SearchRequestDto decide(
            AuthUser user, String requestId, String decision, Integer approvedDays) {
        SearchRequestDto request = getRequest(requestId);
        requireAlbumOwner(user, request.albumId());
        if (!"REQUESTED".equals(request.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "REQUEST_ALREADY_DECIDED", "Request is no longer pending");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if ("REJECT".equalsIgnoreCase(decision)) {
            jdbc.sql("""
                    UPDATE search_requests
                    SET status='REJECTED', decided_by_user_id=:ownerId, decided_at=:now
                    WHERE id=:id
                    """)
                    .param("ownerId", user.id())
                    .param("now", now)
                    .param("id", requestId)
                    .update();
            audit(user.id(), "REQUEST_REJECTED", "SEARCH_REQUEST", requestId, Map.of());
            return getRequest(requestId);
        }
        if (!"APPROVE".equalsIgnoreCase(decision)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_DECISION", "Decision must be APPROVE or REJECT");
        }
        int days = approvedDays == null ? 7 : approvedDays;
        if (days < 1 || days > 7) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_DURATION", "Approved days must be 1..7");
        }
        OffsetDateTime expiresAt = now.plusDays(days);
        String consentId = "consent-" + UUID.randomUUID();
        jdbc.sql("""
                UPDATE search_requests
                SET status='APPROVED', approved_days=:days, decided_by_user_id=:ownerId,
                    decided_at=:now, expires_at=:expiresAt
                WHERE id=:id
                """)
                .param("days", days)
                .param("ownerId", user.id())
                .param("now", now)
                .param("expiresAt", expiresAt)
                .param("id", requestId)
                .update();
        jdbc.sql("""
                INSERT INTO consents (
                  id, request_id, album_id, subject_user_id, status,
                  policy_version, effective_at, expires_at
                ) VALUES (
                  :id, :requestId, :albumId, :subjectId, 'ACTIVE',
                  :policyVersion, :now, :expiresAt
                )
                """)
                .param("id", consentId)
                .param("requestId", requestId)
                .param("albumId", request.albumId())
                .param("subjectId", request.requesterId())
                .param("policyVersion", POLICY_VERSION)
                .param("now", now)
                .param("expiresAt", expiresAt)
                .update();
        audit(user.id(), "REQUEST_APPROVED", "SEARCH_REQUEST", requestId,
                Map.of("consentId", consentId, "days", days));
        return getRequest(requestId);
    }

    @Transactional
    public SearchSessionDto startSearch(
            AuthUser user,
            String requestId,
            String queryText,
            String sortMode,
            boolean diversity,
            byte[] sample,
            String contentType) {
        SearchRequestDto request = getRequest(requestId);
        if (!request.requesterId().equals(user.id())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "REQUEST_OWNER_MISMATCH", "This request belongs to another user");
        }
        ConsentRow consent = activeConsent(requestId);
        if (sample == null || sample.length == 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SAMPLE_REQUIRED", "A sample image is required");
        }
        if (sample.length > 10 * 1024 * 1024) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "SAMPLE_TOO_LARGE", "Sample exceeds 10 MB");
        }
        ModelFeatures features = modelClient.extract(sample, contentType, queryText);
        List<ScoredPhoto> photos = vectorIndex.search(
                features, queryText == null ? "" : queryText, normalizeSort(sortMode), diversity);
        List<SearchItemDto> items = photos.stream()
                .map(photo -> new SearchItemDto(
                        photo.photoId(),
                        photo.title(),
                        photo.albumId(),
                        photo.albumName(),
                        photo.scene(),
                        photo.outfit(),
                        photo.date(),
                        photo.overall(),
                        new FactorDto(photo.face(), photo.outfitScore(), photo.sceneScore()),
                        photo.reason(),
                        "视觉相似建议，不是身份认定"))
                .toList();
        String sessionId = "session-" + UUID.randomUUID();
        try {
            String json = objectMapper.writeValueAsString(items);
            jdbc.sql("""
                    INSERT INTO search_sessions (
                      id, request_id, actor_user_id, sort_mode, diversity_enabled,
                      query_text, status, model_version, consent_version, results_json, created_at
                    ) VALUES (
                      :id, :requestId, :userId, :sortMode, :diversity,
                      :queryText, 'COMPLETED', :modelVersion, :consentVersion, :resultsJson, :now
                    )
                    """)
                    .param("id", sessionId)
                    .param("requestId", requestId)
                    .param("userId", user.id())
                    .param("sortMode", normalizeSort(sortMode))
                    .param("diversity", diversity)
                    .param("queryText", queryText == null ? "" : queryText)
                    .param("modelVersion", features.modelVersion())
                    .param("consentVersion", consent.id())
                    .param("resultsJson", json)
                    .param("now", OffsetDateTime.now(ZoneOffset.UTC))
                    .update();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize search results", exception);
        }
        audit(user.id(), "SEARCH_COMPLETED", "SEARCH_SESSION", sessionId,
                Map.of("requestId", requestId, "modelVersion", features.modelVersion()));
        return new SearchSessionDto(
                sessionId,
                requestId,
                "COMPLETED",
                0,
                features.modelVersion(),
                consent.id(),
                items);
    }

    public SearchSessionDto getResults(AuthUser user, String sessionId) {
        SessionRow row = jdbc.sql("SELECT * FROM search_sessions WHERE id=:id")
                .param("id", sessionId)
                .query((rs, rowNum) -> new SessionRow(
                        rs.getString("id"),
                        rs.getString("request_id"),
                        rs.getString("actor_user_id"),
                        rs.getString("status"),
                        rs.getString("model_version"),
                        rs.getString("consent_version"),
                        rs.getString("results_json")))
                .optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Search session not found"));
        if (!row.actorUserId().equals(user.id())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SESSION_FORBIDDEN", "Search session belongs to another user");
        }
        activeConsent(row.requestId());
        try {
            List<SearchItemDto> items = objectMapper.readValue(
                    row.resultsJson(), new TypeReference<List<SearchItemDto>>() {
                    });
            return new SearchSessionDto(
                    row.id(), row.requestId(), row.status(), 0,
                    row.modelVersion(), row.consentVersion(), items);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored search results are invalid", exception);
        }
    }

    @Transactional
    public Map<String, Object> feedback(AuthUser user, String sessionId, String resultId, String relevance) {
        if (!List.of("RELEVANT", "IRRELEVANT", "UNDO").contains(relevance)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_FEEDBACK", "Unsupported feedback value");
        }
        SessionActor session = jdbc.sql("SELECT actor_user_id FROM search_sessions WHERE id=:id")
                .param("id", sessionId)
                .query((rs, rowNum) -> new SessionActor(rs.getString("actor_user_id")))
                .optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Search session not found"));
        if (!session.userId().equals(user.id())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SESSION_FORBIDDEN", "Search session belongs to another user");
        }
        String eventId = "feedback-" + UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO feedback_events (id, session_id, result_id, actor_user_id, relevance, created_at)
                VALUES (:id, :sessionId, :resultId, :userId, :relevance, :now)
                """)
                .param("id", eventId)
                .param("sessionId", sessionId)
                .param("resultId", resultId)
                .param("userId", user.id())
                .param("relevance", relevance)
                .param("now", OffsetDateTime.now(ZoneOffset.UTC))
                .update();
        audit(user.id(), "FEEDBACK_RECORDED", "SEARCH_RESULT", resultId,
                Map.of("sessionId", sessionId, "relevance", relevance));
        return Map.of("feedbackId", eventId, "status", "RECORDED");
    }

    @Transactional
    public Map<String, Object> revokeConsent(AuthUser user, String consentId) {
        ConsentSummary consent = jdbc.sql("""
                SELECT id, request_id, subject_user_id, status FROM consents WHERE id=:id
                """)
                .param("id", consentId)
                .query((rs, rowNum) -> new ConsentSummary(
                        rs.getString("id"),
                        rs.getString("request_id"),
                        rs.getString("subject_user_id"),
                        rs.getString("status")))
                .optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONSENT_NOT_FOUND", "Consent not found"));
        SearchRequestDto request = getRequest(consent.requestId());
        boolean allowed = consent.subjectUserId().equals(user.id()) || user.isOwner() || "ADMIN".equals(user.role());
        if (!allowed) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CONSENT_FORBIDDEN", "Cannot revoke another user's consent");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.sql("UPDATE consents SET status='REVOKED', revoked_at=:now WHERE id=:id")
                .param("now", now)
                .param("id", consentId)
                .update();
        jdbc.sql("UPDATE search_requests SET status='REVOKED' WHERE id=:id")
                .param("id", request.id())
                .update();
        jdbc.sql("DELETE FROM search_sessions WHERE request_id=:id")
                .param("id", request.id())
                .update();
        String cleanupJobId = "cleanup-" + UUID.randomUUID();
        audit(user.id(), "CONSENT_REVOKED", "CONSENT", consentId,
                Map.of("cleanupJobId", cleanupJobId, "requestId", request.id()));
        return Map.of(
                "consentId", consentId,
                "status", "CLEANUP_PENDING",
                "cleanupJobId", cleanupJobId);
    }

    public List<AuditEventDto> auditEvents(AuthUser user, String albumId) {
        if (!user.isOwner() && !"ADMIN".equals(user.role())) {
            requireMember(user, albumId);
            return jdbc.sql("""
                    SELECT * FROM audit_events
                    WHERE actor_user_id=:userId ORDER BY occurred_at DESC LIMIT 30
                    """)
                    .param("userId", user.id())
                    .query((rs, rowNum) -> new AuditEventDto(
                            rs.getString("id"),
                            rs.getString("action"),
                            rs.getString("resource_type"),
                            rs.getString("resource_id"),
                            rs.getObject("occurred_at", OffsetDateTime.class).toString()))
                    .list();
        }
        requireAlbumOwner(user, albumId);
        return jdbc.sql("""
                SELECT * FROM audit_events ORDER BY occurred_at DESC LIMIT 100
                """)
                .query((rs, rowNum) -> new AuditEventDto(
                        rs.getString("id"),
                        rs.getString("action"),
                        rs.getString("resource_type"),
                        rs.getString("resource_id"),
                        rs.getObject("occurred_at", OffsetDateTime.class).toString()))
                .list();
    }

    public List<ExperimentDto> experiments(AuthUser user) {
        if (!user.isOwner() && !"ADMIN".equals(user.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "Experiment summary is restricted");
        }
        return List.of(
                new ExperimentDto("balanced-multimodal", "stub-multimodal-v1", 15, .864, .821, 428),
                new ExperimentDto("face-first", "stub-multimodal-v1", 85, .889, .803, 411));
    }

    private void requireMember(AuthUser user, String albumId) {
        int count = jdbc.sql("""
                SELECT COUNT(*) FROM album_members
                WHERE album_id=:albumId AND user_id=:userId AND status='ACTIVE'
                """)
                .param("albumId", albumId)
                .param("userId", user.id())
                .query(Integer.class)
                .single();
        if (count == 0) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ALBUM_FORBIDDEN", "Album access is not authorized");
        }
    }

    private void requireAlbumOwner(AuthUser user, String albumId) {
        int count = jdbc.sql("""
                SELECT COUNT(*) FROM albums WHERE id=:albumId AND owner_user_id=:userId
                """)
                .param("albumId", albumId)
                .param("userId", user.id())
                .query(Integer.class)
                .single();
        if (count == 0 && !"ADMIN".equals(user.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "OWNER_REQUIRED", "Album owner permission is required");
        }
    }

    private SearchRequestDto getRequest(String id) {
        return jdbc.sql("SELECT * FROM search_requests WHERE id=:id")
                .param("id", id)
                .query((rs, rowNum) -> new SearchRequestDto(
                        rs.getString("id"),
                        rs.getString("album_id"),
                        rs.getString("requester_user_id"),
                        rs.getString("purpose_code"),
                        rs.getString("status"),
                        rs.getInt("requested_days"),
                        rs.getObject("approved_days", Integer.class),
                        rs.getString("decided_by_user_id"),
                        rs.getObject("created_at", OffsetDateTime.class).toString(),
                        rs.getObject("expires_at", OffsetDateTime.class) == null
                                ? null
                                : rs.getObject("expires_at", OffsetDateTime.class).toString(),
                        jdbc.sql("SELECT id FROM consents WHERE request_id=:id")
                                .param("id", id)
                                .query(String.class)
                                .optional()
                                .orElse(null)))
                .optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND", "Search request not found"));
    }

    private ConsentRow activeConsent(String requestId) {
        ConsentRow consent = jdbc.sql("""
                SELECT id, request_id, subject_user_id, status, expires_at
                FROM consents WHERE request_id=:requestId
                """)
                .param("requestId", requestId)
                .query((rs, rowNum) -> new ConsentRow(
                        rs.getString("id"),
                        rs.getString("request_id"),
                        rs.getString("subject_user_id"),
                        rs.getString("status"),
                        rs.getObject("expires_at", OffsetDateTime.class)))
                .optional()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.FORBIDDEN, "CONSENT_REQUIRED", "Owner approval is required"));
        if (!"ACTIVE".equals(consent.status()) || consent.expiresAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new ApiException(HttpStatus.CONFLICT, "CONSENT_CHANGED", "Consent is expired or revoked");
        }
        return consent;
    }

    private void audit(String actorId, String action, String type, String resourceId, Map<String, Object> metadata) {
        try {
            jdbc.sql("""
                    INSERT INTO audit_events (
                      id, actor_user_id, action, resource_type, resource_id,
                      policy_version, metadata, occurred_at
                    ) VALUES (:id, :actorId, :action, :type, :resourceId, :policyVersion, :metadata, :now)
                    """)
                    .param("id", "audit-" + UUID.randomUUID())
                    .param("actorId", actorId)
                    .param("action", action)
                    .param("type", type)
                    .param("resourceId", resourceId)
                    .param("policyVersion", POLICY_VERSION)
                    .param("metadata", objectMapper.writeValueAsString(metadata))
                    .param("now", OffsetDateTime.now(ZoneOffset.UTC))
                    .update();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize audit metadata", exception);
        }
    }

    private String normalizeSort(String sortMode) {
        String sort = sortMode == null ? "OVERALL" : sortMode.toUpperCase();
        return List.of("OVERALL", "FACE", "OUTFIT", "SCENE").contains(sort) ? sort : "OVERALL";
    }

    private record ConsentRow(
            String id,
            String requestId,
            String subjectUserId,
            String status,
            OffsetDateTime expiresAt) {
    }

    private record ConsentSummary(String id, String requestId, String subjectUserId, String status) {
    }

    private record SessionRow(
            String id,
            String requestId,
            String actorUserId,
            String status,
            String modelVersion,
            String consentVersion,
            String resultsJson) {
    }

    private record SessionActor(String userId) {
    }

    public record AlbumDto(String id, String name, int photoCount, String indexStatus, String role) {
    }

    public record SearchRequestDto(
            String id,
            String albumId,
            String requesterId,
            String purposeCode,
            String status,
            int requestedDays,
            Integer approvedDays,
            String decidedBy,
            String createdAt,
            String expiresAt,
            String consentId) {
    }

    public record FactorDto(int face, int outfit, int scene) {
    }

    public record SearchItemDto(
            String photoId,
            String title,
            String albumId,
            String albumName,
            String scene,
            String outfit,
            String date,
            int score,
            FactorDto factors,
            String summary,
            String identityDisclaimer) {
    }

    public record SearchSessionDto(
            String id,
            String requestId,
            String status,
            int pollAfterMs,
            String modelVersion,
            String consentVersion,
            List<SearchItemDto> items) {
    }

    public record AuditEventDto(String id, String action, String resourceType, String resourceId, String occurredAt) {
    }

    public record ExperimentDto(
            String key,
            String modelVersion,
            int rolloutPercent,
            double topKRecall,
            double ndcgAt10,
            int p95LatencyMs) {
    }
}
