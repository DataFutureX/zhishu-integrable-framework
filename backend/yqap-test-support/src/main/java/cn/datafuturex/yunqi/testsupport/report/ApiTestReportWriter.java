package cn.datafuturex.yunqi.testsupport.report;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生成中文 HTML API 测试报告
 */
public final class ApiTestReportWriter {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private ApiTestReportWriter() {
    }

    public static Path write(Path outputDir, List<ApiTestCaseRecord> records) throws IOException {
        return write(outputDir, records, null);
    }

    /**
     * @param docsDir 可选：仓库根 docs 目录，同时落盘一份便于查阅
     */
    public static Path write(Path outputDir, List<ApiTestCaseRecord> records, Path docsDir) throws IOException {
        Instant generatedAt = Instant.now();
        String timeText = TIME_FMT.format(generatedAt);
        Instant suiteStart = records.stream()
                .map(ApiTestCaseRecord::getStartedAt)
                .filter(t -> t != null)
                .min(Instant::compareTo)
                .orElse(generatedAt);
        Instant suiteEnd = records.stream()
                .map(r -> r.getFinishedAt() != null ? r.getFinishedAt() : r.getStartedAt())
                .filter(t -> t != null)
                .max(Instant::compareTo)
                .orElse(generatedAt);
        long durationSec = Math.max(0, Duration.between(suiteStart, suiteEnd).getSeconds());

        String title = "API 接口测试报告（测试时间：" + timeText + "）";

        Files.createDirectories(outputDir);
        Path index = outputDir.resolve("index.html");
        long passed = records.stream().filter(ApiTestCaseRecord::isPassed).count();
        long failed = records.size() - passed;

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">");
        html.append("<title>").append(escape(title)).append("</title>");
        html.append("<style>");
        html.append("body{font-family:Segoe UI,Microsoft YaHei,sans-serif;margin:24px;background:#f6f8fa;color:#24292f;}");
        html.append("h1{margin:0 0 8px;} .meta{color:#57606a;margin-bottom:20px;}");
        html.append("h2{margin:0 0 12px;font-size:18px;}");
        html.append("table{border-collapse:collapse;width:100%;background:#fff;box-shadow:0 1px 2px rgba(0,0,0,.06);}");
        html.append("th,td{border:1px solid #d0d7de;padding:10px 12px;text-align:left;vertical-align:top;}");
        html.append("th{background:#f6f8fa;} .ok{color:#1a7f37;font-weight:600;} .fail{color:#cf222e;font-weight:600;}");
        html.append("details{margin-top:8px;} pre{background:#f6f8fa;padding:10px;overflow:auto;white-space:pre-wrap;}");
        html.append(".card{background:#fff;padding:16px;margin:16px 0;border:1px solid #d0d7de;border-radius:6px;}");
        html.append(".info-table th{width:180px;white-space:nowrap;}");
        html.append("</style></head><body>");
        html.append("<h1>").append(escape(title)).append("</h1>");
        html.append("<div class=\"meta\">测试时间：").append(escape(timeText));
        html.append("　|　开始：").append(escape(TIME_FMT.format(suiteStart)));
        html.append("　|　结束：").append(escape(TIME_FMT.format(suiteEnd)));
        html.append("　|　耗时：").append(durationSec).append(" 秒");
        html.append("　|　总计：").append(records.size());
        html.append("　|　通过：<span class=\"ok\">").append(passed).append("</span>");
        html.append("　|　失败：<span class=\"fail\">").append(failed).append("</span></div>");

        appendPlatformInfo(html);

        html.append("<table><thead><tr>");
        html.append("<th>#</th><th>用例</th><th>目标接口</th><th>结果</th>");
        html.append("</tr></thead><tbody>");
        int i = 1;
        for (ApiTestCaseRecord r : records) {
            html.append("<tr>");
            html.append("<td>").append(i++).append("</td>");
            html.append("<td><a href=\"#case-").append(i - 1).append("\">")
                    .append(escape(nullToEmpty(r.getDisplayName()))).append("</a><br><small>")
                    .append(escape(nullToEmpty(r.getClassName()))).append("#")
                    .append(escape(nullToEmpty(r.getMethodName()))).append("</small></td>");
            html.append("<td>").append(escape(nullToEmpty(r.getTargetApi()))).append("</td>");
            html.append("<td class=\"").append(r.isPassed() ? "ok" : "fail").append("\">")
                    .append(r.isPassed() ? "通过" : "失败").append("</td>");
            html.append("</tr>");
        }
        html.append("</tbody></table>");

