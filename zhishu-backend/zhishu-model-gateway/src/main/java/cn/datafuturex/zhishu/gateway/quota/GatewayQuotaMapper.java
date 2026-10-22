package cn.datafuturex.zhishu.gateway.quota;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 模型网关额度 Mapper（含原子扣减/回退语句）
 */
@Mapper
public interface GatewayQuotaMapper extends BaseMapper<GatewayQuotaEntity> {

    /**
     * 惰性滚动周期：仅当周期已过期时清零并推进窗口。
     *
     * @param id 额度行 ID
     * @return 影响行数（1=发生了滚动）
     */
    @Update("""
            UPDATE ai_gateway_quota
               SET used_tokens = 0,
                   period_start = date_trunc('month', CURRENT_TIMESTAMP),
                   period_end   = date_trunc('month', CURRENT_TIMESTAMP) + INTERVAL '1 month',
                   update_time  = CURRENT_TIMESTAMP
             WHERE id = #{id}
               AND period_end <= CURRENT_TIMESTAMP
            """)
    int rollPeriod(@Param("id") Long id);

    /**
     * 原子预留：只有未超额时才累加，返回 0 表示额度不足。
     *
     * @param id    额度行 ID
     * @param delta 预留量
     * @return 影响行数
     */
    @Update("""
            UPDATE ai_gateway_quota
               SET used_tokens = used_tokens + #{delta},
                   update_time = CURRENT_TIMESTAMP
             WHERE id = #{id}
               AND (token_limit IS NULL OR used_tokens + #{delta} <= token_limit)
            """)
    int addUsage(@Param("id") Long id, @Param("delta") long delta);

    /**
     * 结算：按「实际 - 预留」差额调整，允许负值（退回多预留），且不会低于 0。
     *
     * @param id    额度行 ID
     * @param delta 差额
     * @return 影响行数
     */
    @Update("""
            UPDATE ai_gateway_quota
               SET used_tokens = GREATEST(used_tokens + #{delta}, 0),
                   update_time = CURRENT_TIMESTAMP
             WHERE id = #{id}
            """)
    int settle(@Param("id") Long id, @Param("delta") long delta);
}
