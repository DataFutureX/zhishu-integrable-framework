package cn.datafuturex.zhishu.ai.chat.service;

import cn.datafuturex.zhishu.ai.chat.dto.ChatSessionCreateDTO;
import cn.datafuturex.zhishu.ai.chat.dto.ChatSessionTitleDTO;
import cn.datafuturex.zhishu.ai.chat.vo.ChatSessionVO;
import cn.datafuturex.zhishu.ai.chat.vo.QaHistoryVO;

import java.util.List;

public interface ChatSessionService {

    List<ChatSessionVO> listCurrentUser(String scene);

    ChatSessionVO create(ChatSessionCreateDTO dto);

    ChatSessionVO rename(String conversationId, ChatSessionTitleDTO dto);

    void delete(String conversationId);

    List<QaHistoryVO> listMessages(String conversationId, Integer limit);

    /**
     * 保留前 keepUserTurns 轮问答，删除其后 qa_history 并重建 Chat Memory。
     * 用于「编辑提问再发送」。
     */
    void truncateAfterTurns(String conversationId, int keepUserTurns);

    /** 保存问答后同步会话元数据（首次自动生成标题） */
    void touchOnMessage(String userId, String scene, String conversationId, String question, Long agentId);

    /** 清空当前用户某场景下全部会话元数据（不含消息，由调用方清理 qa_history） */
    void clearCurrentUser(String scene);
}
