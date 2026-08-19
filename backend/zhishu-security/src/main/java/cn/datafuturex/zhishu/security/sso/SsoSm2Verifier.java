package cn.datafuturex.zhishu.security.sso;

import cn.hutool.crypto.SmUtil;
import cn.hutool.crypto.asymmetric.SM2;
import lombok.extern.slf4j.Slf4j;

import java.security.PublicKey;

/**
 * 国密 SM2（SM3withSM2）JWT 签名校验。
 * <p>签名值约定为 Base64URL 编码的 DER（ASN.1）格式；若失败再尝试明文 R||S，以兼容部分实现。</p>
 */
@Slf4j
final class SsoSm2Verifier {

    private SsoSm2Verifier() {
    }

    static boolean verify(PublicKey publicKey, byte[] signingInput, byte[] signature) {
        if (publicKey == null || signingInput == null || signature == null || signature.length == 0) {
            return false;
        }
        try {
            SM2 der = SmUtil.sm2(null, publicKey);
            if (der.verify(signingInput, signature)) {
                return true;
            }
            SM2 plain = SmUtil.sm2(null, publicKey);
            plain.usePlainEncoding();
            return plain.verify(signingInput, signature);
        } catch (Exception e) {
            log.warn("SSO SM2 验签异常: {}", e.getMessage());
            return false;
        }
    }
}
