package org.bsc.langgraph4j.action;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.state.AgentState;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * 表示一个可以带配置参数运行的异步节点动作接口。
 *
 * @param <S> 代理状态的类型
 */
public interface AsyncNodeActionWithConfig<S extends AgentState> extends BiFunction<S, RunnableConfig, CompletableFuture<Map<String, Object>>> {

    /**
     * 针对给定的代理状态和执行配置执行动作。
     *
     * @param state  代理状态
     * @param config 运行配置
     * @return 包含动作结果的CompletableFuture
     */
    CompletableFuture<Map<String, Object>> apply(S state, RunnableConfig config);

    /**
     * 将同步的 {@link NodeActionWithConfig} 转换为异步动作接口。
     *
     * @param syncAction 要转换的同步节点动作
     * @param <S>        代理状态类型
     * @return 异步节点动作接口
     */
    static <S extends AgentState> AsyncNodeActionWithConfig<S> node_async(NodeActionWithConfig<S> syncAction) {
        return (t, config) -> {
            CompletableFuture<Map<String, Object>> result = new CompletableFuture<>();
            try {
                result.complete(syncAction.apply(t, config));
            } catch (Throwable e) {
                result.completeExceptionally(e);
            }
            return result;
        };
    }

    /**
     * 将一个简单的 AsyncNodeAction 适配成带配置的 AsyncNodeActionWithConfig。
     *
     * @param action 简单的异步节点动作
     * @param <S>    代理状态类型
     * @return 带配置参数的异步节点动作
     */
    static <S extends AgentState> AsyncNodeActionWithConfig<S> of(AsyncNodeAction<S> action) {
        return (t, config) -> action.apply(t);
    }

}