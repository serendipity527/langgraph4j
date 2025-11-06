package org.bsc.langgraph4j.action;

import org.bsc.langgraph4j.state.AgentState;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * 表示一个异步的节点操作，作用于代理状态，并返回状态更新。
 *
 * @param <S> 代理状态的类型
 */
@FunctionalInterface
public interface AsyncNodeAction<S extends AgentState> extends Function<S, CompletableFuture<Map<String, Object>>> {

    /**
     * 对给定的代理状态应用此操作。
     *
     * @param state 代理状态
     * @return 一个表示操作结果的CompletableFuture
     */
    CompletableFuture<Map<String, Object>> apply(S state);

    /**
     * 从同步节点操作创建异步节点操作。将同步节点包装为异步节点
     *
     * @param syncAction 同步节点操作
     * @param <S> 代理状态的类型
     * @return 一个异步节点操作
     */
    static <S extends AgentState> AsyncNodeAction<S> node_async(NodeAction<S> syncAction) {
        return t -> {
            CompletableFuture<Map<String, Object>> result = new CompletableFuture<>();
            try {
                result.complete(syncAction.apply(t));
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
            return result;
        };
    }
}
