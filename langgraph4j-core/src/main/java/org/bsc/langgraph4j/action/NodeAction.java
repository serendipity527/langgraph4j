package org.bsc.langgraph4j.action;

import org.bsc.langgraph4j.state.AgentState;

import java.util.Map;

/**
 * NodeAction 表示一个节点可执行的动作。
 * <p>
 * 用于在节点处理时，对 {@link AgentState} 执行自定义逻辑，并返回结果映射。
 *
 * @param <T> AgentState 的类型，必须继承自 AgentState
 */
@FunctionalInterface
public interface NodeAction <T extends AgentState> {

    /**
     * 执行节点动作，并返回相应的结果映射。
     *
     * @param state 当前节点的 agent state
     * @return 结果映射（键值对），可用于后续节点处理
     * @throws Exception 执行过程中可能抛出的异常
     */
    Map<String, Object> apply(T state) throws Exception;
}
