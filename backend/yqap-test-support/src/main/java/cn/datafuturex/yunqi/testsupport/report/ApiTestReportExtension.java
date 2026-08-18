package cn.datafuturex.yunqi.testsupport.report;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * JUnit 5 扩展：收集用例并写出中文 HTML 报告
 */
public class ApiTestReportExtension implements BeforeEachCallback, AfterEachCallback, AfterAllCallback {

    public static final String REPORT_DIR_PROPERTY = "api.test.report.dir";
    public static final String REPORT_DOCS_DIR_PROPERTY = "api.test.report.docs.dir";

    private static final ExtensionContext.Namespace NS =
            ExtensionContext.Namespace.create(ApiTestReportExtension.class);

    private static volatile boolean shutdownHookRegistered;

    @Override
    public void beforeEach(ExtensionContext context) {
        registerShutdownHookOnce();
        ApiTestCaseRecord record = new ApiTestCaseRecord();
        record.setClassName(context.getRequiredTestClass().getSimpleName());
        record.setMethodName(context.getRequiredTestMethod().getName());
        record.setDisplayName(context.getDisplayName());
        record.setStartedAt(Instant.now());
        ApiTestContext.set(record);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        ApiTestCaseRecord record = ApiTestContext.get();
        if (record == null) {
            return;
        }
        record.setFinishedAt(Instant.now());
        boolean passed = context.getExecutionException().isEmpty();
        record.setPassed(passed);
        context.getExecutionException().ifPresent(ex ->
                record.setErrorMessage(ex.getClass().getSimpleName() + ": " + ex.getMessage()));
        ApiTestReportStore.add(record);
        ApiTestContext.clear();

        ExtensionContext root = context.getRoot();
        root.getStore(NS).put("dirty", Boolean.TRUE);
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        writeReport();
    }

    public static synchronized void writeReport() throws Exception {
        String dir = System.getProperty(REPORT_DIR_PROPERTY);
        if (dir == null || dir.isBlank()) {
            dir = Path.of(System.getProperty("user.dir"), "target", "api-test-report").toString();
        }
        Path outputDir = Path.of(dir).toAbsolutePath().normalize();

        Path docsDir = resolveDocsDir();
        Path path = ApiTestReportWriter.write(outputDir, ApiTestReportStore.snapshot(), docsDir);

        StringBuilder pointer = new StringBuilder();
        pointer.append("target=").append(path).append(System.lineSeparator());
        if (docsDir != null) {
            Path docsIndex = docsDir.resolve("index.html").toAbsolutePath().normalize();
            pointer.append("docs=").append(docsIndex).append(System.lineSeparator());
            System.out.println("[ApiTestReport] Docs copy written to: " + docsIndex);
            System.out.println("[ApiTestReport] Open docs report: file:///" + docsIndex.toString().replace('\\', '/'));
        }
        Files.writeString(outputDir.resolve("REPORT_PATH.txt"), pointer.toString(), StandardCharsets.UTF_8);
        System.out.println("[ApiTestReport] HTML report written to: " + path);
        System.out.println("[ApiTestReport] Open in browser: file:///" + path.toString().replace('\\', '/'));
    }

    private static Path resolveDocsDir() {
        String configured = System.getProperty(REPORT_DOCS_DIR_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        // 默认：从 user.dir（一般为 yqap-core）向上找仓库根并写入 docs/api-test-report
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (int i = 0; i < 6 && dir != null; i++) {
            if (Files.isDirectory(dir.resolve("backend")) && Files.isRegularFile(dir.resolve("README.md"))) {
                return dir.resolve("docs").resolve("api-test-report");
            }
            if (Files.isDirectory(dir.resolve("yqap-core")) && Files.isRegularFile(dir.resolve("pom.xml"))) {
                // 当前在 backend/
                Path repoRoot = dir.getParent();
                if (repoRoot != null) {
                    return repoRoot.resolve("docs").resolve("api-test-report");
                }
            }
            dir = dir.getParent();
        }
        return null;
    }

    private static void registerShutdownHookOnce() {
        if (shutdownHookRegistered) {
            return;
        }
        synchronized (ApiTestReportExtension.class) {
            if (shutdownHookRegistered) {
                return;
            }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    writeReport();
                } catch (Exception ignored) {
                    // ignore
                }
            }, "api-test-report-shutdown"));
            shutdownHookRegistered = true;
        }
    }
}
