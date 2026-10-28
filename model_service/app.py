#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import math
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any


def clamp(value: float, lower: float = 0.0, upper: float = 1.0) -> float:
    return max(lower, min(upper, value))


def extract_features(payload: dict[str, Any]) -> dict[str, Any]:
    digest = bytes.fromhex(str(payload.get("sampleSha256", "0" * 64))[:64])
    query = str(payload.get("queryText", "")).lower()
    noise = [digest[index] / 255.0 for index in range(6)]

    vector = {
        "face": round(clamp(0.84 + 0.10 * noise[0]), 4),
        "body": round(clamp(0.78 + 0.12 * noise[1]), 4),
        "outfit": round(clamp(0.76 + 0.12 * noise[2]), 4),
        "scene": round(clamp(0.76 + 0.12 * noise[3]), 4),
        "blue": round(clamp(0.20 + 0.12 * noise[4]), 4),
        "wood": round(clamp(0.18 + 0.12 * noise[5]), 4),
        "indoor": round(clamp(0.18 + 0.10 * noise[0]), 4),
    }
    if any(token in query for token in ("蓝", "azul", "blue")):
        vector["blue"] = 0.97
        vector["outfit"] = max(vector["outfit"], 0.92)
    if any(token in query for token in ("木屋", "木桌", "木制")):
        vector["wood"] = 0.96
        vector["scene"] = max(vector["scene"], 0.91)
    if any(token in query for token in ("室内", "咖啡", "暖光")):
        vector["indoor"] = 0.94
        vector["scene"] = max(vector["scene"], 0.90)
    if any(token in query for token in ("树林", "森林", "户外")):
        vector["wood"] = max(vector["wood"], 0.82)

    return {
        "engine": "deterministic-feature-stub-v1",
        "modelVersion": "stub-multimodal-v1",
        "vector": vector,
        "privacy": {"samplePersisted": False, "identityInference": False},
    }


def bounded_explanation(payload: dict[str, Any]) -> str:
    factors = payload.get("factors", {})
    face = int(factors.get("face", 0))
    outfit = int(factors.get("outfit", 0))
    scene = int(factors.get("scene", 0))
    query = str(payload.get("queryText", "")).strip()
    context = f"；补充描述包含“{query}”" if query else ""
    return f"人脸 {face} 分、衣着 {outfit} 分、场景 {scene} 分构成主要视觉依据{context}。"


class ModelHandler(BaseHTTPRequestHandler):
    server_version = "AI03ModelStub/0.1"

    def _json(self, status: int, body: dict[str, Any]) -> None:
        encoded = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def do_GET(self) -> None:
        if self.path == "/health":
            self._json(200, {
                "status": "UP",
                "service": "ai03-model-service",
                "engine": "deterministic-feature-stub-v1",
                "modelVersion": "stub-multimodal-v1",
            })
        else:
            self._json(404, {"error": "NOT_FOUND"})

    def do_POST(self) -> None:
        if self.path not in ("/v1/features", "/v1/explain"):
            self._json(404, {"error": "NOT_FOUND"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0 or length > 1_000_000:
                self._json(422, {"error": "INVALID_BODY"})
                return
            payload = json.loads(self.rfile.read(length).decode("utf-8"))
            if self.path == "/v1/features":
                self._json(200, extract_features(payload))
            else:
                self._json(200, {
                    "summary": bounded_explanation(payload),
                    "identityDisclaimer": "视觉相似建议，不是身份认定",
                })
        except (ValueError, json.JSONDecodeError):
            self._json(422, {"error": "INVALID_JSON"})
        except Exception:
            self._json(500, {"error": "MODEL_INTERNAL_ERROR"})

    def log_message(self, fmt: str, *args: Any) -> None:
        print(f"{self.address_string()} - {fmt % args}", flush=True)


def main() -> None:
    server = ThreadingHTTPServer(("127.0.0.1", 8090), ModelHandler)
    print("AI-03 model service listening on http://127.0.0.1:8090", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
