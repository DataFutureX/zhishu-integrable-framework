package com.datafuturex.assistant.biztools.stub;

import com.datafuturex.assistant.biztools.api.TerminalOverviewPort;
import com.datafuturex.assistant.shared.vo.structured.StationCompareResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EmptyTerminalOverviewPort implements TerminalOverviewPort {

    @Override
    public Overview build(int detailLimit) {
        StationCompareResult empty = new StationCompareResult(null, "监测 Tool 未接入（请配置 wanxiang-mcp upstream）", List.of());
        return new Overview(0, 0, 0, List.of(), empty.summary(), "", empty);
    }
}
