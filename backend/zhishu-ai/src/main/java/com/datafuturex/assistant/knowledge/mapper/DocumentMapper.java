package com.datafuturex.assistant.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datafuturex.assistant.knowledge.domain.Document;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文档 Mapper（表 documents）
 */
@Mapper
public interface DocumentMapper extends BaseMapper<Document> {

    Document selectByFileName(@Param("fileName") String fileName);

    List<Document> selectProcessedDocuments();

    List<Document> selectUnprocessedDocuments();
}
