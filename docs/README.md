# 工程文档索引

## 需求与评审

- [需求分析与教师确认](AI-03-分析设计与教师确认.md)：用户角色、需求映射、交互决策、隐私边界和待确认事项。
- [教师评审确认单](AI-03-教师评审确认单.pdf)：两页评审材料，可直接提交老师。

## 系统设计

- [系统设计与接口](AI-03-系统设计与接口.md)：系统架构、检索时序、授权状态机、推荐策略、数据模型、测试用例与实施顺序。
- [OpenAPI 接口契约](api/openapi.yaml)：OpenAPI 3.1 接口定义。
- [PostgreSQL 数据模型](db/schema.sql)：授权、相册、检索、反馈、审计和删除任务表。
- [数据库与核心实现说明](数据库与核心实现说明.md)：规范化结果、清理任务、outbox 和验证结果。
- [运行与测试说明](运行与测试说明.md)：H2/PostgreSQL 启动、健康检查、清理 Worker 和 E2E 验证。
- [PostgreSQL 联调环境](../infra/README.md)：本地 PostgreSQL 容器和 Flyway 迁移入口。

## 原型

- [交互原型](../frontend/prototype/index.html)
- [原型使用说明](../frontend/prototype/README.md)
- [真实 API 联调页面](../frontend/mvp/index.html)
- [React + TypeScript 正式客户端](../frontend/app/index.html)
- [端到端冒烟测试](../scripts/e2e_smoke.py)

## 当前状态

当前阶段使用虚构演示数据，默认以 H2 和确定性特征桩运行完整业务闭环。真实 InsightFace/CLIP/LLaVA、对象存储删除适配器、OIDC/JWT 和外部 PostgreSQL/Milvus 联调仍需在后续阶段完成。
