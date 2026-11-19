# PostgreSQL 联调环境

课程演示默认使用 H2 文件数据库。需要验证 Flyway 和真实 PostgreSQL 时，在仓库根目录执行：

```bash
docker compose -f infra/postgres-compose.yml up -d
```

然后在后端目录使用 PostgreSQL profile：

```bash
SPRING_PROFILES_ACTIVE=postgres \
DB_URL=jdbc:postgresql://127.0.0.1:5432/ai03 \
DB_USER=ai03 \
DB_PASSWORD=ai03 \
mvn spring-boot:run
```

后端启动时会按 `V1` 到 `V4` 执行迁移。确认数据库健康：

```bash
curl http://127.0.0.1:8081/api/v1/health/dependencies
```

停止并保留数据：

```bash
docker compose -f infra/postgres-compose.yml down
```

该目录只提供 PostgreSQL。Milvus 仍需要单独启动，并通过 `SPRING_PROFILES_ACTIVE=postgres,milvus` 和 `MILVUS_URL` 接入。
