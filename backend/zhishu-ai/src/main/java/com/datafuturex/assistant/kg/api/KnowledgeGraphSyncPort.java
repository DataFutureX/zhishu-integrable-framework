package com.datafuturex.assistant.kg.api;

import com.datafuturex.assistant.kg.api.dto.KgSyncResult;
import com.datafuturex.assistant.kg.api.dto.KgSyncStatusVO;

/**
 * 知识图谱同步端口。
 */
public interface KnowledgeGraphSyncPort {

    KgSyncResult sync(boolean full);

    KgSyncStatusVO status();
}
