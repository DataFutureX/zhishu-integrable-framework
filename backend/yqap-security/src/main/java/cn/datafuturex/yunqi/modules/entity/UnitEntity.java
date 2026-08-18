package cn.datafuturex.yunqi.modules.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单位管理实体
 */
@Data
@TableName("sys_unit")
public class UnitEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 父单位ID，0 表示根节点 */
    private Long parentId;

    /** 单位编码 */
    private String unitCode;

    /** 单位名称 */
    private String unitName;

    /** 单位类型 */
    private String unitType;

    /** 所属区域 */
    private String region;

    /** 单位地址 */
    private String address;

    /** 联系人 */
    private String contactPerson;

    /** 联系电话 */
    private String contactPhone;

    /** 同级排序 */
    private Integer sort;

    /** 状态（1-启用，0-停用） */
    private Integer status;

    /** 备注 */
    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
