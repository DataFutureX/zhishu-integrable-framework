package cn.datafuturex.zhishu.testsupport.report;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiTestReportWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void write_containsPlatformBasicInfo() throws Exception {
        Path index = ApiTestReportWriter.write(tempDir, List.of());
        String html = Files.readString(index, StandardCharsets.UTF_8);
        assertTrue(html.contains("平台基本信息"));
        assertTrue(html.contains("知枢可集成框架"));
        assertTrue(html.contains("ZhiShu Integrable Framework"));
        assertTrue(html.contains("Java 版本"));
        assertTrue(html.contains("Spring Boot"));
        assertTrue(html.contains("操作系统"));
    }

    @Test
    void write_docsDir_keepsOnlyLatestIndex() throws Exception {
        Path docsDir = tempDir.resolve("docs-report");
        Files.createDirectories(docsDir);
        Files.writeString(docsDir.resolve("api-test-report-20260101-120000.html"), "old");

        ApiTestReportWriter.write(tempDir.resolve("out"), List.of(), docsDir);

        assertTrue(Files.exists(docsDir.resolve("index.html")));
        try (var stream = Files.list(docsDir)) {
            long dated = stream.filter(p -> p.getFileName().toString().startsWith("api-test-report-")).count();
            org.junit.jupiter.api.Assertions.assertEquals(0, dated);
        }
    }
}
