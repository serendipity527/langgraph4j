package org.bsc.langgraph4j.state;

import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.SnapshotOutput;
import org.bsc.langgraph4j.checkpoint.Checkpoint;

import java.util.Objects;

import static java.lang.String.*;

/**
 * StateSnapshot 类表示某一节点在特定状态下的快照。
 * 
 * @param <State> 继承自 AgentState 的状态类型
 */
public final class StateSnapshot<State extends AgentState> extends NodeOutput<State> implements SnapshotOutput {
    // 保存执行配置
    private final RunnableConfig config;

    /**
     * 获取下一步要执行的节点名称。
     * 
     * @return 下一个节点的名称，如果不存在则返回 null
     */
    public String next() {
        return config.nextNode().orElse(null);
    }

    /**
     * 获取当前的执行配置。
     * 
     * @return RunnableConfig 实例
     */
    public RunnableConfig config() {
        return config;
    }

    /**
     * 私有构造方法，初始化 StateSnapshot 实例。
     * 
     * @param node 当前节点名称，不能为空
     * @param state 当前状态实例，不能为空
     * @param config 当前的执行配置，不能为空
     */
    private StateSnapshot(String node, State state, RunnableConfig config) {
        super(
            Objects.requireNonNull(node, "node cannot be null"),
            Objects.requireNonNull(state, "state cannot be null")
        );
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    /**
     * 重写 toString 方法，便于调试和日志输出。
     * 
     * @return 对象的字符串描述
     */
    @Override
    public String toString() {
        return format("StateSnapshot{node=%s, state=%s, config=%s}", node(), state(), config());
    }

    /**
     * 静态工厂方法，根据检查点和工厂函数创建 StateSnapshot 实例。
     * 
     * @param checkpoint 检查点对象，包含节点ID、状态等信息
     * @param config 当前的执行配置
     * @param factory AgentStateFactory，用于根据检查点状态生成实际的 State 实例
     * @param <State> 继承自 AgentState 的状态类型
     * @return 新的 StateSnapshot 实例
     */
    public static <State extends AgentState> StateSnapshot<State> of(Checkpoint checkpoint, RunnableConfig config, AgentStateFactory<State> factory) {
        // 构建新的执行配置，并设置检查点ID和下一个节点
        RunnableConfig newConfig = RunnableConfig.builder(config)
                .checkPointId(checkpoint.getId())
                .nextNode(checkpoint.getNextNodeId())
                .build();
        // 根据检查点和工厂函数创建新的 StateSnapshot
        return new StateSnapshot<>(checkpoint.getNodeId(), factory.apply(checkpoint.getState()), newConfig);
    }
}
