package com.datafuturex.assistant.briefing.service;

import com.datafuturex.assistant.briefing.domain.entity.AiBriefingScheduleEntity;

public interface BriefingJobService {

    /**
     * 按调度为全部启用用户生成简报。
     *
     * @param triggerType SCHEDULE | RUN_NOW | EVENT
     */
    int runSchedule(AiBriefingScheduleEntity schedule, String triggerType);

    /**
     * @param triggerRef 事件引用，如 OFFLINE_SPIKE:12；为空时默认使用 scheduleId
     */
    int runSchedule(AiBriefingScheduleEntity schedule, String triggerType, String triggerRef);

    /**
     * 仅为指定用户名生成简报（开放 API / 工作台代调）。
     *
     * @param promptOverride 非空时覆盖调度模板
     */
    int runForUsername(AiBriefingScheduleEntity schedule, String triggerType, String username, String promptOverride);
}
