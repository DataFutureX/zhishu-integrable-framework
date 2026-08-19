package com.datafuturex.assistant.knowledge.service;

import org.springframework.web.multipart.MultipartFile;

import com.datafuturex.assistant.knowledge.dto.DocumentUploadDTO;
import com.datafuturex.assistant.knowledge.domain.Document;
import com.datafuturex.assistant.knowledge.vo.DocumentVO;

import java.util.List;

/**
 * 文档管理服务接口
 * 
 * @author Qoder
 * @since 1.0.0
 */
public interface DocumentManagementService {

    /**
     * 上传并处理文档
     *
     * @param file      上传的文件
     * @param uploadDTO 上传请求信息
     * @return 文档信息
     */
    DocumentVO uploadDocument(MultipartFile file, DocumentUploadDTO uploadDTO);

    /**
     * 获取文档列表
     *
     * @param categoryId 知识库分类 ID，可空表示全部
     * @return 文档列表
     */
    List<DocumentVO> getAllDocuments(Long categoryId);

    /**
     * 根据ID获取文档信息
     *
     * @param id 文档ID
     * @return 文档信息
     */
    DocumentVO getDocumentById(Long id);

    /**
     * 调整文档所属知识库
     */
    DocumentVO updateDocumentCategory(Long id, Long categoryId);

    /**
     * 删除文档
     *
     * @param id 文档ID
     */
    void deleteDocument(Long id);

    /**
     * 重新处理文档（解析、切片、向量化）
     *
     * @param id 文档ID
     * @return 文档信息
     */
    DocumentVO reprocessDocument(Long id);

    /**
     * 查询某知识库下的文档 ID 列表
     */
    List<Long> listDocumentIdsByCategory(Long categoryId);
}
