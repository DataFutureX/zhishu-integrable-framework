package cn.datafuturex.zhishu.ai.openapi.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 开放应用 VO（管理端展示用）。
 */
@Data
public class OpenAppVO {

    private Long id;

    private String code;

    private String name;

    private String status;

    /** 允许的调用范围，JSON 数组字符串 */
    private String allowedScopes;

    private String remark;

    /** Access Key（明文，可展示给管理员） */
    private String accessKey;

    /** AK/SK 最近生成时间 */
    private LocalDateTime akskGeneratedAt;

    /** 最近一次调用时间 */
    private LocalDateTime lastUsedAt;

    private LocalDateTime createTime;
}
