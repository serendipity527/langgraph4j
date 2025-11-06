package org.bsc.langgraph4j.action;

import org.bsc.langgraph4j.state.AgentState;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * 表示对代理状态进行操作并返回状态更新的异步节点动作接口。
 *
 * @param <S> 代理状态的类型
 */
@FunctionalInterface
public interface AsyncNodeAction<S extends AgentState> extends Function<S, CompletableFuture<Map<String, Object>>> {

    /**
     * 对给定的代理状态应用此动作。
     *
     * @param state 代理状态
     * @return 包含动作结果的CompletableFuture
     */
    CompletableFuture<Map<String, Object>> apply(S state);

    /**
     * 从同步节点动作创建一个异步节点动作。
     *
     * @param syncAction 同步节点动作
     * @param <S> 代理状态的类型
     * @return 异步节点动作
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
