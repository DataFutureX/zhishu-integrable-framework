/**
 * 知枢平台 Open API 对接 SDK。
 * <p>
 * 使用 AK/SK 签名鉴权调用知枢开放接口，内置 HTTP 客户端与 JSON 序列化。<br>
 * Token 格式：{@code {ak}:{timestampMs}:{signature}}，签名算法：HMAC-SHA256。
 * </p>
 *
 * @see cn.datafuturex.zhishu.openapi.sdk.ZhishuOpenApiClient
 * @see cn.datafuturex.zhishu.openapi.sdk.ZhishuOpenApiSigner
 */
package cn.datafuturex.zhishu.openapi.sdk;
