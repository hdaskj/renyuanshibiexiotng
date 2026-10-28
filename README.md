# SE2026-AI-03：基于多模态大模型的相似人员照片推荐系统

> 项目编号：SE2026-AI-03
>
> 当前阶段：交互原型、系统设计与可运行端到端 MVP

相寻是一个面向**已授权私人相册**的照片发现系统。用户可以上传一张检索样本并补充衣着、场景等自然语言描述，系统结合人脸、人体、衣着和场景特征返回可能与其本人有关的照片，并展示推荐依据。

项目严格限制使用场景：不提供公共场所找人、陌生人检索、真实身份核验、批量导出或监控能力。

![交互原型预览](frontend/prototype/prototype-preview.png)

![真实 API 联调页面](frontend/mvp/preview.png)

## 当前完成内容

- 明示同意、授权范围、有效期和撤回入口。
- 照片上传、权利确认和模拟特征提取流程。
- 人脸、衣着、场景的多模态检索交互。
- 综合、人脸、衣着、场景排序及多样性重排。
- 单张结果的证据分解和推荐解释。
- 相关/不相关反馈及反馈撤销。
- 相册所有者检索申请审批、7 天临时授权和审计日志。
- 撤回授权、停止检索和索引清理状态。
- 桌面端和移动端响应式布局。
- 系统架构、授权状态机、OpenAPI 接口和 PostgreSQL 数据模型。
- Spring Boot 授权与检索编排后端。
- Python 多模态特征服务（当前为可替换的确定性特征桩）。
- 真实 API 联调页面、反馈记录、审计和撤回阻断。

## 快速体验

原型是无需安装依赖的离线单页应用。

```bash
git clone https://github.com/hdaskj/renyuanshibiexiotng.git
cd renyuanshibiexiotng
```

直接打开：

```text
frontend/prototype/index.html
```

也可以启动本地静态服务器：

```bash
python -m http.server 8080 --directory frontend/prototype
```

访问 `http://localhost:8080/`。

推荐演示路径：

1. 勾选用途与权利确认，进入个人相册。
2. 打开任一照片的“解释”，查看人脸、衣着、场景证据。
3. 输入“穿蓝色针织衫，在木屋咖啡馆”，观察语义重排。
4. 切换排序并开启“增加多样性”。
5. 对结果选择相关或不相关，观察反馈状态。
6. 进入“相册协作”，审批检索申请并查看审计日志。
7. 进入“授权与隐私”，演示撤回授权和索引清理。

## 启动可运行 MVP

当前 MVP 使用 H2 文件数据库和本地向量索引适配器，以便先验证完整业务链路。正式版本再替换为 PostgreSQL 与 Milvus/Faiss。

环境要求：

- JDK 17
- Maven 3.9+
- Python 3.10+

### 1. 启动模型服务

```bash
cd model_service
python app.py
```

服务地址：`http://localhost:8090`

### 2. 启动后端

```bash
cd backend
mvn spring-boot:run
```

服务地址：`http://localhost:8081`

### 3. 启动 API 联调页面

```bash
cd frontend/mvp
python -m http.server 8080 --bind 127.0.0.1
```

访问 `http://127.0.0.1:8080/`，使用以下开发账户：

- `participant`：林岚，参与者。
- `owner`：林澜，相册所有者。
- `admin`：系统管理员。

### Docker Compose

```bash
docker compose up --build
```

访问 `http://localhost:8080/`。

### 冒烟测试

模型服务和后端启动后：

```bash
python scripts/e2e_smoke.py
```

测试覆盖申请、审批、样本上传、特征调用、授权内召回、反馈、审计和撤回。

## 工程文档

- [文档索引](docs/README.md)
- [需求分析与教师确认](docs/AI-03-分析设计与教师确认.md)
- [系统设计与接口](docs/AI-03-系统设计与接口.md)
- [OpenAPI 接口契约](docs/api/openapi.yaml)
- [PostgreSQL 数据模型](docs/db/schema.sql)
- [教师评审确认单](docs/AI-03-教师评审确认单.pdf)

## 目录结构

```text
.
├── frontend/
│   ├── mvp/                    # 真实 API 联调页面
│   └── prototype/              # 高保真交互原型
├── backend/                    # Spring Boot 后端
├── model_service/              # Python 特征服务
├── scripts/                    # E2E 冒烟测试
├── vector_store/               # 后续向量库适配
├── docs/
│   ├── api/                    # OpenAPI 接口契约
│   ├── db/                     # PostgreSQL 数据模型
│   ├── AI-03-分析设计与教师确认.md
│   ├── AI-03-系统设计与接口.md
│   └── AI-03-教师评审确认单.pdf
├── .gitignore
└── README.md
```

当前 `backend/` 已实现授权、审批、检索编排、反馈、审计与撤回；`model_service/` 是可运行的特征服务桩。Milvus/Faiss、真实 InsightFace/CLIP/LLaVA、React 工程化版本和 PostgreSQL 切换仍待后续完成。

## 技术路线

| 模块 | 计划技术 |
| --- | --- |
| 前端 | React |
| 后端 | Spring Boot |
| 特征提取 | InsightFace、CLIP、LLaVA |
| 向量检索 | Milvus 或 Faiss |
| 元数据 | PostgreSQL |
| 模型编排 | LangChain |
| 监控与实验 | Prometheus、Grafana、A/B 分流 |

## 验收口径

- Top-K 召回率与 NDCG 的 K 值、标注集和计算规则待老师确认。
- 推荐响应时间的课程目标为服务端 P95 小于 500ms，不含上传和前端渲染。
- 撤回授权后，新检索必须立即被阻断，派生向量进入清理流程。
- 推荐结果只能表述为“视觉相似建议”，不能作为真实身份认定。

## 数据与伦理

- 仅处理用户拥有合法使用权的私人相册内容。
- 检索前需要相册所有者批准，并记录用途、范围和有效期。
- 检索样本仅在当前任务中使用，不建立可搜索的公开身份库。
- 用户可关闭单个相册授权或撤回全部授权。
- 系统不提供公众场所监控、批量找人、身份核验和公开分享能力。

## 当前限制

当前 MVP 已经打通真实前后端与模型服务调用，但模型仍为确定性特征桩，向量召回使用进程内适配器。CLIP/InsightFace/LLaVA 真实推理、Milvus/Faiss、React 工程化版本、PostgreSQL、灰度发布与生产监控仍待接入；演示分值和响应时间不代表真实模型效果。
