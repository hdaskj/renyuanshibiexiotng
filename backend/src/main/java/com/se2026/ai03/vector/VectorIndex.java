package com.se2026.ai03.vector;

import java.util.List;

import com.se2026.ai03.model.ModelClient.ModelFeatures;

public interface VectorIndex {
    List<ScoredPhoto> search(ModelFeatures features, String queryText, String sortMode, boolean diversity);

    record ScoredPhoto(
            String photoId,
            String title,
            String albumId,
            String albumName,
            String scene,
            String outfit,
            String date,
            int overall,
            int face,
            int outfitScore,
            int sceneScore,
            String reason) {
    }
}
