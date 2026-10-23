package cn.datafuturex.zhishu.ai;

import org.springframework.modulith.Modulithic;

/**
 * AI 内核 Spring Modulith 元数据入口（应用模块根包：cn.datafuturex.zhishu.ai）。
 * <p>
 * 与 {@code zhishu-biz} 的 {@code BizModules} 同一约定：本类不参与组件扫描，
 * 仅作为模块检测的基准包标记，供结构校验测试使用。
 */
@Modulithic(systemName = "YunQi Application Platform · AI")
public final class AiModules {

    private AiModules() {
    }
}
