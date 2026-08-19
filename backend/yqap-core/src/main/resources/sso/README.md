# SSO 伙伴公钥

本目录存放万象、数智 IoT 等伙伴的 **验签公钥**（PEM，X.509 SubjectPublicKeyInfo）。

- 支持 **RSA**（`alg=RS256`）与 **国密 SM2**（`alg=SM2`，SM3withSM2）。
- 仅公钥可入库；对应私钥见 `docs/dev-keys/`（已 gitignore）。
- 配置项：`yunqi.sso.partners.<name>.public-key` / `public-keys.<kid>`
- 同一伙伴可同时登记 RSA 与 SM2 公钥（不同 `kid`）；验签按票据 `Header.alg` + `kid` 选择。
- 生产可通过环境变量覆盖为 `file:/path/to/partner.pem`
