package org.bsc.langgraph4j.state;

import java.util.List;
import java.util.function.Supplier;

/**
 * Channels 工厂接口，提供不同类型 Channel 的创建方法。
 */
public interface Channels {

    /**
     * 创建一个不可重复添加元素的 List 类型通道。
     * 元素通过 {@link AppenderChannel.ReducerDisallowDuplicate} 进行合并，禁止重复。
     *
     * @param defaultProvider 默认 List 提供者
     * @return 不允许重复的 List Channel
     * @param <T> List 元素类型
     */
    static <T> Channel<List<T>> appender(Supplier<List<T>> defaultProvider ) {
        return new AppenderChannel<T>( new AppenderChannel.ReducerDisallowDuplicate<>(), defaultProvider );
    }

    /**
     * 创建一个允许重复添加元素的 List 类型通道。
     * 元素通过 {@link AppenderChannel.ReducerAllowDuplicate} 进行合并，允许重复。
     *
     * @param defaultProvider 默认 List 提供者
     * @return 允许重复元素的 List Channel
     * @param <T> List 元素类型
     */
    static <T> Channel<List<T>> appenderWithDuplicate( Supplier<List<T>> defaultProvider ) {
        return new AppenderChannel<>( new AppenderChannel.ReducerAllowDuplicate<>(), defaultProvider );
    }

    /**
     * 创建一个只设置默认值、不设置 reducer 的通用 Channel。
     *
     * @param defaultProvider 默认值提供者
     * @return 通用 Channel 实例（无 reducer）
     * @param <T> Channel值类型
     */
    static <T>  Channel<T> base( Supplier<T> defaultProvider) {
        return new BaseChannel<>(null, defaultProvider);
    }

    /**
     * 创建一个只设置 reducer、不设置默认值的通用 Channel。
     *
     * @param reducer 合并 reducer
     * @return 通用 Channel 实例（无默认值）
     * @param <T> Channel值类型
     */
    static <T>  Channel<T> base( Reducer<T> reducer ) {
        return new BaseChannel<>(reducer, null);
    }

    /**
     * 创建一个同时设置 reducer 和默认值的通用 Channel。
     *
     * @param reducer 合并 reducer
     * @param defaultProvider 默认值提供者
     * @return 通用 Channel 实例
     * @param <T> Channel值类型
     */
    static <T> Channel<T> base( Reducer<T> reducer, Supplier<T> defaultProvider ) {
        return new BaseChannel<>(reducer, defaultProvider);
    }
}
