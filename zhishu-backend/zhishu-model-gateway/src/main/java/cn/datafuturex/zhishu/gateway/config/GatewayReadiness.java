package cn.datafuturex.zhishu.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 网关就绪状态：建表/迁移未完成时拒绝服务并给出可操作提示。
 * <p>
 * 默认 ready，只有当 {@link GatewaySchemaInitializer} 确认结构缺失时才置为不可用。
 */
@Slf4j
@Component
public class GatewayReadiness {

    private volatile boolean ready = true;

    private volatile String reason;

    /**
     * 是否已就绪。
     *
     * @return true=可用
     */
    public boolean isReady() {
        return ready;
    }

    /**
     * 未就绪原因。
     *
     * @return 原因；就绪时为 null
     */
    public String reason() {
        return reason;
    }

    /**
     * 标记就绪。
     */
    public void markReady() {
        if (!ready) {
            log.info("模型网关结构校验通过，已恢复服务");
        }
        this.ready = true;
        this.reason = null;
    }

    /**
     * 标记未就绪（不阻断应用启动，只让网关返回可读错误）。
     *
     * @param reason 可操作的原因说明
     */
    public void markNotReady(String reason) {
        log.error("模型网关不可用：{}", reason);
        this.ready = false;
        this.reason = reason;
    }
}
