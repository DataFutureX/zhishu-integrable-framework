package cn.datafuturex.zhishu.ai.biztools.stub;

import cn.datafuturex.zhishu.ai.biztools.api.TerminalOverviewPort;
import cn.datafuturex.zhishu.ai.shared.vo.structured.StationCompareResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnMissingBean(TerminalOverviewPort.class)
public class EmptyTerminalOverviewPort implements TerminalOverviewPort {

    @Override
    public Overview build(int detailLimit) {
        String hint = "监测 Tool 未接入（请在 MCP Hub 启用万象 MCP 上游）";
        StationCompareResult empty = new StationCompareResult(null, hint, List.of());
        return new Overview(0, 0, 0, List.of(), hint, hint, empty);
    }
}
