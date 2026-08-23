package cn.datafuturex.zhishu.ai.kg.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record KgTopologySummary(
        boolean found,
        String message,
        @JsonSerialize(using = ToStringSerializer.class)
        long projectId,
        String projectName,
        long terminalCount,
        long openAlertCount,
        long planCount,
        long taskCount,
        long openIssueCount
) {
}
