package cn.datafuturex.zhishu.ai.briefing.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 从同库 sys_config 读取 SMTP 配置（60s 缓存）。
 */
@Service
@ConditionalOnProperty(prefix = "zhishu.briefing.mail", name = "jdbc-enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SysConfigMailSettingsService {

    private static final long CACHE_TTL_MS = 60_000L;

    private final JdbcTemplate jdbcTemplate;

    private final AtomicReference<CacheEntry> cache = new AtomicReference<>();

    public record MailSettings(
            boolean enabled,
            String host,
            Integer port,
            String username,
            String password,
            String from,
            boolean ssl,
            boolean starttls
    ) {
    }

    private record CacheEntry(MailSettings settings, long loadedAtMs) {
    }

    public MailSettings getMailSettings() {
        CacheEntry entry = cache.get();
        long now = System.currentTimeMillis();
        if (entry != null && (now - entry.loadedAtMs()) < CACHE_TTL_MS) {
            return entry.settings();
        }
        MailSettings loaded = loadFromDb();
        cache.set(new CacheEntry(loaded, now));
        return loaded;
    }

    public void invalidate() {
        cache.set(null);
    }

    private MailSettings loadFromDb() {
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    """
                    SELECT mail_enabled, mail_host, mail_port, mail_username, mail_password,
                           mail_from, mail_ssl, mail_starttls
                    FROM sys_config WHERE id = 1
                    """);
            return new MailSettings(
                    boolVal(row.get("mail_enabled")),
                    stringVal(row.get("mail_host")),
                    intVal(row.get("mail_port")),
                    stringVal(row.get("mail_username")),
                    stringVal(row.get("mail_password")),
                    stringVal(row.get("mail_from")),
                    boolVal(row.get("mail_ssl")),
                    boolVal(row.get("mail_starttls")));
        } catch (Exception e) {
            log.warn("读取 sys_config 邮件配置失败: {}", e.getMessage());
            return new MailSettings(false, null, 465, null, null, null, true, false);
        }
    }

    private static boolean boolVal(Object v) {
        if (v instanceof Boolean b) {
            return b;
        }
        if (v == null) {
            return false;
        }
        return "true".equalsIgnoreCase(String.valueOf(v)) || "1".equals(String.valueOf(v));
    }

    private static Integer intVal(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String stringVal(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return StringUtils.hasText(s) ? s : null;
    }
}
