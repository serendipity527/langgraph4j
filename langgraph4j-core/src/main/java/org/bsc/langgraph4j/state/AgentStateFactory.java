package org.bsc.langgraph4j.state;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AgentState的工厂接口（用于创建AgentState实例）。
 *
 * @param <State> agent状态类型
 */
public interface AgentStateFactory<State extends AgentState> extends Function<Map<String,Object>, State> {

    /**
     * 根据 schema 获取包含所有定义了默认值通道的初始数据（键值对）。
     * @param schema 通道定义的schema map
     * @return 包含所有有默认值通道的初始数据的map
     */
    default Map<String,Object> initialDataFromSchema( Map<String,Channel<?>> schema  ) {
        return schema.entrySet().stream()
                .filter( c -> c.getValue().getDefault().isPresent() ) // 筛选出有默认值的Channel
                .collect(Collectors.toMap(Map.Entry::getKey, e ->
                        e.getValue().getDefault().get().get() // 获取默认值
                ));
    }

    /**
     * 通过schema创建并初始化State。
     * 此方法会从schema中提取每个有定义默认值的channel，并生成初始状态数据。
     *
     * @param schema 通道定义的schema map
     * @return 应用默认值后的初始化State
     */
    default State applyFromSchema(Map<String,Channel<?>> schema )  {
        return apply( initialDataFromSchema(schema) );
    }

}
