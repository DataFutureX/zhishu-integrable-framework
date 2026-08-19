package com.datafuturex.assistant.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datafuturex.assistant.chat.domain.ChatSession;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
