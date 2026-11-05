# React Web

正式 React 客户端，使用 Vite 开发、TypeScript 校验和 `/api` 代理。

![React 客户端](preview.png)

```bash
pnpm install
pnpm dev
```

开发地址：`http://127.0.0.1:5173/`

构建：

```bash
pnpm build
```

生产部署由 Nginx 将 `/api/` 代理到 Spring Boot。

当前 Web 模块包含参与者检索、所有者审批和审计/实验三个业务域，所有数据均来自真实后端。
