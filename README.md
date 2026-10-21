# SE2026-AI-03：基于多模态大模型的相似人员照片推荐系统
> 项目编号：SE2026-AI-03
> 基于CLIP / LLaVA + InsightFace实现人脸、衣着、场景多模态语义嵌入，结合Milvus/Faiss向量数据库完成跨模态相似人员检索与智能推荐

## ✨ 项目简介
本系统接收用户上传人员照片，提取人脸、人体、衣着、场景多模态特征向量，利用向量检索引擎召回相似样本，由多模态大模型生成推荐解释；同时接入用户反馈，构建推荐闭环，支持推荐结果排序、多样性调控，面向非监控场景的人像相似检索推荐，严格遵守隐私伦理规范。

## 🧠 AI & SE 技术亮点
- AI点：人脸/人体多模态嵌入 + 向量检索 + CLIP/LLaVA跨模态语义匹配
- SE点：大规模向量库、推荐服务、冷启动策略、A/B实验灰度测试

## 📌 核心功能
- 照片上传与多模态特征自动提取
- 相似人员跨模态检索（人脸+衣着+场景融合）
- 推荐结果重排与自然语言结果解释
- 用户点赞/负反馈闭环，迭代推荐策略
- 指标统计、检索耗时监控

## 🛠 技术栈
- 多模态模型：CLIP、LLaVA、InsightFace
- 向量检索：Faiss / Milvus
- LLM编排：LangChain
- 后端服务：Spring Boot
- 前端页面：React
- 开发环境：Python3.10+, JDK17+, Node.js

## 📊 验收指标
1. Top-K 召回率
2. NDCG 推荐排序指标
3. 单条推荐响应时间 < 500ms

## 📂 目录结构参考
├── backend/                # SpringBoot 后端服务
├── frontend/               # React 前端页面
├── model_service/          # 多模态特征提取服务 (Python)
├── vector_store/           # 向量库初始化、索引脚本
├── docs/                   # 设计文档、接口文档、实验报告
├── scripts/                # 测试、指标评估脚本
├── .gitignore              # 项目忽略配置
└── README.md

## 🚀 快速启动
> 环境准备：Python虚拟环境、JDK、Node、Milvus/Faiss环境
1. 克隆仓库
```bash
git clone https://github.com/HDASKJ/renyuanshibiexietong.git
cd renyuanshibiexietong
