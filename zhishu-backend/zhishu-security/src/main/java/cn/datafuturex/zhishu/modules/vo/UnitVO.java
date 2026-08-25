package cn.datafuturex.zhishu.modules.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 单位树形视图对象
 */
@Data
public class UnitVO {

    private Long id;

    private Long parentId;

    private String parentName;

    private String unitCode;

    private String unitName;

    private String unitType;

    private String region;

    private String address;

    private String contactPerson;

    private String contactPhone;

    private Integer sort;

    private Integer status;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private List<UnitVO> children = new ArrayList<>();
}
