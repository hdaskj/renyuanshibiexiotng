# Milvus Vector Store

`init_milvus.py` creates the `ai03_photos` collection and inserts the prototype photo metadata vectors.

```bash
MILVUS_URL=http://127.0.0.1:19530 python init_milvus.py
```

Use the backend profile `milvus`:

```bash
SPRING_PROFILES_ACTIVE=milvus,postgres mvn spring-boot:run
```

The current collection is a demonstration dataset. Production indexing must write one partition per tenant/album and record every vector ID in `photo_embeddings`.
