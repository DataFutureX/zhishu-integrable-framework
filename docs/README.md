# 文档与测试报告

本目录存放说明文档，以及本地生成的测试报告（报告目录已 gitignore，不提交仓库）。

门户独立文档页 `/docs` 会内嵌下列对接说明，以及仓库根目录的快速开始。

## 对接说明

| 文件 | 说明 |
|------|------|
| [单点登录对接说明.md](单点登录对接说明.md) | 万象 / 数智 IoT 等伙伴以 ticket 换票登录云起的协议、接口与联调清单 |
| [万象接入联调实现步骤.md](万象接入联调实现步骤.md) | 万象侧逐步联调清单 |
| [他方SSO接入SDK使用说明.md](他方SSO接入SDK使用说明.md) | 伙伴侧 Java SDK：密钥生成、Ticket 签发、回调 URL（源码见 `sdk/yunqi-sso-partner-sdk`） |

## API 接口测试报告

每次执行后端全量 API 集成测试后，会自动写入 `docs/api-test-report/`：

```bash
cd backend
# Windows 请先切 UTF-8，或使用 verify-api.bat
chcp 65001
mvn -pl yqap-core -am verify
```

| 文件 | 说明 |
|------|------|
| `api-test-report/index.html` | 最新一次报告（覆盖写入） |

报告标题含测试时间；正文含**平台基本信息**、开始/结束时间、耗时、目标接口、输入、输出与测试过程。构建产物另见 `backend/yqap-core/target/api-test-report/`（`target/` 本身已忽略）。

## 前端单元测试报告

```bash
cd frontend
# Windows: test-unit.bat
# Linux / macOS: ./test-unit.sh
npm run test
```

| 文件 | 说明 |
|------|------|
| [`unit-test-report/index.html`](unit-test-report/index.html) | Vitest HTML 报告（覆盖写入） |

## 前端界面冒烟报告

前端 Playwright 冒烟（门户 / 登录 / 业务页可打开）执行后写入本目录：

```bash
cd frontend
npx playwright install chromium   # 首次
# Windows: test-e2e-demo.bat
# Linux / macOS: ./test-e2e-demo.sh
npm run test:e2e:demo             # 演示模式日常冒烟
# 联调发版前：先启动后端 + npm run dev，再执行
npm run test:e2e:integration
npm run test:e2e:report           # 浏览器打开 docs/e2e-test-report
```

| 文件 | 说明 |
|------|------|
| [`e2e-test-report/index.html`](e2e-test-report/index.html) | Playwright HTML 报告（覆盖写入） |

详细说明见 [frontend/README.md](../frontend/README.md)「前端界面冒烟」。
