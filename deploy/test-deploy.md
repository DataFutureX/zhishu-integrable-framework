# 测试环境部署

## 测试服务器SSH方式

ssh -i "D:/LetsWork-Docs/hyf_ed25519" root@192.168.11.14

前端程序和后端程序，分别以docker容器的方式部署在服务器上，运行程序有映射存储在宿主机路径上



## 后端打包发布测试服务器

后端部署路径：/home/letswork/zhishu-backend/deployment/app/zhishu-core-1.0.0.jar

备份目录下已经有的jar，按照日期命名备份，并上传新的jar，重启容器，容器名称为：zhishu-app



## 前端打包发布测试服务器

前端部署路径 ：/home/letswork/zhishu-frontend/deployment/web

web即目标部署的前端包，即将打包后的原dist文件夹命名为web，每次部署先备份web，按照日期命名备份文件夹，备份完成后，再替换为新的包，不需要重启容器，容器名称为：zhishu-web



---

## 一键部署脚本（推荐）

上述流程已由 `deploy/deploy-test.ps1` 实现（PowerShell 7），打包复用仓库既有的
`zhishu-backend/deployment/scripts/package-jar.ps1` 与 `zhishu-frontend/deployment/scripts/package-web.ps1`。

```powershell
.\deploy\deploy-test.ps1            # 交互菜单：1 前后端 / 2 仅前端 / 3 仅后端
```

| 参数 | 作用 |
| --- | --- |
| `-Target both / frontend / backend` | 跳过菜单，便于复用与自动化 |
| `-Check` | 只检查环境与远端现状（容器 / jar / 站点 / 健康码），不打包不部署 |
| `-DryRun` | 只打印将执行的本地与远端命令 |
| `-Rollback both / frontend / backend` | 用服务器最近一次 `*.bak-*` 备份回滚 |
| `-SkipBuild` | 不重新打包，直接用已暂存的 `deployment/app` 与 `deployment/web` |
| `-RunTests` / `-Demo` / `-SkipTypeCheck` | 后端带单测打包 / 前端演示模式 / 跳过 vue-tsc |
| `-FixKeyPermissions` | 先备份再收紧私钥 ACL（见下方“私钥权限”） |
| `-Force` | 跳过部署前的二次确认 |
| `-Server` `-SshUser` `-SshKey` `-AppPort` `-WebPort` | 覆盖默认目标与端口 |

执行内容：

- 后端：`mvn package` -> 远端旧 jar 备份为 `*.bak-时间戳` -> scp 到 `.upload-时间戳` 后 `mv -f` 原子替换
  -> `docker compose restart`（失败兜底 `docker restart zhishu-app`）-> 轮询 `/api/v1/system/health` 直到 200
- 前端：`npm run build` -> 本地 `tar` 打包 -> 远端 `cp -a web web.bak-时间戳` -> 解压到 `web.new`
  -> 清空 `web/` 内容后 `cp -a` **原地替换** -> 不重启容器

> 前端必须原地覆盖目录内容：nginx 以 bind mount 挂载 `./web`，若把 `web` 目录整体重命名再新建，
> 容器内看到的仍是旧 inode，站点不会更新。

> 换 jar 只需 `restart`；只有改了 `deployment/.env` 或 `docker-compose.yml` 才需要 `docker compose up -d`。

回滚：

```powershell
.\deploy\deploy-test.ps1 -Rollback frontend -Force   # 或 backend / both
```

## 访问地址（实测端口，与 compose 一致）

| 服务 | 容器 | 地址 | 说明 |
| --- | --- | --- | --- |
| 后端 | `zhishu-app` | `http://192.168.11.14:8180/` | `APP_PORT=8180`；健康检查 `/api/v1/system/health` |
| 前端 | `zhishu-web` | `http://192.168.11.14:8080/` | `WEB_PORT=8080`、`NGINX_HTTP_PORT=8080`，**不是 80** |
| 前端 HTTPS | `zhishu-web` | `https://192.168.11.14:8443/` | 映射 `8443->443`，需 `ENABLE_HTTPS=true` |

其他常驻容器：`pgvector`（5432）、`portainer`（8000/9443）。

## 私钥权限（Windows 上的必踩坑）

OpenSSH 会拒用权限过宽的私钥，报错形如：

```
WARNING: UNPROTECTED PRIVATE KEY FILE!
Load key "D:/LetsWork-Docs/hyf_ed25519": bad permissions
root@192.168.11.14: Permission denied (publickey)
```

修复（脚本加 `-FixKeyPermissions` 会自动执行；以下是手工等价命令）：

```powershell
icacls D:/LetsWork-Docs/hyf_ed25519 /save $env:TEMP\key-acl-backup.txt   # 先备份
icacls D:/LetsWork-Docs/hyf_ed25519 /inheritance:r /grant:r "$env:USERNAME:(R)"
# 恢复：icacls D:/LetsWork-Docs/hyf_ed25519 /restore $env:TEMP\key-acl-backup.txt
```

## 2026-10-23 只读验证结果

```
SSH 连通正常
后端 jar：/home/letswork/zhishu-backend/deployment/app/zhishu-core-1.0.0.jar（167313147 B，Oct 21 10:04，root:root）
前端站点：/home/letswork/zhishu-frontend/deployment/web（assets index.html logo.svg portal ...）
容器：zhishu-app Up 26h ; zhishu-web Up 26h (healthy) ; pgvector Up 44h (healthy) ; portainer Up 27h
compose：两个 deployment 目录均有 docker-compose.yml + .env，服务名分别 zhishu-app / zhishu-web
健康检查：后端 200、前端 8080 返回 200
```

当时远端只有 1 个 jar 且无任何 `*.bak-*` 备份，说明该环境尚未通过脚本做过升级；首次部署后才会产生可回滚的备份链。

## 常见故障速查

| 现象 | 处理 |
| --- | --- |
| `bad permissions` / `Permission denied (publickey)` | 见上方私钥权限；或确认公钥在服务器 `~root/.ssh/authorized_keys` |
| `未找到 mvn` / `未找到 npm` | 后端需 Maven 3.9+ 与 JDK 21，前端需 Node.js 18+ 并 `npm install` |
| 健康检查超时 | `ssh -i <key> root@192.168.11.14 'docker logs --tail 200 zhishu-app'` |
| 前端部署后浏览器仍看到旧页面 | 确认是原地覆盖（非重命名目录）；`index.html` 已设 no-cache，强刷即可 |
| 改了 `.env` 不生效 | `docker compose restart` 不重读 `.env`，需 `docker compose up -d` |
| 需要回退 | `.\deploy\deploy-test.ps1 -Rollback backend -Force`（或 frontend/both） |