package org.bsc.langgraph4j.state;

import org.bsc.langgraph4j.utils.CollectionsUtils;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

import static java.util.Collections.unmodifiableMap;
import static java.util.Optional.ofNullable;
import static org.bsc.langgraph4j.utils.CollectionsUtils.entryOf;

/**
 * 表示一个包含数据映射的代理状态
 */
public class AgentState {
    // 标记用于重置的常量对象
    public static final Object MARK_FOR_RESET = new Object();
    // 标记用于移除的常量对象
    public static final Object MARK_FOR_REMOVAL = new Object();

    // 保存状态数据的Map
    private final java.util.Map<String,Object> data;

    /**
     * 使用给定的初始数据构建AgentState对象
     * @param initData 代理状态的初始数据
     */
    public AgentState(Map<String,Object> initData) {
        this.data = new HashMap<>(initData);
    }

    /**
     * 获取数据Map的不可修改视图
     * @return 数据Map的不可修改视图
     */
    public final java.util.Map<String,Object> data() {
        return unmodifiableMap(data);
    }


    /**
     * 根据key获取关联的值，如果存在
     * @param key 需要返回其值的key
     * @param <T> 值的类型
     * @return 包含值的Optional，如果不存在则为空
     */
    @SuppressWarnings("unchecked")
    public final <T> Optional<T> value(String key) { return ofNullable((T) data().get(key));}

    /**
     * 返回代理状态的字符串表示形式
     * @return 数据Map的字符串表示形式
     */
    @Override
    public String toString() {
        return CollectionsUtils.toString(data);
    }

    /**
     * 生成一个Collector，用于合并数据，同时移除被标记的项
     * @return Collector对象
     */
    private static Collector<Map.Entry<String,Object>, ?, Map<String, Object>> toMapRemovingItemMarkedForRemoval() {
        final BinaryOperator<Object> mergeFunction = ( currentValue, newValue ) -> newValue;

        return Collector.of(
                HashMap::new,
                (map, element) -> {
                    var key     = element.getKey();
                    var value   = element.getValue();
                    // 如果值为null、重置或移除，则从map中移除
                    if( value == null || value == MARK_FOR_RESET || value == MARK_FOR_REMOVAL) {
                        map.remove(key);
                    }
                    else {
                        map.merge(key, value, mergeFunction);
                    }
                },
                (map1, map2) -> {
                    map2.forEach( (key, value) -> {
                        if ( value != null && value != MARK_FOR_RESET && value != MARK_FOR_REMOVAL) {
                            map1.merge(key, value, mergeFunction);
                        }
                    });
                    return map1;
                },
                Collector.Characteristics.UNORDERED);
    }

    /**
     * 生成一个Collector，允许值为null
     * @return Collector对象
     */
    private static Collector<Map.Entry<String,Object>, ?, Map<String, Object>> toMapAllowingNulls() {
        return Collector.of(
                HashMap::new,
                (map, element) -> map.put(element.getKey(), element.getValue()),
                (map1, map2) -> {
                    map1.putAll(map2);
                    return map1;
                },
                Collector.Characteristics.UNORDERED);
    }

    /**
     * 使用channels，根据schema更新部分状态
     * @param state 当前完整状态的Key-Value映射
     * @param partialState 需要被更新的部分状态
     * @param channels channel名称及其对应实现的map
     * @return 经schema与channel处理后的部分状态
     */
    private static Map<String,Object> updatePartialStateFromSchema(  Map<String,Object> state, Map<String,Object> partialState, Map<String, Channel<?>> channels ) {
        if( channels == null || channels.isEmpty() ) {
            return partialState;
        }
        return partialState.entrySet().stream().map( entry -> {

            Channel<?> channel = channels.get(entry.getKey());
            if (channel != null) {
                Object newValue = channel.update( entry.getKey(), state.get(entry.getKey()), entry.getValue());
                return entryOf(entry.getKey(), newValue);
            }

            return entry;
        })
        .collect(toMapAllowingNulls());
    }

    /**
     * 用提供的部分状态更新完整状态
     * 合并时会将新旧值融合，部分状态中的相同键将覆盖旧值
     * @param state 当前状态
     * @param partialState 用于更新的部分状态
     * @param channels 用于（如有需要）更新部分状态的channels
     * @return 更新后的新状态
     * @throws NullPointerException 如果state为null
     */
    public static Map<String,Object> updateState( Map<String,Object> state, Map<String,Object> partialState, Map<String, Channel<?>> channels ) {
        Objects.requireNonNull(state, "state cannot be null");
        if (partialState == null || partialState.isEmpty()) {
            return state;
        }

        Map<String, Object> updatedPartialState = updatePartialStateFromSchema(state, partialState, channels);

        return  Stream.concat( state.entrySet().stream(), updatedPartialState.entrySet().stream())
                .collect(toMapRemovingItemMarkedForRemoval());
    }

    /**
     * 用提供的部分状态更新完整状态
     * 合并时会将新旧值融合，部分状态中的相同键将覆盖旧值
     * @param state 当前AgentState对象
     * @param partialState 用于更新的部分状态
     * @param channels 用于（如有需要）更新部分状态的channels
     * @return 更新后的新状态
     * @throws NullPointerException 如果state为null
     */
    public static Map<String,Object> updateState( AgentState state, Map<String,Object> partialState, Map<String, Channel<?>> channels ) {
        return updateState(state.data(), partialState, channels);
    }

    /**
     * 返回指定key关联的值，没有则返回默认值
     * @param key 需查找的key
     * @param defaultValue 未找到时返回的默认值
     * @param <T> 值的类型
     * @return 指定key关联的值，如果找不到则返回defaultValue
     * @deprecated 此方法已废弃，未来版本可能删除
     */
    @Deprecated(forRemoval = true)
    public final <T> T value(String key, T defaultValue ) { return this.<T>value(key).orElse(defaultValue);}

    /**
     * 返回指定key关联的值，如果没有则返回提供的默认Supplier的值
     * @param key 需查找的key
     * @param defaultProvider 一个提供默认值的函数
     * @param <T> 值的类型
     * @return 指定key对应的值，若没有则返回defaultProvider返回值
     * @deprecated 此方法已废弃，未来版本可能删除
     */
    @Deprecated(forRemoval = true)
    public final <T> T value(String key, Supplier<T>  defaultProvider ) { return this.<T>value(key).orElseGet(defaultProvider); }

    /**
     * 合并当前状态与部分状态并返回一个新状态
     * @param partialState 部分状态
     * @param channels 用于更新部分状态的channels（如有需要）
     * @return 合并后的新状态
     * @deprecated 使用 {@link #updateState(AgentState, Map, Map)} 替代
     */
    @Deprecated(forRemoval = true)
    public final Map<String,Object> mergeWith( Map<String,Object> partialState, Map<String, Channel<?>> channels ) {
        return updateState(data(), partialState, channels);
    }

}