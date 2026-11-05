#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import urllib.request


BASE = os.getenv("MILVUS_URL", "http://127.0.0.1:19530")
COLLECTION = os.getenv("MILVUS_COLLECTION", "ai03_photos")

PHOTOS = [
    ("P-0231", "木屋前的蓝色针织衫", [0.95, 0.90, 0.94, 0.91, 0.96, 0.94, 0.45]),
    ("P-0186", "窗边的侧脸", [0.93, 0.88, 0.76, 0.96, 0.18, 0.91, 0.82]),
    ("P-0274", "拿起咖啡的瞬间", [0.84, 0.86, 0.95, 0.87, 0.94, 0.79, 0.92]),
    ("P-0154", "树林里的回头", [0.91, 0.83, 0.73, 0.84, 0.12, 0.62, 0.18]),
    ("P-0312", "生日会上的笑声", [0.88, 0.82, 0.79, 0.77, 0.15, 0.58, 0.88]),
    ("P-0098", "沿街散步", [0.75, 0.79, 0.94, 0.65, 0.91, 0.08, 0.16]),
    ("P-0288", "木桌前写卡片", [0.68, 0.77, 0.83, 0.86, 0.12, 0.95, 0.77]),
    ("P-0119", "天桥下的风", [0.79, 0.71, 0.55, 0.61, 0.08, 0.04, 0.06]),
    ("P-0208", "帐篷旁的合照", [0.62, 0.68, 0.67, 0.90, 0.08, 0.31, 0.04]),
]


def post(path: str, payload: dict) -> dict:
    request = urllib.request.Request(
        BASE + path,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json", "User-Agent": "AI03-Milvus-Init"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        body = response.read()
        return json.loads(body) if body else {}


def main() -> None:
    post("/v2/vectordb/collections/create", {
        "collectionName": COLLECTION,
        "dimension": 7,
        "metricType": "COSINE",
        "idType": "VarChar",
        "maxLength": 64,
        "enableDynamicField": True,
    })
    data = [
        {
            "id": photo_id,
            "title": title,
            "vector": vector,
        }
        for photo_id, title, vector in PHOTOS
    ]
    result = post("/v2/vectordb/entities/insert", {
        "collectionName": COLLECTION,
        "data": data,
    })
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
