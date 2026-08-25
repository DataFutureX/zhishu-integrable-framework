package cn.datafuturex.zhishu.ai.knowledge.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文档解析服务接口
 * 
 * @author Qoder
 * @since 1.0.0
 */
public interface DocumentParseService {

    /**
     * 解析文档内容为文本
     *
     * @param file 上传的文件
     * @return 解析后的文本内容
     */
    String parseDocument(MultipartFile file);

    /**
     * 根据文件类型解析文档
     *
     * @param file     上传的文件
     * @param fileType 文件类型（pdf, docx, doc）
     * @return 解析后的文本内容
     */
    String parseByFileType(MultipartFile file, String fileType);

    /**
     * 解析PDF文件
     *
     * @param file PDF文件
     * @return 解析后的文本内容
     */
    String parsePdf(MultipartFile file);

    /**
     * 解析Word文档（.docx）
     *
     * @param file Word文件
     * @return 解析后的文本内容
     */
    String parseDocx(MultipartFile file);

    /**
     * 解析Word文档（.doc）
     *
     * @param file Word文件
     * @return 解析后的文本内容
     */
    String parseDoc(MultipartFile file);
}
