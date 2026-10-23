/**
 * OpenAI 兼容客户端构建器。
 * <p>
 * 作为命名接口导出：ai 侧内部客户端与模型网关侧运行时缓存共用同一套构建逻辑，
 * 避免解密与参数拼装分叉。
 */
@org.springframework.modulith.NamedInterface("api")
package cn.datafuturex.zhishu.ai.gatewayclient.runtime;
