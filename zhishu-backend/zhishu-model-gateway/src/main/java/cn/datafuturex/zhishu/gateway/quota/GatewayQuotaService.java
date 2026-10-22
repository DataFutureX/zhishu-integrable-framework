package cn.datafuturex.zhishu.gateway.quota;

import cn.datafuturex.zhishu.gateway.auth.GatewayPrincipal;

/**
 * 额度服务：周期内 token 额度的查看、预留与结算。
 */
public interface GatewayQuotaService {

    /**
     * 请求前预留额度（原子扣减）。
     *
     * @param principal     调用主体
     * @param requestTokens 预留量
     * @return 预留凭据
     * @throws cn.datafuturex.zhishu.gateway.compat.GatewayException 额度不足（429 insufficient_quota）
     */
    QuotaReservation reserve(GatewayPrincipal principal, long requestTokens);

    /**
     * 结算：按「实际 - 预留」的差额调整已用量。
     *
     * @param quotaId 额度行 ID，null 表示不限量
     * @param delta   差额，可为负
     */
    void settle(Long quotaId, long delta);

    /**
     * 全额退回预留（上游直接失败时）。
     *
     * @param quotaId        额度行 ID
     * @param reservedTokens 预留量
     */
    void release(Long quotaId, long reservedTokens);

    /**
     * 读取当前主体额度快照（用于 /v1/quota 展示）。
     *
     * @param principal 调用主体
     * @return 额度快照
     */
    QuotaSnapshot snapshot(GatewayPrincipal principal);

    /**
     * 额度预留凭据。
     *
     * @param quotaId           额度行 ID（null 表示不限量，无需结算）
     * @param reservedTokens    预留 token 数
     * @param limit             周期上限（null 表示不限量）
     * @param resetAtEpochSecond 周期重置时间（epoch 秒）
     */
    record QuotaReservation(
            Long quotaId,
            long reservedTokens,
            Long limit,
            Long resetAtEpochSecond
    ) {
        /**
         * 是否受到额度约束。
         *
         * @return true=需要结算
         */
        public boolean tracked() {
            return quotaId != null;
        }
    }

    /**
     * 额度快照。
     *
     * @param subjectType        主体类型
     * @param subjectKey         主体键
     * @param periodKind         周期类型
     * @param limit              周期上限（null 表示不限量）
     * @param used               已用（含预留）
     * @param remaining          剩余（不限量时为 -1）
     * @param resetAtEpochSecond 重置时间（epoch 秒）
     * @param status             状态
     */
    record QuotaSnapshot(
            String subjectType,
            String subjectKey,
            String periodKind,
            Long limit,
            long used,
            long remaining,
            Long resetAtEpochSecond,
            String status
    ) {
    }
}
