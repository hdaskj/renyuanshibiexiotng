import hashlib
import unittest

from app import bounded_explanation, extract_features


class ModelServiceTest(unittest.TestCase):
    def test_blue_and_wood_query_adjusts_semantic_axes(self):
        result = extract_features({
            "sampleSha256": hashlib.sha256(b"sample").hexdigest(),
            "queryText": "穿蓝色针织衫，在木屋咖啡馆",
        })
        self.assertEqual(result["modelVersion"], "stub-multimodal-v1")
        self.assertGreater(result["vector"]["blue"], 0.9)
        self.assertGreater(result["vector"]["wood"], 0.9)
        self.assertFalse(result["privacy"]["samplePersisted"])

    def test_explanation_contains_factors_and_no_identity_claim(self):
        text = bounded_explanation({
            "factors": {"face": 95, "outfit": 92, "scene": 91},
            "queryText": "蓝色针织衫",
        })
        self.assertIn("人脸 95", text)
        self.assertIn("视觉依据", text)


if __name__ == "__main__":
    unittest.main()
