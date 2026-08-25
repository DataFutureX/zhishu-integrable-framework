package cn.datafuturex.zhishu.modules.dto;

/**
 * 单位查询请求 DTO
 */
public record UnitQueryDTO(
        String unitCode,
        String unitName,
        String unitType,
        Integer status,
        Long parentId,
        Integer pageNum,
        Integer pageSize
) {
    public UnitQueryDTO {
        if (pageNum == null || pageNum < 1) {
            pageNum = 1;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = 10;
        }
    }
}
