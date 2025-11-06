package org.bsc.langgraph4j.action;

import org.bsc.langgraph4j.state.AgentState;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * 表示一个异步边动作接口，在给定 AgentState 时，返回用于跳转的下一个节点名称。
 *
 * @param <S> agent 状态的类型
 */
@FunctionalInterface
public interface AsyncEdgeAction<S extends AgentState> extends Function<S, CompletableFuture<String>> {

    /**
     * 对给定的 agent 状态执行此动作。
     *
     * @param state 当前的 agent 状态
     * @return 一个 CompletableFuture，表示异步跳转结果（下一个节点的名称）
     */
    CompletableFuture<String> apply(S state);

    /**
     * 将同步的 EdgeAction 转换为异步的 AsyncEdgeAction。
     *
     * @param syncAction 同步的边动作
     * @param <S> agent 状态的类型
     * @return 转换后的异步边动作
     */
    static <S extends AgentState> AsyncEdgeAction<S> edge_async(EdgeAction<S> syncAction ) {
        return t -> {
            CompletableFuture<String> result = new CompletableFuture<>();
            try {
                result.complete(syncAction.apply(t));
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
            return result;
        };
    }
}
