package cn.datafuturex.zhishu.security.sso;

import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SSO Ticket 支持的签名算法：RS256 与国密 SM2（SM3withSM2）并存，按 Header.alg 选择验签器。
 */
public final class SsoTicketAlgorithm {

    public static final String RS256 = "RS256";
    /** JWT Header.alg 取值；语义为 SM3withSM2 签名 */
    public static final String SM2 = "SM2";

    private static final Set<String> SUPPORTED = Set.of(RS256, SM2);

    private SsoTicketAlgorithm() {
    }

    public static boolean isSupported(String alg) {
        return StringUtils.hasText(alg) && SUPPORTED.contains(alg.trim());
    }

    /**
     * 解析伙伴配置的算法白名单；空则允许全部已支持算法。
     */
    public static Set<String> parseAllowList(String configured) {
        if (!StringUtils.hasText(configured)) {
            return Set.copyOf(SUPPORTED);
        }
        Set<String> allowed = Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(SsoTicketAlgorithm::normalizeConfigured)
                .filter(SUPPORTED::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return allowed.isEmpty() ? Set.copyOf(SUPPORTED) : Set.copyOf(allowed);
    }

    public static boolean isAllowed(String configured, String ticketAlg) {
        return StringUtils.hasText(ticketAlg) && parseAllowList(configured).contains(ticketAlg.trim());
    }

    /**
     * 配置项别名归一：SM3withSM2 / SM2withSM3 → SM2
     */
    static String normalizeConfigured(String value) {
        String v = value.trim();
        String upper = v.toUpperCase(Locale.ROOT);
        if ("SM3WITHSM2".equals(upper) || "SM2WITHSM3".equals(upper) || "SM2".equals(upper)) {
            return SM2;
        }
        if ("RS256".equals(upper)) {
            return RS256;
        }
        return v;
    }
}
