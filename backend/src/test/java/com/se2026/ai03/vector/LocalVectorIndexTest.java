package com.se2026.ai03.vector;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.se2026.ai03.model.ModelClient.ModelFeatures;

class LocalVectorIndexTest {
    private final LocalVectorIndex index = new LocalVectorIndex();

    @Test
    void multimodalQueryPromotesBlueOutfitAndWoodScene() {
        ModelFeatures features = new ModelFeatures(
                "test",
                Map.of(
                        "face", .92,
                        "outfit", .95,
                        "scene", .93,
                        "blue", .98,
                        "wood", .97,
                        "indoor", .91),
                "test");

        List<VectorIndex.ScoredPhoto> results = index.search(features, "蓝色针织衫 木屋", "OVERALL", false);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).title()).contains("蓝色针织衫");
        assertThat(results.get(0).overall()).isGreaterThan(80);
    }

    @Test
    void diversityKeepsMoreThanOneAlbumNearTheTop() {
        ModelFeatures features = new ModelFeatures(
                "test",
                Map.of("face", .9, "outfit", .9, "scene", .9, "blue", .9, "wood", .9, "indoor", .9),
                "test");

        List<VectorIndex.ScoredPhoto> results = index.search(features, "", "SCENE", true);

        assertThat(results).hasSize(9);
        assertThat(results.stream().limit(4).map(VectorIndex.ScoredPhoto::albumId).distinct().count())
                .isGreaterThan(1);
    }
}
