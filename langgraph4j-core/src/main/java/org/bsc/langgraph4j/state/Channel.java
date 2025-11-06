package org.bsc.langgraph4j.state;

import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Supplier;

import static java.util.Optional.ofNullable;
import static org.bsc.langgraph4j.state.AgentState.MARK_FOR_REMOVAL;
import static org.bsc.langgraph4j.state.AgentState.MARK_FOR_RESET;

/**
 * 基础通道实现类，封装了默认提供者和合并函数
 * @param <T> 通道保存的状态类型
 */
class BaseChannel<T> implements Channel<T> {
    final Supplier<T> defaultProvider; // 默认值提供者
    final Reducer<T> reducer;          // 合并函数

    /**
     * 构造一个新的 BaseChannel，指定合并函数和默认值提供者
     *
     * @param reducer         用于状态合并的函数，不能为空
     * @param defaultProvider 提供默认值的 Supplier，不能为空
     * @throws NullPointerException 如果 reducer 或 defaultProvider 为 null 则抛出
     */
    BaseChannel(Reducer<T> reducer, Supplier<T> defaultProvider ) {
        this.defaultProvider = defaultProvider;
        this.reducer = reducer;
    }

    /**
     * 返回一个 Optional，如果设置了默认值提供者则包含 Supplier，否则为空
     *
     * @return Optional 包含默认值提供者 Supplier，或 Optional.empty()
     */
    public Optional<Supplier<T>> getDefault() {
        return ofNullable(defaultProvider);
    }

    /**
     * 获取 Reducer 实例作为 Optional 返回
     *
     * @return Optional 包含合并函数对象 Reducer，如果为 null 则返回 Optional.empty()
     */
    public Optional<Reducer<T>> getReducer() {
        return ofNullable(reducer);
    }
}

/**
 * Channel 是用于维护状态属性的机制。
 *
 * <p>每个 Channel 与一个键（key）和一个值（value）关联。可通过调用 {@link #update(String, Object, Object)} 方法更新 Channel。更新操作应用于当前 Channel 的值。
 * <p>Channel 可以使用默认值初始化。默认值由 {@link Supplier} 提供。{@link #getDefault()} 返回包含默认 Supplier 的 Optional。
 * <p>Channel 也可以关联一个 Reducer。Reducer 是一个函数，用于将当前值与新值合并。
 * <p>{@link #update(String, Object, Object)} 方法根据提供的 key、旧值和新值更新 Channel 的状态。当未初始化时使用默认值，已初始化时使用 reducer 计算新值。
 *
 * @param <T> 状态属性的类型
 */
public interface Channel<T> {

    /**
     * 如果设置了 Reducer，则每个属性更新时将被调用以计算新值。
     *
     * @return 可选的 Reducer，如果不存在则为 Optional.empty()
     */
    Optional<Reducer<T>> getReducer() ;

    /**
     * 获取可用于初始化状态属性的默认值 Supplier。结果应该是可变的对象。
     *
     * @return 可选的默认值 Supplier
     */
    Optional<Supplier<T>> getDefault();

    /**
     * 判断传入的值是否被标记为重置。即值为 null 或等于 MARK_FOR_RESET。
     *
     * @param value 需要校验的值
     * @return 值为 null 或标记为重置（MARK_FOR_RESET）时返回 true，否则返回 false
     */
    default boolean isMarkedForReset( Object value ) {
        return value == null || value == MARK_FOR_RESET ;
    }

    /**
     * 判断传入的值是否被标记为移除。即值等于 MARK_FOR_REMOVAL。
     *
     * @param value 需要校验的值
     * @return 如果值等于 MARK_FOR_REMOVAL 则返回 true，否则返回 false
     */
    default boolean isMarkedForRemoval( Object value ) {
        return value == MARK_FOR_REMOVAL;
    }

    /**
     * 使用指定的 key 更新状态属性，并返回新的值。
     *
     * @param key      状态属性的键
     * @param oldValue 当前的值
     * @param newValue 准备设置的新值
     * @return 新的状态值
     */
    @SuppressWarnings("unchecked")
    default Object update(String key, Object oldValue, Object newValue) {
        if( isMarkedForReset(newValue) ) {
            // 如果 newValue 为 null 或 MARK_FOR_RESET，则将通道重置为默认值
            return getDefault().orElse( () -> null ).get();
        }
        if( isMarkedForRemoval(newValue)) {
            // 如果标记为移除，则返回 null
            return null;
        }

        // 将 newValue 强制转换为 T 类型，作为本次要设置的新值
        T _new = (T)newValue;

        // 如果 oldValue 为 null，则尝试从默认值提供者获取初值，否则直接强转为 T
        final T _old = (oldValue == null) ?
            getDefault().map(Supplier::get).orElse(null) :
            (T)oldValue;

        // 如果设置了 reducer，则合并老值与新值，否则直接返回新值
        return getReducer().map( reducer -> reducer.apply( _old, _new)).orElse(_new);
    }
}