package com.datafuturex.assistant.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.datafuturex.assistant.knowledge.domain.Document;
import com.datafuturex.assistant.knowledge.domain.KnowledgesCategory;
import com.datafuturex.assistant.knowledge.dto.DocumentUploadDTO;
import com.datafuturex.assistant.knowledge.vo.DocumentVO;
import com.datafuturex.assistant.shared.exception.BusinessException;
import com.datafuturex.assistant.knowledge.mapper.KnowledgesCategoryMapper;
import com.datafuturex.assistant.knowledge.mapper.DocumentMapper;
import com.datafuturex.assistant.knowledge.service.KnowledgesCategoryService;
import com.datafuturex.assistant.knowledge.service.DocumentChunkService;
import com.datafuturex.assistant.knowledge.service.DocumentManagementService;
import com.datafuturex.assistant.knowledge.service.DocumentParseService;
import com.datafuturex.assistant.knowledge.service.EmbeddingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 文档管理服务实现类
 * 
 * @author Qoder
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentManagementServiceImpl implements DocumentManagementService {

    private final DocumentMapper documentMapper;
    private final KnowledgesCategoryMapper knowledgesCategoryMapper;
    private final KnowledgesCategoryService knowledgesCategoryService;
    private final DocumentParseService parseService;
    private final DocumentChunkService chunkService;
    private final EmbeddingService embeddingService;

    @Value("${document.upload.dir:./uploads}")
    private String uploadDir;

    @Value("${spring.ai.debug.skip-embedding:false}")
    private boolean skipEmbedding;

    /**
     * 上传并处理文档
     *
     * @param file      上传的文件
     * @param uploadDTO 上传请求信息
     * @return 文档信息
     */
    @Override
    @Transactional
    public DocumentVO uploadDocument(MultipartFile file, DocumentUploadDTO uploadDTO) {
        // 记录初始内存状态
        logMemoryUsage("上传开始");

        // 验证文件
        validateFile(file);

        try {
            // 保存文件到本地
            String fileName = saveFile(file);
            String fileType = getFileExtension(file.getOriginalFilename());

            logMemoryUsage("文件保存后");

            // 解析文档内容
            log.info("开始解析文档: {}", fileName);
            String content = parseService.parseDocument(file);

            logMemoryUsage("文档解析后");

            // 检查文本长度，防止异常大的文本
            if (content.length() > 10_000_000) { // 10MB
                log.warn("⚠️ 文档解析后文本过长: {} 字符，可能存在异常", content.length());
            } else {
                log.info("文档解析成功，文本长度: {} 字符", content.length());
            }

            if (content.isEmpty()) {
                throw new BusinessException("文档内容为空");
            }

            Long categoryId = resolveCategoryId(uploadDTO.categoryId());

            // 创建文档记录
            Document document = new Document();
            document.setFileName(uploadDTO.title() + "." + fileType);
            document.setFileType(fileType);
            document.setFilePath(fileName);
            document.setFileSize(file.getSize());
            document.setContent(content);
            document.setCategoryId(categoryId);
            document.setProcessed(false);
            document.setUploadTime(LocalDateTime.now());
            document.setUpdateTime(LocalDateTime.now());

            documentMapper.insert(document);
            log.info("文档记录已保存，ID: {}", document.getId());

            logMemoryUsage("数据库插入后");

            // 切片并向量化
            processDocument(document);

            logMemoryUsage("向量化完成");

            return convertToVO(document);
        } catch (IOException e) {
            log.error("文件上传失败: {}", e.getMessage(), e);
            throw new BusinessException("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有文档列表
     *
     * @return 文档列表
     */
    @Override
    public List<DocumentVO> getAllDocuments(Long categoryId) {
        LambdaQueryWrapper<Document> qw = new LambdaQueryWrapper<Document>()
                .orderByDesc(Document::getUploadTime);
        if (categoryId != null) {
            qw.eq(Document::getCategoryId, categoryId);
        }
        List<Document> documents = documentMapper.selectList(qw);
        Map<Long, String> categoryNames = loadCategoryNames(documents);
        return documents.stream()
                .map(d -> convertToVO(d, false, categoryNames))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public DocumentVO updateDocumentCategory(Long id, Long categoryId) {
        Document document = documentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException("文档不存在: " + id);
        }
        Long resolved = resolveCategoryId(categoryId);
        document.setCategoryId(resolved);
        document.setUpdateTime(LocalDateTime.now());
        documentMapper.updateById(document);
        // 重新向量化以刷新 metadata 中的 categoryId
        processDocument(document);
        return convertToVO(document, false);
    }

    @Override
    public List<Long> listDocumentIdsByCategory(Long categoryId) {
        if (categoryId == null) {
            return Collections.emptyList();
        }
        return documentMapper.selectList(new LambdaQueryWrapper<Document>()
                        .select(Document::getId)
                        .eq(Document::getCategoryId, categoryId)
                        .eq(Document::getProcessed, true))
                .stream()
                .map(Document::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 根据ID获取文档信息
     *
     * @param id 文档ID
     * @return 文档信息
     */
    @Override
    public DocumentVO getDocumentById(Long id) {
        Document document = documentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException("文档不存在: " + id);
        }
        return convertToVO(document, true);
    }

    /**
     * 删除文档
     *
     * @param id 文档ID
     */
    @Override
    @Transactional
    public void deleteDocument(Long id) {
        Document document = documentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException("文档不存在: " + id);
        }

        // 从向量存储中删除
        if (Boolean.TRUE.equals(document.getProcessed())) {
            try {
                embeddingService.deleteEmbeddings(List.of(String.valueOf(id)));
            } catch (Exception e) {
                log.warn("从向量存储删除文档失败: {}", e.getMessage());
            }
        }

        // 删除物理文件
        try {
            Path filePath = Paths.get(document.getFilePath());
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("删除物理文件失败: {}", e.getMessage());
        }

        // 删除数据库记录
        documentMapper.deleteById(id);
        log.info("文档已删除: ID={}", id);
    }

    /**
     * 重新处理文档（解析、切片、向量化）
     *
     * @param id 文档ID
     * @return 文档信息
     */
    @Override
    @Transactional
    public DocumentVO reprocessDocument(Long id) {
        Document document = documentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException("文档不存在: " + id);
        }

        processDocument(document);
        return convertToVO(document);
    }

    /**
     * 处理文档：切片并向量化
     *
     * @param document 文档实体
     */
    private void processDocument(Document document) {
        try {
            log.info("开始处理文档: ID={}, 文件名={}", document.getId(), document.getFileName());

            // 切片
            List<org.springframework.ai.document.Document> aiDocuments = chunkService.convertToAiDocuments(
                    document.getContent(),
                    document.getId(),
                    document.getFileName(),
                    document.getCategoryId(),
                    1000, // 切片大小
                    200 // 重叠大小
            );

            logMemoryUsage("切片完成");

            // 向量化并存储（可选跳过）
            if (skipEmbedding) {
                log.warn("⚠️ 已跳过向量化步骤（调试模式）");
            } else {
                embeddingService.embedAndStore(aiDocuments);
                logMemoryUsage("向量化完成");
            }

            // 更新文档状态
            document.setProcessed(!skipEmbedding); // 如果跳过向量化，标记为未处理
            document.setUpdateTime(LocalDateTime.now());
            documentMapper.updateById(document);

            log.info("文档处理完成: ID={}, 片段数量={}, 已向量化={}",
                    document.getId(), aiDocuments.size(), !skipEmbedding);
        } catch (Exception e) {
            log.error("文档处理失败: ID={}, 错误: {}", document.getId(), e.getMessage(), e);
            throw new BusinessException("文档处理失败: " + e.getMessage());
        }
    }

    /**
     * 保存文件到本地
     *
     * @param file 上传的文件
     * @return 文件路径
     * @throws IOException IO异常
     */
    private String saveFile(MultipartFile file) throws IOException {
        // 创建上传目录
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // 生成唯一文件名
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        String uniqueFileName = UUID.randomUUID().toString() + "." + extension;

        // 保存文件
        Path filePath = uploadPath.resolve(uniqueFileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        log.info("文件已保存: {}", filePath);
        return filePath.toString();
    }

    /**
     * 验证文件
     *
     * @param file 上传的文件
     */
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isEmpty()) {
            throw new BusinessException("文件名不能为空");
        }

        String fileType = getFileExtension(fileName).toLowerCase();
        if (!fileType.equals("pdf") && !fileType.equals("docx") && !fileType.equals("doc")) {
            throw new BusinessException("不支持的文件类型: " + fileType + "，仅支持 PDF、DOCX、DOC");
        }

        // 限制文件大小（10MB）
        long maxSize = 10 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new BusinessException("文件大小不能超过10MB");
        }
    }

    /**
     * 获取文件扩展名
     *
     * @param fileName 文件名
     * @return 文件扩展名
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            throw new BusinessException("无法识别文件类型");
        }
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == fileName.length() - 1) {
            throw new BusinessException("无法识别文件类型");
        }
        return fileName.substring(lastDotIndex + 1);
    }

    /**
     * 转换为VO（默认不含文本内容，用于列表）
     *
     * @param document 文档实体
     * @return 文档VO
     */
    private DocumentVO convertToVO(Document document) {
        return convertToVO(document, false);
    }

    private DocumentVO convertToVO(Document document, boolean includeContent) {
        Map<Long, String> names = loadCategoryNames(List.of(document));
        return convertToVO(document, includeContent, names);
    }

    private DocumentVO convertToVO(Document document, boolean includeContent, Map<Long, String> categoryNames) {
        String categoryId = document.getCategoryId() == null ? null : String.valueOf(document.getCategoryId());
        String categoryName = document.getCategoryId() == null
                ? null
                : categoryNames.get(document.getCategoryId());
        return new DocumentVO(
                document.getId() == null ? null : String.valueOf(document.getId()),
                document.getFileName(),
                document.getFileType(),
                document.getFileSize(),
                document.getUploadTime(),
                document.getProcessed(),
                categoryId,
                categoryName,
                includeContent ? document.getContent() : null);
    }

    private Long resolveCategoryId(Long categoryId) {
        if (categoryId != null) {
            return knowledgesCategoryService.requireEnabledCategoryId(categoryId);
        }
        KnowledgesCategory general = knowledgesCategoryMapper.selectOne(new LambdaQueryWrapper<KnowledgesCategory>()
                .eq(KnowledgesCategory::getCode, "general")
                .last("LIMIT 1"));
        if (general != null) {
            return general.getId();
        }
        List<KnowledgesCategory> enabled = knowledgesCategoryMapper.selectList(new LambdaQueryWrapper<KnowledgesCategory>()
                .eq(KnowledgesCategory::getStatus, "ENABLED")
                .orderByAsc(KnowledgesCategory::getSortOrder)
                .last("LIMIT 1"));
        if (enabled.isEmpty()) {
            throw new BusinessException("尚未配置可用的知识库分类，请先创建分类");
        }
        return enabled.get(0).getId();
    }

    private Map<Long, String> loadCategoryNames(List<Document> documents) {
        List<Long> ids = documents.stream()
                .map(Document::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> map = new HashMap<>();
        knowledgesCategoryMapper.selectBatchIds(ids).forEach(c -> map.put(c.getId(), c.getName()));
        return map;
    }

    /**
     * 记录内存使用情况
     *
     * @param stage 阶段描述
     */
    private void logMemoryUsage(String stage) {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory() / (1024 * 1024);

        log.debug("[{}] 内存使用: {}MB / {}MB (最大: {}MB)",
                stage, usedMemory, totalMemory, maxMemory);
    }
}
