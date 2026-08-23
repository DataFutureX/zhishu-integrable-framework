package cn.datafuturex.zhishu.ai.knowledge.service;

import cn.datafuturex.zhishu.ai.knowledge.dto.KnowledgesCategoryCreateDTO;
import cn.datafuturex.zhishu.ai.knowledge.dto.KnowledgesCategoryUpdateDTO;
import cn.datafuturex.zhishu.ai.knowledge.vo.KnowledgesCategoryVO;

import java.util.List;

public interface KnowledgesCategoryService {

    List<KnowledgesCategoryVO> listAll(boolean includeDisabled);

    KnowledgesCategoryVO getById(Long id);

    KnowledgesCategoryVO create(KnowledgesCategoryCreateDTO dto);

    KnowledgesCategoryVO update(Long id, KnowledgesCategoryUpdateDTO dto);

    void delete(Long id);

    /** 校验分类存在且启用，返回 id；categoryId 为空时返回 null */
    Long requireEnabledCategoryId(Long categoryId);
}
