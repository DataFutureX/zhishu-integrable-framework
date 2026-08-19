package com.datafuturex.assistant.kg.controller;

import com.datafuturex.assistant.kg.api.dto.KgSyncResult;
import com.datafuturex.assistant.kg.api.dto.KgUpsertRequest;
import com.datafuturex.assistant.kg.sync.KgPushIngestService;
import com.datafuturex.assistant.shared.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/open/v1/kg")
@RequiredArgsConstructor
@Tag(name = "开放 API · 知识图谱")
public class OpenKgController {

    private final ObjectProvider<KgPushIngestService> ingestService;

    @PostMapping("/upsert")
    @Operation(summary = "接收万象推送的工程/遥测站/告警节点")
    public Result<KgSyncResult> upsert(@RequestBody KgUpsertRequest request) {
        KgPushIngestService ingest = ingestService.getIfAvailable();
        if (ingest == null) {
            return Result.fail("知识图谱未启用");
        }
        KgUpsertRequest body = request == null
                ? new KgUpsertRequest(false, null, null, null)
                : request;
        return Result.success(ingest.ingest(body));
    }
}
