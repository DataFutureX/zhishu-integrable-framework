package com.datafuturex.assistant.biztools.api;

/**
 * 向 Agent / Platform 提供 Tool Bean。
 */
public interface BizToolProviderPort {

    /**
     * @return Spring AI {@code @Tool} Bean 数组
     */
    Object[] toolBeans();
}
