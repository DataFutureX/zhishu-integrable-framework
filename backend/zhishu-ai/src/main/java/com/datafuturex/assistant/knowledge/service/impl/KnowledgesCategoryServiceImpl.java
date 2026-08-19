package com.datafuturex.assistant.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.datafuturex.assistant.knowledge.domain.Document;
import com.datafuturex.assistant.knowledge.domain.KnowledgesCategory;
import com.datafuturex.assistant.knowledge.dto.KnowledgesCategoryCreateDTO;
import com.datafuturex.assistant.knowledge.dto.KnowledgesCategoryUpdateDTO;
import com.datafuturex.assistant.knowledge.vo.KnowledgesCategoryVO;
import com.datafuturex.assistant.shared.exception.BusinessException;
import com.datafuturex.assistant.knowledge.mapper.DocumentMapper;
import com.datafuturex.assistant.knowledge.mapper.KnowledgesCategoryMapper;
import com.datafuturex.assistant.knowledge.service.KnowledgesCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgesCategoryServiceImpl implements KnowledgesCategoryService {

    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_DISABLED = "DISABLED";

    private final KnowledgesCategoryMapper categoryMapper;
    private final DocumentMapper documentMapper;

    @Override
    public List<KnowledgesCategoryVO> listAll(boolean includeDisabled) {
        LambdaQueryWrapper<KnowledgesCategory> qw = new LambdaQueryWrapper<KnowledgesCategory>()
                .orderByAsc(KnowledgesCategory::getSortOrder)
                .orderByAsc(KnowledgesCategory::getId);
        if (!includeDisabled) {
            qw.eq(KnowledgesCategory::getStatus, STATUS_ENABLED);
        }
        List<KnowledgesCategory> list = categoryMapper.selectList(qw);
        Map<Long, Long> countMap = countDocumentsByCategory();
        return list.stream()
                .map(c -> toVO(c, countMap.getOrDefault(c.getId(), 0L)))
                .collect(Collectors.toList());
    }

    @Override
    public KnowledgesCategoryVO getById(Long id) {
        KnowledgesCategory category = requireCategory(id);
        Long count = documentMapper.selectCount(new LambdaQueryWrapper<Document>()
                .eq(Document::getCategoryId, id));
        return toVO(category, count);
    }

    @Override
    @Transactional
    public KnowledgesCategoryVO create(KnowledgesCategoryCreateDTO dto) {
        String code = dto.code().trim().toLowerCase(Locale.ROOT);
        Long exists = categoryMapper.selectCount(new LambdaQueryWrapper<KnowledgesCategory>()
                .eq(KnowledgesCategory::getCode, code));
        if (exists != null && exists > 0) {
            throw new BusinessException("分类编码已存在: " + code);
        }
        KnowledgesCategory entity = new KnowledgesCategory();
        entity.setCode(code);
        entity.setName(dto.name().trim());
        entity.setDescription(trimToNull(dto.description()));
        entity.setSortOrder(dto.sortOrder() != null ? dto.sortOrder() : 100);
        entity.setStatus(STATUS_ENABLED);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        categoryMapper.insert(entity);
        log.info("创建知识库分类: id={}, code={}", entity.getId(), entity.getCode());
        return toVO(entity, 0L);
    }

    @Override
    @Transactional
    public KnowledgesCategoryVO update(Long id, KnowledgesCategoryUpdateDTO dto) {
        KnowledgesCategory entity = requireCategory(id);
        if (StringUtils.hasText(dto.name())) {
            entity.setName(dto.name().trim());
        }
        if (dto.description() != null) {
            entity.setDescription(trimToNull(dto.description()));
        }
        if (dto.sortOrder() != null) {
            entity.setSortOrder(dto.sortOrder());
        }
        if (StringUtils.hasText(dto.status())) {
            String status = dto.status().trim().toUpperCase(Locale.ROOT);
            if (!STATUS_ENABLED.equals(status) && !STATUS_DISABLED.equals(status)) {
                throw new BusinessException("状态仅支持 ENABLED / DISABLED");
            }
            entity.setStatus(status);
        }
        entity.setUpdateTime(LocalDateTime.now());
        categoryMapper.updateById(entity);
        Long count = documentMapper.selectCount(new LambdaQueryWrapper<Document>()
                .eq(Document::getCategoryId, id));
        return toVO(entity, count);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        requireCategory(id);
        Long count = documentMapper.selectCount(new LambdaQueryWrapper<Document>()
                .eq(Document::getCategoryId, id));
        if (count != null && count > 0) {
            throw new BusinessException("该知识库下仍有 " + count + " 个文档，请先迁移或删除文档后再删除分类");
        }
        categoryMapper.deleteById(id);
        log.info("删除知识库分类: id={}", id);
    }

    @Override
    public Long requireEnabledCategoryId(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        KnowledgesCategory category = requireCategory(categoryId);
        if (!STATUS_ENABLED.equalsIgnoreCase(category.getStatus())) {
            throw new BusinessException("知识库分类已停用: " + category.getName());
        }
        return category.getId();
    }

    private KnowledgesCategory requireCategory(Long id) {
        KnowledgesCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException("知识库分类不存在: " + id);
        }
        return category;
    }

    private Map<Long, Long> countDocumentsByCategory() {
        List<Document> docs = documentMapper.selectList(new LambdaQueryWrapper<Document>()
                .select(Document::getId, Document::getCategoryId)
                .isNotNull(Document::getCategoryId));
        return docs.stream()
                .filter(d -> d.getCategoryId() != null)
                .collect(Collectors.groupingBy(Document::getCategoryId, Collectors.counting()));
    }

    private KnowledgesCategoryVO toVO(KnowledgesCategory c, Long documentCount) {
        return new KnowledgesCategoryVO(
                c.getId() == null ? null : String.valueOf(c.getId()),
                c.getCode(),
                c.getName(),
                c.getDescription(),
                c.getSortOrder(),
                c.getStatus(),
                documentCount,
                c.getCreateTime(),
                c.getUpdateTime());
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
