package cn.datafuturex.zhishu.ai.chat.service;

import cn.datafuturex.zhishu.ai.shared.dto.ChatRequestDTO;
import cn.datafuturex.zhishu.ai.shared.dto.ChatStructuredRequestDTO;
import cn.datafuturex.zhishu.ai.shared.vo.ChatResponseVO;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * AI 聊天服务（多轮 Memory + 可选 RAG + 结构化输出）
 */
public interface AiChatService {

    ChatResponseVO chat(ChatRequestDTO request);

    ChatResponseVO cachedChat(ChatRequestDTO request);

    Flux<ServerSentEvent<String>> streamChat(ChatRequestDTO request);

    ChatResponseVO structuredChat(ChatStructuredRequestDTO request);

    /** 简易连通性探测（不走会话记忆） */
    String ping();
}