        i = 1;
        for (ApiTestCaseRecord r : records) {
            html.append("<div class=\"card\" id=\"case-").append(i).append("\">");
            html.append("<h2>").append(i++).append(". ").append(escape(nullToEmpty(r.getDisplayName())));
            html.append(" <span class=\"").append(r.isPassed() ? "ok" : "fail").append("\">")
                    .append(r.isPassed() ? "通过" : "失败").append("</span></h2>");
            html.append("<p><b>目标接口：</b>").append(escape(nullToEmpty(r.getTargetApi()))).append("</p>");
            html.append("<p><b>输入：</b></p>").append(mapToPre(r.getInput()));
            html.append("<p><b>输出：</b></p>").append(mapToPre(r.getOutput()));
            html.append("<p><b>测试过程：</b></p><ol>");
            for (String step : r.getSteps()) {
                html.append("<li>").append(escape(step)).append("</li>");
            }
            html.append("</ol>");
            if (!r.isPassed() && r.getErrorMessage() != null) {
                html.append("<p><b>失败信息：</b></p><pre>").append(escape(r.getErrorMessage())).append("</pre>");
            }
            html.append("</div>");
        }

        html.append("</body></html>");
        writeUtf8Bom(index, html.toString());

        if (docsDir != null) {
            Files.createDirectories(docsDir);
            // 仅保留最新一份 index.html，清理历史时间戳归档
            try (var stream = Files.list(docsDir)) {
                stream.filter(p -> {
                    String name = p.getFileName().toString();
                    return name.startsWith("api-test-report-") && name.endsWith(".html");
                }).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        // best-effort cleanup
                    }
                });
            }
            Path docsIndex = docsDir.resolve("index.html");
            Files.copy(index, docsIndex, StandardCopyOption.REPLACE_EXISTING);
        }
        return index;
    }

    private static void appendPlatformInfo(StringBuilder html) {
        Map<String, String> info = collectPlatformInfo();
        html.append("<div class=\"card\" id=\"platform-info\">");
        html.append("<h2>平台基本信息</h2>");
        html.append("<table class=\"info-table\"><tbody>");
        info.forEach((k, v) -> html.append("<tr><th>").append(escape(k)).append("</th><td>")
                .append(escape(v)).append("</td></tr>"));
        html.append("</tbody></table></div>");
    }

    static Map<String, String> collectPlatformInfo() {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("平台名称", System.getProperty("yqap.name", "云起应用平台"));
        info.put("英文名称", System.getProperty("yqap.englishName", "YunQi Application Platform (YQAP)"));
        info.put("平台版本", System.getProperty("yqap.version", "1.0.0"));
        info.put("简介", "面向企业数字化应用建设的模块化开发基础平台（MIT）");
        info.put("Java 版本", System.getProperty("java.version", "unknown")
                + "（" + System.getProperty("java.vendor", "unknown") + "）");
        info.put("Spring Boot", resolveSpringBootVersion());
        info.put("操作系统", System.getProperty("os.name", "unknown")
                + " " + System.getProperty("os.version", "")
                + " / " + System.getProperty("os.arch", ""));
        info.put("用户时区", ZoneId.systemDefault().getId());
        info.put("文件编码", System.getProperty("file.encoding", Charset.defaultCharset().name()));
        info.put("控制台编码", System.getProperty("CONSOLE_LOG_CHARSET",
                System.getProperty("stdout.encoding", Charset.defaultCharset().name())));
        info.put("工作目录", System.getProperty("user.dir", ""));
        info.put("报告生成时间", TIME_FMT.format(Instant.now()));
        return info;
    }

    private static String resolveSpringBootVersion() {
        String fromProp = System.getProperty("spring-boot.version");
        if (fromProp != null && !fromProp.isBlank()) {
            return fromProp;
        }
        try {
            Class<?> clazz = Class.forName("org.springframework.boot.SpringBootVersion");
            Object version = clazz.getMethod("getVersion").invoke(null);
            return version != null ? version.toString() : "unknown";
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private static void writeUtf8Bom(Path file, String html) throws IOException {
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        byte[] content = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, content, 0, bom.length);
        System.arraycopy(body, 0, content, bom.length, body.length);
        Files.write(file, content);
    }

    private static String mapToPre(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return "<pre>（无）</pre>";
        }
        StringBuilder sb = new StringBuilder("<pre>");
        map.forEach((k, v) -> sb.append(escape(k)).append(": ").append(escape(nullToEmpty(v))).append('\n'));
        sb.append("</pre>");
        return sb.toString();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
