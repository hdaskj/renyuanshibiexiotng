package com.se2026.ai03.vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.se2026.ai03.api.ApiException;
import com.se2026.ai03.model.ModelClient.ModelFeatures;

@Component
@Profile("milvus")
public class MilvusVectorIndex extends LocalVectorIndex {
    private static final Logger LOG = LoggerFactory.getLogger(MilvusVectorIndex.class);

    private final RestClient client;
    private final String collection;
    private final boolean failOpen;

    public MilvusVectorIndex(
            RestClient.Builder builder,
            @Value("${app.vector-store.milvus.base-url}") String baseUrl,
            @Value("${app.vector-store.milvus.collection}") String collection,
            @Value("${app.vector-store.milvus.fail-open:false}") boolean failOpen) {
        this.client = builder.baseUrl(baseUrl).build();
        this.collection = collection;
        this.failOpen = failOpen;
    }

    @Override
    public List<ScoredPhoto> search(ModelFeatures features, String queryText, String sortMode, boolean diversity) {
        List<ScoredPhoto> local = super.search(features, queryText, sortMode, diversity);
        try {
            List<String> ids = queryMilvus(features);
            return reorder(local, ids);
        } catch (RestClientException | IllegalArgumentException exception) {
            if (failOpen) {
                LOG.warn("Milvus unavailable; using local index fallback", exception);
                return local;
            }
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "VECTOR_STORE_UNAVAILABLE",
                    "Milvus vector search is unavailable");
        }
    }

    private List<String> queryMilvus(ModelFeatures features) {
        List<Double> vector = List.of(
                features.value("face", 0.8),
                features.value("body", 0.8),
                features.value("outfit", 0.8),
                features.value("scene", 0.8),
                features.value("blue", 0.2),
                features.value("wood", 0.2),
                features.value("indoor", 0.2));
        Map<?, ?> response = client.post()
                .uri("/v2/vectordb/entities/search")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "collectionName", collection,
                        "data", List.of(vector),
                        "annsField", "vector",
                        "limit", 20,
                        "outputFields", List.of("id")))
                .retrieve()
                .body(Map.class);
        if (response == null || !(response.get("data") instanceof List<?> rows)) {
            throw new IllegalArgumentException("Milvus response contains no data");
        }
        List<String> ids = new ArrayList<>();
        for (Object row : rows) {
            if (row instanceof Map<?, ?> item && item.get("id") != null) {
                ids.add(String.valueOf(item.get("id")));
            }
        }
        return ids;
    }

    private List<ScoredPhoto> reorder(List<ScoredPhoto> local, List<String> ids) {
        if (ids.isEmpty()) {
            return local;
        }
        Set<String> ranking = new LinkedHashSet<>(ids);
        Map<String, Integer> rankById = new HashMap<>();
        int rank = 0;
        for (String id : ranking) {
            rankById.put(id, rank++);
        }
        List<ScoredPhoto> reordered = new ArrayList<>();
        local.stream()
                .filter(photo -> ranking.contains(photo.photoId()))
                .sorted((a, b) -> Integer.compare(
                        rankById.get(a.photoId()),
                        rankById.get(b.photoId())))
                .forEach(reordered::add);
        local.stream().filter(photo -> !ranking.contains(photo.photoId())).forEach(reordered::add);
        return reordered.stream().limit(9).toList();
    }
}
