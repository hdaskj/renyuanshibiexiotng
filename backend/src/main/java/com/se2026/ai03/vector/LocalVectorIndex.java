package com.se2026.ai03.vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.se2026.ai03.model.ModelClient.ModelFeatures;

@Component
public class LocalVectorIndex implements VectorIndex {
    private record Candidate(
            String id,
            String title,
            String albumId,
            String albumName,
            String scene,
            String outfit,
            String date,
            double face,
            double outfitBase,
            double sceneBase,
            double blue,
            double wood,
            double indoor,
            String reason) {
    }

    private final List<Candidate> candidates = List.of(
            new Candidate("P-0231", "木屋前的蓝色针织衫", "album-camping", "春日露营", "木屋", "蓝色针织衫",
                    "2026.04.18 · 14:32", .95, .94, .91, .96, .94, .45,
                    "人脸轮廓、蓝色针织纹理与木屋场景同时接近。"),
            new Candidate("P-0186", "窗边的侧脸", "album-camping", "春日露营", "木屋", "米白衬衫",
                    "2026.04.18 · 15:08", .93, .76, .96, .18, .91, .82,
                    "侧脸角度和木屋窗边的自然光构成主要相似证据。"),
            new Candidate("P-0274", "拿起咖啡的瞬间", "album-cabin", "山间木屋生日会", "咖啡馆", "蓝色针织衫",
                    "2026.04.18 · 16:21", .84, .95, .87, .94, .79, .92,
                    "蓝色衣着与暖色木质室内场景同时接近。"),
            new Candidate("P-0154", "树林里的回头", "album-camping", "春日露营", "树林", "卡其外套",
                    "2026.04.17 · 11:46", .91, .73, .84, .12, .62, .18,
                    "回头姿态和面部区域接近，场景属于同一活动。"),
            new Candidate("P-0312", "生日会上的笑声", "album-cabin", "山间木屋生日会", "室内", "深绿上衣",
                    "2026.04.20 · 19:42", .88, .79, .77, .15, .58, .88,
                    "面部与笑容接近，暖色灯光提供辅助场景证据。"),
            new Candidate("P-0098", "沿街散步", "album-city", "城市漫步", "城市", "蓝色针织衫",
                    "2026.03.29 · 10:15", .75, .94, .65, .91, .08, .16,
                    "蓝色针织衫与身形轮廓相似，但拍摄场景已变化。"),
            new Candidate("P-0288", "木桌前写卡片", "album-cabin", "山间木屋生日会", "木屋", "米白衬衫",
                    "2026.04.19 · 09:20", .68, .83, .86, .12, .95, .77,
                    "木桌、衣着和室内空间相似，低头造成人脸证据较弱。"),
            new Candidate("P-0119", "天桥下的风", "album-city", "城市漫步", "城市", "灰色风衣",
                    "2026.03.29 · 16:06", .79, .55, .61, .08, .04, .06,
                    "部分面部区域相似，服装和场景差异明显。"),
            new Candidate("P-0208", "帐篷旁的合照", "album-camping", "春日露营", "露营", "酒红围巾",
                    "2026.04.18 · 13:57", .62, .67, .90, .08, .31, .04,
                    "同一活动和时间提供了场景证据，但人物较小且被遮挡。"));

    @Override
    public List<ScoredPhoto> search(ModelFeatures features, String queryText, String sortMode, boolean diversity) {
        int face = percent(features.value("face", .82));
        int outfit = percent(features.value("outfit", .82));
        int scene = percent(features.value("scene", .82));
        double blue = features.value("blue", .25);
        double wood = features.value("wood", .25);
        double indoor = features.value("indoor", .25);

        List<ScoredPhoto> scored = new ArrayList<>();
        for (Candidate candidate : candidates) {
            int candidateFace = clamp((int) Math.round(
                    100 * (.55 * candidate.face + .45 * features.value("face", .82))));
            int candidateOutfit = clamp((int) Math.round(
                    100 * (.55 * candidate.outfitBase
                            + .25 * candidate.blue * blue
                            + .20 * features.value("outfit", .82))));
            int candidateScene = clamp((int) Math.round(
                    100 * (.50 * candidate.sceneBase
                            + .25 * candidate.wood * wood
                            + .15 * candidate.indoor * indoor
                            + .10 * features.value("scene", .82))));
            int overall = clamp((int) Math.round(
                    .42 * candidateFace + .35 * candidateOutfit + .23 * candidateScene));
            scored.add(new ScoredPhoto(
                    candidate.id,
                    candidate.title,
                    candidate.albumId,
                    candidate.albumName,
                    candidate.scene,
                    candidate.outfit,
                    candidate.date,
                    overall,
                    candidateFace,
                    candidateOutfit,
                    candidateScene,
                    candidate.reason));
        }

        Comparator<ScoredPhoto> comparator = switch (sortMode) {
            case "FACE" -> Comparator.comparingInt(ScoredPhoto::face);
            case "OUTFIT" -> Comparator.comparingInt(ScoredPhoto::outfitScore);
            case "SCENE" -> Comparator.comparingInt(ScoredPhoto::sceneScore);
            default -> Comparator.comparingInt(ScoredPhoto::overall);
        };
        scored.sort(comparator.reversed());

        if (diversity) {
            return diversify(scored);
        }
        return scored.stream().limit(9).toList();
    }

    private List<ScoredPhoto> diversify(List<ScoredPhoto> sorted) {
        Map<String, List<ScoredPhoto>> buckets = new LinkedHashMap<>();
        sorted.forEach(photo -> buckets.computeIfAbsent(
                photo.albumId() + "|" + photo.scene(), ignored -> new ArrayList<>()).add(photo));
        List<ScoredPhoto> reordered = new ArrayList<>();
        boolean added = true;
        while (added && reordered.size() < 9) {
            added = false;
            for (List<ScoredPhoto> bucket : buckets.values()) {
                if (!bucket.isEmpty() && reordered.size() < 9) {
                    reordered.add(bucket.remove(0));
                    added = true;
                }
            }
        }
        return reordered;
    }

    private int percent(double value) {
        return clamp((int) Math.round(value <= 1.0 ? value * 100 : value));
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
