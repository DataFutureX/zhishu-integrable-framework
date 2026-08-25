package cn.datafuturex.zhishu.ai.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.datafuturex.zhishu.ai.chat.domain.QaHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 问答历史 Mapper（表 qa_history）
 */
@Mapper
public interface QaHistoryMapper extends BaseMapper<QaHistory> {
}
