package com.se2026.ai03.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.se2026.ai03.api.ApiException;

@Component
public class ModelClient {
    private final RestClient client;
    private final ObjectMapper objectMapper;

    public ModelClient(
            RestClient.Builder builder,
            @Value("${app.model-service.base-url}") String baseUrl,
            ObjectMapper objectMapper) {
        this.client = builder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
    }

    public ModelFeatures extract(byte[] sample, String contentType, String queryText) {
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "sampleSha256", sha256(sample),
                    "sampleSize", sample.length,
                    "contentType", contentType == null ? "application/octet-stream" : contentType,
                    "queryText", queryText == null ? "" : queryText));
            Map<?, ?> response = client.post()
                    .uri("/v1/features")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Content-Length", Integer.toString(requestBody.getBytes(StandardCharsets.UTF_8).length))
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);
            if (response == null || !(response.get("vector") instanceof Map<?, ?> rawVector)) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "MODEL_BAD_RESPONSE", "Feature service returned no vector");
            }
            Map<String, Double> vector = new java.util.HashMap<>();
            rawVector.forEach((key, value) -> vector.put(String.valueOf(key), ((Number) value).doubleValue()));
            return new ModelFeatures(
                    String.valueOf(response.get("modelVersion")),
                    vector,
                    String.valueOf(response.get("engine")));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize model request", exception);
        } catch (RestClientException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MODEL_UNAVAILABLE", "Feature service is unavailable");
        }
    }

    private String sha256(byte[] input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input);
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record ModelFeatures(String modelVersion, Map<String, Double> vector, String engine) {
        public double value(String key, double fallback) {
            return vector.getOrDefault(key, fallback);
        }
    }
}
