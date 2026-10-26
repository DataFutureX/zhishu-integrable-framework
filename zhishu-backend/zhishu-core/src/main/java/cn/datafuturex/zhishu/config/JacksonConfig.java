package cn.datafuturex.zhishu.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * 大整数（雪花 ID）序列化约定。
 *
 * <p>Javascript 的 Number 只能精确表示到 2^53-1，19 位雪花 ID 直接以 number 下发会在
 * 浏览器 JSON.parse 阶段静默丢精度（前端历史上用「给 JSON 原文正则加引号」的方式兜底，
 * 既脆弱又会误伤文本内容）。这里统一在服务端把超出安全范围的 Long 序列化为字符串，
 * 小于该范围的数值（计数、耗时、分页 total）保持 number，不破坏既有数值契约。</p>
 */
@Configuration
public class JacksonConfig {

    /** JS Number.MAX_SAFE_INTEGER */
    private static final long JS_MAX_SAFE_INTEGER = 9007199254740991L;

    @Bean
    public Module safeLongModule() {
        SimpleModule module = new SimpleModule("safe-long-module");
        SafeLongSerializer serializer = new SafeLongSerializer();
        module.addSerializer(Long.class, serializer);
        module.addSerializer(Long.TYPE, serializer);
        return module;
    }

    /**
     * 安全范围内的 Long 仍输出数字；超出范围输出字符串。
     */
    public static class SafeLongSerializer extends JsonSerializer<Long> {

        @Override
        public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value == null) {
                gen.writeNull();
                return;
            }
            if (value > JS_MAX_SAFE_INTEGER || value < -JS_MAX_SAFE_INTEGER) {
                gen.writeString(value.toString());
                return;
            }
            gen.writeNumber(value);
        }
    }
}
