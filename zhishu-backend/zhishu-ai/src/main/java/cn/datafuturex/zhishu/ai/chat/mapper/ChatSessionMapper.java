package cn.datafuturex.zhishu.ai.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.datafuturex.zhishu.ai.chat.domain.ChatSession;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
