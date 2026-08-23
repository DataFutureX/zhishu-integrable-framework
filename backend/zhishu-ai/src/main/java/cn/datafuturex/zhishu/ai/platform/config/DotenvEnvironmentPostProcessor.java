package cn.datafuturex.zhishu.ai.platform.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 加载工作目录 .env（或仓库根目录 .env），不覆盖已有环境变量。
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path file = resolveEnvFile();
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        Map<String, Object> values = parse(file);
        if (values.isEmpty()) {
            return;
        }
        environment.getPropertySources().addLast(new MapPropertySource("wanxiangDotenv", values));
    }

    private static Path resolveEnvFile() {
        Path cwd = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        Path direct = cwd.resolve(".env");
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        Path parent = cwd.getParent();
        if (parent != null) {
            Path rootEnv = parent.resolve(".env");
            if (Files.isRegularFile(rootEnv)) {
                return rootEnv;
            }
        }
        FileSystemResource resource = new FileSystemResource(".env");
        return resource.exists() ? resource.getFile().toPath() : null;
    }

    private static Map<String, Object> parse(Path file) {
        Map<String, Object> values = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!StringUtils.hasText(trimmed) || trimmed.startsWith("#")) {
                    continue;
                }
                if (trimmed.startsWith("export ")) {
                    trimmed = trimmed.substring(7).trim();
                }
                int eq = trimmed.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, eq).trim();
                if (!StringUtils.hasText(key) || System.getenv(key) != null) {
                    continue;
                }
                String raw = trimmed.substring(eq + 1).trim();
                if ((raw.startsWith("\"") && raw.endsWith("\"")) || (raw.startsWith("'") && raw.endsWith("'"))) {
                    raw = raw.substring(1, raw.length() - 1);
                }
                values.put(key, raw);
            }
        } catch (Exception ignored) {
            // 启动不因 .env 解析失败中断
        }
        return values;
    }
}
