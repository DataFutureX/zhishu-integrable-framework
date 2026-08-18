# SSO 伙伴公钥

本目录存放万象、数智 IoT 等伙伴的 **RSA 公钥**（PEM，X.509 SubjectPublicKeyInfo）。

- 仅公钥可入库；对应私钥见 `docs/dev-keys/`（已 gitignore）。
- 配置项：`yunqi.sso.partners.<name>.public-key`
- 生产可通过环境变量覆盖为 `file:/path/to/partner.pem`
