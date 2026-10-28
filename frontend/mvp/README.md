# API 联调台

该页面连接真实的 Spring Boot 后端和 Python 模型服务，用于验证端到端流程。

![API 联调页面](preview.png)

本地启动：

```bash
python -m http.server 8080 --bind 127.0.0.1
```

开发账户：

- `participant`：林岚，参与者。
- `owner`：林澜，相册所有者。
- `admin`：系统管理员。

页面中的开发令牌只保存在当前内存，生产环境应替换为 Spring Security + OIDC/JWT。
