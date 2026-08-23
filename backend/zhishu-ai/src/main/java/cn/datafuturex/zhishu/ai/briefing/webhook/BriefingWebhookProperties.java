package cn.datafuturex.zhishu.ai.briefing.webhook;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "zhishu.briefing.webhook")
public class BriefingWebhookProperties {

    private boolean enabled = false;

    /** 可选：知枢内部简报外推地址。外部自建简报请走 /open/v1/chat，不要依赖此 Webhook */
    private String url = "http://127.0.0.1:8180/api/v1/internal/briefings/notify";

    private String apiKey = "dev-briefing-webhook-key";

    /** 失败重试次数（含首次），默认 3 */
    private int maxAttempts = 3;

    /** 重试间隔基数（毫秒），实际等待 = base * 尝试序号 */
    private long retryBackoffMs = 1000L;
}
