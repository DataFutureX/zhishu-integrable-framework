package cn.datafuturex.zhishu.openapi.sdk.model;

/**
 * 知识图谱同步结果。
 *
 * @param nodesCreated  新增节点数
 * @param nodesUpdated  更新节点数
 * @param edgesCreated  新增边数
 * @param edgesUpdated  更新边数
 * @param dryRun        是否为试运行
 */
public record KgSyncResult(
        int nodesCreated,
        int nodesUpdated,
        int edgesCreated,
        int edgesUpdated,
        boolean dryRun
) {
}
