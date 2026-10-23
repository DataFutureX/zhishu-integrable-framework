package cn.datafuturex.zhishu.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 建表脚本的会话状态护栏：{@code db/*.sql} 里不得出现会话级的 {@code search_path} 重置。
 * <p>
 * 背景：脚本由 {@code ApplicationRunner} 用 {@code ResourceDatabasePopulator} 在连接池的
 * 连接上执行，脚本里的 {@code set_config('search_path', ..., false)}（pg_dump 的产物）是
 * <b>会话级</b>设置，执行完连接归还池子时不会被还原。后续在同一连接上跑的不带 schema 限定的
 * SQL（如 {@code FROM ai_mcp_upstream}）就会报 {@code relation "xxx" does not exist}，
 * 而库里其实有表 —— 表现为启动期随机崩溃、极难排查。
 * <p>
 * 约定：脚本内的对象名一律写全 {@code public.} 前缀，不要依赖/修改会话的 search_path。
 */
class DatabaseScriptSessionStateTest {

    /**
     * 命中 pg_dump 的 {@code set_config('search_path', ...)} 或手写的
     * {@code SET/RESET [SESSION] search_path}。
     */
    private static final Pattern SESSION_SEARCH_PATH = Pattern.compile(
            "set_config\\s*\\(\\s*'search_path'|\\b(set|reset)\\s+(session\\s+)?search_path\\b",
            Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("db 脚本不得残留会话级 search_path 设置")
    void dbScriptsMustNotChangeSessionSearchPath() throws Exception {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath*:db/*.sql");
        assertTrue(resources.length > 0, "未读到任何 db/*.sql 脚本，检查 classpath 资源路径");

        List<String> offenders = new ArrayList<>();
        for (Resource resource : resources) {
            try (var in = resource.getInputStream()) {
                String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                appendOffenders(resource.getFilename(), sql, offenders);
            }
        }
        assertTrue(offenders.isEmpty(),
                "以下脚本会修改连接池连接的会话 search_path（执行完不清除，后续不带 schema 限定的 SQL 会报表不存在）："
                        + System.lineSeparator() + "  "
                        + String.join(System.lineSeparator() + "  ", offenders)
                        + System.lineSeparator()
                        + "  处理方式：删除该语句（脚本内对象名已写全 public. 前缀），需要限定 schema 请用 public.xxx。");
    }

    private void appendOffenders(String filename, String sql, List<String> offenders) {
        String[] lines = sql.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String code = stripLineComment(lines[i]);
            Matcher matcher = SESSION_SEARCH_PATH.matcher(code);
            if (matcher.find()) {
                offenders.add(filename + ":" + (i + 1) + " -> " + code.trim());
            }
        }
    }

    /**
     * 去掉行尾注释，避免注释里提到这些语句时误报。
     */
    private String stripLineComment(String line) {
        int idx = line.indexOf("--");
        return idx < 0 ? line : line.substring(0, idx);
    }
}
