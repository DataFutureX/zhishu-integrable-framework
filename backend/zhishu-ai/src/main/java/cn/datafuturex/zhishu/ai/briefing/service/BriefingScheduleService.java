package cn.datafuturex.zhishu.ai.briefing.service;

import cn.datafuturex.zhishu.ai.briefing.domain.dto.BriefingScheduleUpsertDTO;
import cn.datafuturex.zhishu.ai.briefing.domain.entity.AiBriefingScheduleEntity;
import cn.datafuturex.zhishu.ai.briefing.domain.vo.BriefingScheduleVO;

import java.time.LocalDateTime;
import java.util.List;

public interface BriefingScheduleService {

    List<BriefingScheduleVO> listEnabled();

    List<BriefingScheduleVO> listAll();

    BriefingScheduleVO get(Long id);

    BriefingScheduleVO create(BriefingScheduleUpsertDTO dto);

    BriefingScheduleVO update(Long id, BriefingScheduleUpsertDTO dto);

    AiBriefingScheduleEntity requireEntity(Long id);

    List<AiBriefingScheduleEntity> findDue(LocalDateTime now);

    void markRan(AiBriefingScheduleEntity schedule, LocalDateTime ranAt);

    LocalDateTime computeNextRunAt(AiBriefingScheduleEntity schedule, LocalDateTime from);

    String compileCron(AiBriefingScheduleEntity schedule);
}
