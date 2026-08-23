package cn.datafuturex.zhishu.ai.briefing.scheduler;

import cn.datafuturex.zhishu.ai.briefing.domain.entity.AiBriefingScheduleEntity;
import cn.datafuturex.zhishu.ai.briefing.service.BriefingJobService;
import cn.datafuturex.zhishu.ai.briefing.service.BriefingScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BriefingScheduler {

    private final BriefingScheduleService scheduleService;
    private final BriefingJobService jobService;

    @Scheduled(fixedDelay = 60_000)
    public void pollDueSchedules() {
        LocalDateTime now = LocalDateTime.now();
        List<AiBriefingScheduleEntity> due = scheduleService.findDue(now);
        if (due.isEmpty()) {
            return;
        }
        for (AiBriefingScheduleEntity schedule : due) {
            try {
                log.info("执行到期简报调度 id={}, name={}, nextRunAt={}",
                        schedule.getId(), schedule.getName(), schedule.getNextRunAt());
                jobService.runSchedule(schedule, "SCHEDULE");
            } catch (Exception e) {
                log.error("简报调度执行异常 id={}: {}", schedule.getId(), e.getMessage(), e);
            } finally {
                try {
                    scheduleService.markRan(schedule, now);
                } catch (Exception e) {
                    log.error("推进简报 next_run_at 失败 id={}: {}", schedule.getId(), e.getMessage(), e);
                }
            }
        }
    }
}
