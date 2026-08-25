package cn.datafuturex.yunqi.sso.sdk;

/**
 * Ticket 签名算法。须与云起登记的伙伴公钥类型一致。
 */
public enum SsoAlgorithm {

    /** RSA + SHA-256，JWT Header.alg = RS256 */
    RS256("RS256"),

    /** 国密 SM3withSM2，JWT Header.alg = SM2 */
    SM2("SM2");

    private final String jwtAlg;

    SsoAlgorithm(String jwtAlg) {
        this.jwtAlg = jwtAlg;
    }

    public String jwtAlg() {
        return jwtAlg;
    }

    public static SsoAlgorithm fromJwtAlg(String alg) {
        if (alg == null) {
            throw new IllegalArgumentException("alg 不能为空");
        }
        String v = alg.trim();
        for (SsoAlgorithm item : values()) {
            if (item.jwtAlg.equalsIgnoreCase(v)) {
                return item;
            }
        }
        throw new IllegalArgumentException("不支持的 alg: " + alg + "（仅 RS256 / SM2）");
    }
}
