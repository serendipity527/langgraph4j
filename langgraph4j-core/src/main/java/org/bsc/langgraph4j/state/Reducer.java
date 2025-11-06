package org.bsc.langgraph4j.state;

import java.util.function.BiFunction;

/**
 * 表示一个二元操作符，接收两个相同类型的值，并生成相同类型的结果。
 * 第一个参数为旧值，第二个参数为新值。
 * @param <T> 操作数和结果的类型
 */
public interface Reducer<T> extends BiFunction<T, T, T> {
}