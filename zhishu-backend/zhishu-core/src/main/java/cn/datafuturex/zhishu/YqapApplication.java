package cn.datafuturex.zhishu;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 云起应用平台启动类
 */
@SpringBootApplication(scanBasePackages = "cn.datafuturex.zhishu")
@MapperScan(basePackages = "cn.datafuturex.zhishu", annotationClass = Mapper.class)
@EnableCaching
public class YqapApplication {

    static {
        configureConsoleCharset();
    }

    private static final Logger log = LoggerFactory.getLogger(YqapApplication.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(YqapApplication.class, args);

        String devToolsStatus = "未检测到DevTools";
        try {
            Class.forName("org.springframework.boot.devtools.restart.RestartApplicationListener");
            devToolsStatus = "已启用 - 支持热部署";
        } catch (ClassNotFoundException e) {
            devToolsStatus = "未启用";
        }

        String port = context.getEnvironment().getProperty("server.port", "8080");
        log.info("===========================================");
        log.info("云起应用平台启动成功！");
        log.info("DevTools状态: {}", devToolsStatus);
        log.info("HTTP端口: {}", port);
        log.info("Swagger文档: http://localhost:{}/swagger-ui.html", port);
        log.info("控制台字符集: {}", System.getProperty("CONSOLE_LOG_CHARSET"));
        log.info("===========================================");
    }

    /**
     * 控制台统一 UTF-8（Cursor / Windows Terminal / start-dev.bat 均为 UTF-8）。
     * 可通过 -DCONSOLE_LOG_CHARSET=xxx 覆盖。
     */
    private static void configureConsoleCharset() {
        setDefaultProperty("CONSOLE_LOG_CHARSET", "UTF-8");
        setDefaultProperty("stdout.encoding", "UTF-8");
        setDefaultProperty("stderr.encoding", "UTF-8");
    }

    private static void setDefaultProperty(String key, String value) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }
}
