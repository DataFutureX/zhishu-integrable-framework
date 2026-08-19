package com.datafuturex.assistant.chat.service;

import com.datafuturex.assistant.chat.vo.QaHistoryVO;
import com.datafuturex.assistant.shared.port.QaHistoryPort;

import java.util.List;

/**
 * 问答历史服务
 */
public interface QaHistoryService extends QaHistoryPort {

    List<QaHistoryVO> listCurrentUser(String scene, Integer limit);

    /**
     * 门户演示：按时间倒序取最近 N 条（返回仍按时间升序，便于按对话顺序播放）
     */
    List<QaHistoryVO> listLatestForPortal(String scene, Integer limit);

    void clearCurrentUser(String scene);
}
