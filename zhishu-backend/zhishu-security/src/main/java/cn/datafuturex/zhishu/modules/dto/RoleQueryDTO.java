package cn.datafuturex.zhishu.modules.dto;

/**
 * 角色分页查询 DTO
 */
public record RoleQueryDTO(
        String roleCode,
        String roleName,
        Integer status,
        Integer pageNum,
        Integer pageSize
) {
    public RoleQueryDTO {
        if (pageNum == null || pageNum < 1) {
            pageNum = 1;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = 10;
        }
    }
}
