package com.se2026.ai03.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.se2026.ai03.auth.AuthUser;
import com.se2026.ai03.config.WebConfig;
import com.se2026.ai03.service.WorkflowService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/v1")
public class AppController {
    private final WorkflowService workflow;

    public AppController(WorkflowService workflow) {
        this.workflow = workflow;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "service", "ai03-photo-backend", "version", "0.1.0");
    }

    @GetMapping("/albums")
    public List<WorkflowService.AlbumDto> albums(
            @RequestAttribute(WebConfig.CURRENT_USER_ATTRIBUTE) AuthUser user) {
        return workflow.listAlbums(user);
    }

    @PostMapping("/search-requests")
    public ResponseEntity<WorkflowService.SearchRequestDto> createRequest(
            @RequestAttribute(WebConfig.CURRENT_USER_ATTRIBUTE) AuthUser user,
            @Valid @RequestBody CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workflow.createRequest(user, request.albumId(), request.requestedDays()));
    }

    @GetMapping("/search-requests")
    public List<WorkflowService.SearchRequestDto> requests(
            @RequestAttribute(WebConfig.CURRENT_USER_ATTRIBUTE) AuthUser user,
            @RequestParam("albumId") String albumId) {
        return workflow.listRequests(user, albumId);
    }

    @PatchMapping("/search-requests/{requestId}")
    public WorkflowService.SearchRequestDto decide(
            @RequestAttribute(WebConfig.CURRENT_USER_ATTRIBUTE) AuthUser user,
            @PathVariable("requestId") String requestId,
            @Valid @RequestBody DecisionRequest request) {
        return workflow.decide(user, requestId, request.decision(), request.approvedDays());
    }

    @PostMapping(value = "/search-sessions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<WorkflowService.SearchSessionDto> search(
            @RequestAttribute(WebConfig.CURRENT_USER_ATTRIBUTE) AuthUser user,
            @RequestPart("sample") MultipartFile sample,
            @RequestParam("requestId") String requestId,
            @RequestParam(value = "queryText", defaultValue = "") String queryText,
            @RequestParam(value = "sort", defaultValue = "OVERALL") String sort,
            @RequestParam(value = "diversity", defaultValue = "true") boolean diversity) throws Exception {
        WorkflowService.SearchSessionDto response = workflow.startSearch(
                user,
                requestId,
                queryText,
                sort,
                diversity,
                sample.getBytes(),
                sample.getContentType());
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/search-sessions/{sessionId}/results")
    public WorkflowService.SearchSessionDto results(
            @RequestAttribute(WebConfig.CURRENT_USER_ATTRIBUTE) AuthUser user,
            @PathVariable("sessionId") String sessionId) {
        return workflow.getResults(user, sessionId);
    }

    @PostMapping("/feedback")
    public Map<String, Object> feedback(
            @RequestAttribute(WebConfig.CURRENT_USER_ATTRIBUTE) AuthUser user,
            @Valid @RequestBody FeedbackRequest request) {
        return workflow.feedback(user, request.sessionId(), request.resultId(), request.relevance());
    }

    @DeleteMapping("/consents/{consentId}")
    public Map<String, Object> revoke(
            @RequestAttribute(WebConfig.CURRENT_USER_ATTRIBUTE) AuthUser user,
            @PathVariable("consentId") String consentId) {
        return workflow.revokeConsent(user, consentId);
    }

    @GetMapping("/audit-events")
    public List<WorkflowService.AuditEventDto> audit(
            @RequestAttribute(WebConfig.CURRENT_USER_ATTRIBUTE) AuthUser user,
            @RequestParam("albumId") String albumId) {
        return workflow.auditEvents(user, albumId);
    }

    @GetMapping("/experiments/summary")
    public List<WorkflowService.ExperimentDto> experiments(
            @RequestAttribute(WebConfig.CURRENT_USER_ATTRIBUTE) AuthUser user) {
        return workflow.experiments(user);
    }

    public record CreateRequest(
            @NotBlank String albumId,
            @Min(1) @Max(14) int requestedDays) {
    }

    public record DecisionRequest(
            @NotBlank String decision,
            @Min(1) @Max(7) Integer approvedDays) {
    }

    public record FeedbackRequest(
            @NotBlank String sessionId,
            @NotBlank String resultId,
            @NotBlank String relevance) {
    }
}
