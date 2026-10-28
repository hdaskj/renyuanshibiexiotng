# AI-03 Spring Boot Backend

当前后端实现了端到端 MVP：

- 开发登录与角色分离。
- 参与者提交检索申请。
- 所有者审批并生成临时授权。
- 调用 Python 模型服务提取多模态向量。
- 在本地向量索引适配器中执行授权范围内召回。
- 保存结果、反馈和审计事件。
- 撤回授权后拒绝旧结果并删除会话结果。

运行前需要 Java 21 和 Maven。默认连接 `http://localhost:8090` 模型服务，使用 H2 文件数据库。

```bash
mvn spring-boot:run
```

生产环境应切换到 PostgreSQL、Milvus/Faiss、OIDC/JWT、对象存储和可靠删除任务。
