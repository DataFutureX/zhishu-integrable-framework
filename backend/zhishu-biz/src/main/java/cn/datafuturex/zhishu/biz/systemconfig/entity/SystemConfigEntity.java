package cn.datafuturex.zhishu.biz.systemconfig.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统配置实体（单例，id 固定为 1）
 */
@Data
@TableName("sys_config")
public class SystemConfigEntity {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String systemName;
    /** 英文标题 */
    private String englishTitle;
    private String systemIcon;
    private String copyright;
    /** 系统介绍信息 */
    private String systemIntroduction;
    /** 项目地 */
    private String projectSite;
    /** 是否开启登录重试次数限制 */
    private Boolean loginRetryLimitEnabled;
    /** 允许用户名密码错误次数 */
    private Integer loginMaxRetryAttempts;
    /** 超过失败次数后锁定分钟数 */
    private Integer loginLockMinutes;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
