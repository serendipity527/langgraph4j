package org.bsc.langgraph4j.action;

import org.bsc.langgraph4j.state.AgentState;

import java.util.Map;

/**
 * 表示一个节点操作，作用于代理状态，并返回状态更新。
 * - 本质上是一个函数式接口，输入是代理状态，输出是状态更新。
 * @param <T> 代理状态的类型
 */
@FunctionalInterface
public interface NodeAction <T extends AgentState> {
    /**
     * 对给定的代理状态应用此操作。
     * @param state 代理状态
     * @return 一个表示操作结果的Map，key是状态的名称，value是状态的值
     * @throws Exception 如果操作失败
     */
    Map<String, Object> apply(T state) throws Exception;

}

