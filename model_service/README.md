# AI-03 Model Service

当前服务是可运行的确定性特征桩，用于验证后端契约、授权链和端到端数据流。它不执行真实人脸识别，也不保存图片。

接口：

- `GET /health`
- `POST /v1/features`
- `POST /v1/explain`

启动：

```bash
python app.py
```

正式实现将把 `extract_features` 替换为 InsightFace + CLIP，把解释接口替换为受证据约束的 LLaVA 服务。
