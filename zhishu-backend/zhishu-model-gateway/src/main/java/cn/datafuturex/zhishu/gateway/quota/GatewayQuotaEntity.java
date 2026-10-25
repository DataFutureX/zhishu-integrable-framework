package cn.datafuturex.zhishu.gateway.quota;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模型网关：token 额度 —— 表 ai_gateway_quota
 */
@Data
@TableName("ai_gateway_quota")
public class GatewayQuotaEntity {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** USER / ROLE / DEFAULT */
    private String subjectType;

    /** USER=username；ROLE=role_code；DEFAULT=* */
    private String subjectKey;

    /** 周期类型：MONTHLY */
    private String periodKind;

    /** 周期内 token 上限，null=不限量；改回不限量必须能写回 NULL */
    @TableField(value = "token_limit", updateStrategy = FieldStrategy.ALWAYS)
    private Long tokenLimit;

    /** 当前周期已用（含预留） */
    private Long usedTokens;

    /** 当前周期起点 */
    private LocalDateTime periodStart;

    /** 当前周期终点 */
    private LocalDateTime periodEnd;

    /** ENABLED / DISABLED */
    private String status;

    @TableField(value = "remark", updateStrategy = FieldStrategy.ALWAYS)
    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
