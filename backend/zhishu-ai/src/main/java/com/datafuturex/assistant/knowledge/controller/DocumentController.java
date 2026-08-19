package com.datafuturex.assistant.knowledge.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.datafuturex.assistant.shared.Result;
import com.datafuturex.assistant.knowledge.dto.DocumentUploadDTO;
import com.datafuturex.assistant.knowledge.vo.DocumentVO;
import com.datafuturex.assistant.knowledge.service.DocumentManagementService;

import java.util.List;
import java.util.Map;

/**
 * 文档管理控制器
 */
@RestController
@RequestMapping("/api/v1/knowledges")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "文档管理", description = "提供文档上传、查询、删除等接口")
public class DocumentController {

    private final DocumentManagementService documentManagementService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传文档", description = "上传文档到指定知识库分类，自动解析、切片并向量化")
    public Result<DocumentVO> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "categoryId", required = false) Long categoryId) {

        log.info("收到文档上传请求: 文件名={}, 标题={}, categoryId={}",
                file.getOriginalFilename(), title, categoryId);

        DocumentUploadDTO uploadDTO = new DocumentUploadDTO(title, categoryId);
        DocumentVO documentVO = documentManagementService.uploadDocument(file, uploadDTO);
        return Result.success(documentVO);
    }

    @GetMapping
    @Operation(summary = "获取文档列表", description = "可按知识库分类过滤")
    public Result<List<DocumentVO>> getAllDocuments(
            @Parameter(description = "知识库分类 ID")
            @RequestParam(value = "categoryId", required = false) Long categoryId) {
        return Result.success(documentManagementService.getAllDocuments(categoryId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取文档详情")
    public Result<DocumentVO> getDocumentById(
            @Parameter(description = "文档ID", required = true) @PathVariable Long id) {
        return Result.success(documentManagementService.getDocumentById(id));
    }

    @PutMapping("/{id}/category")
    @Operation(summary = "调整文档所属知识库")
    public Result<DocumentVO> updateCategory(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body) {
        Long categoryId = body == null ? null : body.get("categoryId");
        return Result.success(documentManagementService.updateDocumentCategory(id, categoryId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除文档")
    public Result<Void> deleteDocument(
            @Parameter(description = "文档ID", required = true) @PathVariable Long id) {
        documentManagementService.deleteDocument(id);
        return Result.success(null);
    }

    @PostMapping("/{id}/reprocess")
    @Operation(summary = "重新处理文档")
    public Result<DocumentVO> reprocessDocument(
            @Parameter(description = "文档ID", required = true) @PathVariable Long id) {
        return Result.success(documentManagementService.reprocessDocument(id));
    }
}
