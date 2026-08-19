package com.datafuturex.assistant.biztools.api;

import com.datafuturex.assistant.shared.vo.structured.StationCompareResult;

import java.util.List;

/**
 * 遥测站在线概览（供 chat 短路路径使用）。
 */
public interface TerminalOverviewPort {

    int DEFAULT_DETAIL_LIMIT = 50;

    Overview build(int detailLimit);

    record Overview(
            int total,
            int online,
            int offline,
            List<StationCompareResult.StationCompareItem> items,
            String summary,
            String markdown,
            StationCompareResult structured
    ) {
    }
}
