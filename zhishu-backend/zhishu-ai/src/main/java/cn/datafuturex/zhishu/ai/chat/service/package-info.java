/**
 * Chat 模块的服务契约。
 * <p>
 * 与 {@code chat.vo}、{@code shared} 的 DTO/VO 一起构成对内公开接口，供 {@code openapi}
 * 等编排型模块依赖；实现留在 {@code chat.service.impl}，不在导出面内。
 */
@org.springframework.modulith.NamedInterface("api")
package cn.datafuturex.zhishu.ai.chat.service;
