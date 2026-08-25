package cn.datafuturex.zhishu.ai.agent.graph;

import java.util.ArrayList;
import java.util.List;

public class WorkflowGraph {
    private int version = 1;
    private List<GraphNode> nodes = new ArrayList<>();
    private List<GraphEdge> edges = new ArrayList<>();

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public List<GraphNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<GraphNode> nodes) {
        this.nodes = nodes != null ? nodes : new ArrayList<>();
    }

    public List<GraphEdge> getEdges() {
        return edges;
    }

    public void setEdges(List<GraphEdge> edges) {
        this.edges = edges != null ? edges : new ArrayList<>();
    }
}
