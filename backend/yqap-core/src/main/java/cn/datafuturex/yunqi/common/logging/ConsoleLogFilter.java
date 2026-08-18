package cn.datafuturex.yunqi.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

import java.util.List;

/**
 * 控制台日志过滤器：仅输出对本地调试有帮助的日志。
 * <p>
 * WARN/ERROR 始终输出；INFO 仅对白名单包输出（启动、基础设施、关键业务事件）。
 * DEBUG/TRACE 不输出到控制台，完整明细写入日志文件。
 */
public class ConsoleLogFilter extends Filter<ILoggingEvent> {

    private static final List<String> INFO_LOGGER_PREFIXES = List.of(
            "org.springframework.boot",
            "cn.datafuturex.yunqi.YqapApplication",
            "cn.datafuturex.yunqi.config",
            "cn.datafuturex.yunqi.modules.controller.AuthController",
            "cn.datafuturex.yunqi.common.GlobalExceptionHandler"
    );

    @Override
    public FilterReply decide(ILoggingEvent event) {
        Level level = event.getLevel();
        if (level.isGreaterOrEqual(Level.WARN)) {
            return FilterReply.ACCEPT;
        }
        if (level.isGreaterOrEqual(Level.INFO)) {
            String loggerName = event.getLoggerName();
            for (String prefix : INFO_LOGGER_PREFIXES) {
                if (loggerName.startsWith(prefix)) {
                    return FilterReply.ACCEPT;
                }
            }
        }
        return FilterReply.DENY;
    }
}
