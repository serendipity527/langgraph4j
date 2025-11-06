package org.bsc.langgraph4j.action;

import org.bsc.langgraph4j.state.AgentState;

/**
 * EdgeAction 表示作用于 AgentState 后返回字符串结果的边动作（edge action）。
 * 
 * <p>用于在图的边执行某种操作，并将处理结果（如下一个节点的名称等）以字符串形式返回。
 *
 * @param <S> agent state 的类型，必须为 AgentState 的子类型
 */
@FunctionalInterface
public interface EdgeAction<S extends AgentState> {

    /**
     * 对给定的 agent state 执行动作逻辑并返回结果。
     *
     * @param state 当前的 agent state
     * @return 动作执行的结果字符串（如可作为边的目标节点名）
     * @throws Exception 动作执行过程中出现的异常
     */
    String apply(S state) throws Exception;
}
