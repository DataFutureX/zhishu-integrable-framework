# 开发联调私钥（勿提交）

本目录存放**仅用于本地/联调**的伙伴 RSA 私钥。

| 文件 | 对应 iss | 知枢公钥 |
|------|----------|----------|
| `wanxiang-private.pem` | `wanxiang` | `classpath:sso/wanxiang.pem` |
| `shuzhi-iot-private.pem` | `shuzhi-iot` | `classpath:sso/shuzhi-iot.pem` |

约定：

- 私钥**不得**进入 git（根目录 `.gitignore` 已忽略 `*.pem`）。
- 生产环境由各伙伴自行保管私钥；知枢只登记公钥文件路径（见 `application-prod.yml` 环境变量）。
- 重新生成密钥对后，需同步替换 `backend/zhishu-core/src/main/resources/sso/*.pem` 中的公钥。

签发 Ticket 时 Header 建议携带：

- 万象：`kid: wanxiang-2026`
- 数智 IoT：`kid: shuzhi-iot-2026`

## 一键签发（联调）

在仓库根目录执行：

```powershell
.\docs\dev-keys\issue-ticket.ps1 -Issuer wanxiang -Username admin
.\docs\dev-keys\issue-ticket.ps1 -Issuer shuzhi-iot -Username admin -WebBase http://localhost:3000 -Redirect /home/dashboard
```

脚本会打印完整 `ticket` 与浏览器回调 URL（`/sso/callback?...`）。  
请确保知枢已预开通同名 `sys_user`，且后端 `yunqi.sso.enabled=true`。
