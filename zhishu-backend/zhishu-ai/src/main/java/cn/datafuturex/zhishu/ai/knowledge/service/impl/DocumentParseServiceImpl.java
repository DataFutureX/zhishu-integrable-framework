package cn.datafuturex.zhishu.ai.knowledge.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import cn.datafuturex.zhishu.ai.shared.exception.BusinessException;
import cn.datafuturex.zhishu.ai.knowledge.service.DocumentParseService;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

/**
 * 文档解析服务实现类
 * 
 * @author Qoder
 * @since 1.0.0
 */
@Service
@Slf4j
public class DocumentParseServiceImpl implements DocumentParseService {

    /**
     * 解析文档内容为文本
     *
     * @param file 上传的文件
     * @return 解析后的文本内容
     */
    @Override
    public String parseDocument(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isEmpty()) {
            throw new BusinessException("文件名不能为空");
        }

        String fileType = getFileExtension(fileName);
        return parseByFileType(file, fileType);
    }

    /**
     * 根据文件类型解析文档
     *
     * @param file     上传的文件
     * @param fileType 文件类型（pdf, docx, doc）
     * @return 解析后的文本内容
     */
    @Override
    public String parseByFileType(MultipartFile file, String fileType) {
        return switch (fileType.toLowerCase()) {
            case "pdf" -> parsePdf(file);
            case "docx" -> parseDocx(file);
            case "doc" -> parseDoc(file);
            default -> throw new BusinessException("不支持的文件类型: " + fileType);
        };
    }

    /**
     * 解析PDF文件
     *
     * @param file PDF文件
     * @return 解析后的文本内容
     */
    @Override
    public String parsePdf(MultipartFile file) {
        PDDocument document = null;
        try {
            // 使用PDDocument加载PDF文档（PDFBox 3.0 API）
            byte[] pdfBytes = file.getBytes();
            log.debug("PDF文件大小: {} bytes", pdfBytes.length);

            document = org.apache.pdfbox.Loader.loadPDF(pdfBytes);
            log.debug("PDF文档页数: {}", document.getNumberOfPages());

            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            log.info("成功解析PDF文件，提取文本长度: {}", text.length());
            return text.trim();
        } catch (Exception e) {
            log.error("解析PDF文件失败: {}", e.getMessage(), e);
            throw new BusinessException("解析PDF文件失败: " + e.getMessage());
        } finally {
            // 确保资源被释放
            if (document != null) {
                try {
                    document.close();
                } catch (Exception e) {
                    log.warn("关闭PDF文档失败: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 解析Word文档（.docx）
     *
     * @param file Word文件
     * @return 解析后的文本内容
     */
    @Override
    public String parseDocx(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream();
                XWPFDocument document = new XWPFDocument(inputStream)) {

            StringBuilder text = new StringBuilder();
            List<XWPFParagraph> paragraphs = document.getParagraphs();

            log.debug("DOCX段落数量: {}", paragraphs.size());

            for (XWPFParagraph paragraph : paragraphs) {
                text.append(paragraph.getText()).append("\n");
            }

            log.info("成功解析DOCX文件，提取文本长度: {}", text.length());
            return text.toString().trim();
        } catch (Exception e) {
            log.error("解析DOCX文件失败: {}", e.getMessage(), e);
            throw new BusinessException("解析DOCX文件失败: " + e.getMessage());
        }
    }

    /**
     * 解析Word文档（.doc）
     *
     * @param file Word文件
     * @return 解析后的文本内容
     */
    @Override
    public String parseDoc(MultipartFile file) {
        HWPFDocument document = null;
        WordExtractor extractor = null;
        try {
            byte[] docBytes = file.getBytes();
            log.debug("DOC文件大小: {} bytes", docBytes.length);

            // 使用ByteArrayInputStream加载，避免流的问题
            try (ByteArrayInputStream bais = new ByteArrayInputStream(docBytes)) {
                document = new HWPFDocument(bais);
                extractor = new WordExtractor(document);

                String text = extractor.getText();

                if (text == null || text.isEmpty()) {
                    log.warn("DOC文件解析结果为空");
                    return "";
                }

                log.info("成功解析DOC文件，提取文本长度: {}", text.length());
                return text.trim();
            }
        } catch (Exception e) {
            log.error("解析DOC文件失败: {}", e.getMessage(), e);
            throw new BusinessException("解析DOC文件失败: " + e.getMessage());
        } finally {
            // 确保资源被释放
            if (extractor != null) {
                try {
                    extractor.close();
                } catch (Exception e) {
                    log.warn("关闭WordExtractor失败: {}", e.getMessage());
                }
            }
            if (document != null) {
                try {
                    document.close();
                } catch (Exception e) {
                    log.warn("关闭HWPFDocument失败: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 获取文件扩展名
     *
     * @param fileName 文件名
     * @return 文件扩展名
     */
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == fileName.length() - 1) {
            throw new BusinessException("无法识别文件类型");
        }
        return fileName.substring(lastDotIndex + 1);
    }
}
