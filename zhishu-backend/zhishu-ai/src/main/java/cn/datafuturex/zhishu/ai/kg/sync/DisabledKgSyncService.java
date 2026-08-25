package cn.datafuturex.zhishu.ai.kg.sync;

import cn.datafuturex.zhishu.ai.kg.api.KnowledgeGraphSyncPort;
import cn.datafuturex.zhishu.ai.kg.api.dto.KgSyncResult;
import cn.datafuturex.zhishu.ai.kg.api.dto.KgSyncStatusVO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnMissingBean(name = "kgSyncService")
public class DisabledKgSyncService implements KnowledgeGraphSyncPort {

    @Override
    public KgSyncResult sync(boolean full) {
        return new KgSyncResult(false, full, "知枢不再拉取万象表，请由万象 POST /open/v1/kg/upsert",
                LocalDateTime.now(), LocalDateTime.now(), Map.of(), 0);
    }

    @Override
    public KgSyncStatusVO status() {
        return new KgSyncStatusVO(false, false, null, "图谱数据由万象推送，知枢不扫描业务表", List.of());
    }
}
