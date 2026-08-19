package com.datafuturex.assistant.knowledge.controller;

import com.datafuturex.assistant.shared.Result;
import com.datafuturex.assistant.knowledge.dto.KnowledgesCategoryCreateDTO;
import com.datafuturex.assistant.knowledge.dto.KnowledgesCategoryUpdateDTO;
import com.datafuturex.assistant.knowledge.vo.KnowledgesCategoryVO;
import com.datafuturex.assistant.knowledge.service.KnowledgesCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/knowledges-categories")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "知识库分类", description = "文档分类即知识库：CRUD 与列表")
public class KnowledgesCategoryController {

    private final KnowledgesCategoryService knowledgesCategoryService;

    @GetMapping
    @Operation(summary = "知识库分类列表")
    public Result<List<KnowledgesCategoryVO>> list(
            @Parameter(description = "是否包含停用分类")
            @RequestParam(value = "includeDisabled", defaultValue = "false") boolean includeDisabled) {
        return Result.success(knowledgesCategoryService.listAll(includeDisabled));
    }

    @GetMapping("/{id}")
    @Operation(summary = "知识库分类详情")
    public Result<KnowledgesCategoryVO> detail(@PathVariable Long id) {
        return Result.success(knowledgesCategoryService.getById(id));
    }

    @PostMapping
    @Operation(summary = "创建知识库分类")
    public Result<KnowledgesCategoryVO> create(@Valid @RequestBody KnowledgesCategoryCreateDTO dto) {
        return Result.success(knowledgesCategoryService.create(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新知识库分类")
    public Result<KnowledgesCategoryVO> update(@PathVariable Long id,
                                               @Valid @RequestBody KnowledgesCategoryUpdateDTO dto) {
        return Result.success(knowledgesCategoryService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除知识库分类")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgesCategoryService.delete(id);
        return Result.success(null);
    }
}
