package org.bsc.langgraph4j.state;

import java.util.Objects;

/**
 * 用于实现 {@link AppenderChannel.RemoveIdentifier<T>} 接口的记录类型。
 * 该类通过对象的哈希值来标识唯一性，并支持通过哈希值比较移除元素。
 *
 * @param <T> 将与此 RemoveByHash 实例关联的值的类型
 */
public record RemoveByHash<T>(T value ) implements AppenderChannel.RemoveIdentifier<T> {

    /**
     * 根据对象的哈希值比较当前值和另一个元素。
     * 如果两个对象的哈希值不同，则返回值不为0。
     *
     * @param element 要比较的元素
     * @param atIndex 元素在上下文中的索引（此处未用，仅为接口兼容）
     * @return 当前值与给定元素哈希值之差
     */
    @Override
    public int compareTo(T element, int atIndex) {
        return Objects.hashCode(value) - Objects.hashCode(element);
    }

    /**
     * 静态工厂方法，用于创建一个新的 {@code RemoveByHash} 实例。
     *
     * @param <T> 值的类型
     * @param value 要存储于 {@code RemoveByHash} 的值
     * @return 一个新的 {@code RemoveByHash} 实例
     */
    public static <T> RemoveByHash<T> of ( T value ) {
        return new RemoveByHash<>(value);
    }
}