package cn.datafuturex.zhishu.ai.kg.api;

import cn.datafuturex.zhishu.ai.kg.api.dto.KgSyncResult;
import cn.datafuturex.zhishu.ai.kg.api.dto.KgSyncStatusVO;

/**
 * 知识图谱同步端口。
 */
public interface KnowledgeGraphSyncPort {

    KgSyncResult sync(boolean full);

    KgSyncStatusVO status();
}
