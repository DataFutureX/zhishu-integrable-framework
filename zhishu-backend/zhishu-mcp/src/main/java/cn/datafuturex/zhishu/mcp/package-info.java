/**
 * MCP 双平面模块。
 * <p>
 * 两个方向上互不依赖：
 * <ul>
 *   <li>{@code provide} —— 我方对外提供 MCP 服务（{@code /mcp} 端点、入站鉴权、能力白名单、限流与审计）；</li>
 *   <li>{@code consume} —— 接入他方提供的 MCP 服务（上游登记、连接与工具发现、熔断、Agent 工具装配）。</li>
 * </ul>
 * 两平面只共享 {@code common}（配置 / JSON / 建表 / 审计写入），对外只经
 * {@code provide.spi} 与 {@code consume.api} 供 {@code zhishu-ai} 反向依赖；
 * 本模块不得依赖 {@code cn.datafuturex.zhishu.ai..}（由架构护栏强制）。
 */
package cn.datafuturex.zhishu.mcp;
