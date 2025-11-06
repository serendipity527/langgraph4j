package org.bsc.langgraph4j.state;

import java.util.*;
import java.util.function.Supplier;

import static java.util.Collections.unmodifiableList;
import static java.util.Optional.ofNullable;
import static org.bsc.langgraph4j.state.AgentState.MARK_FOR_REMOVAL;
import static org.bsc.langgraph4j.state.AgentState.MARK_FOR_RESET;

/**
 * AppenderChannel 是一种 {@link Channel} 实现，
 * 用于累积一组值集合（List）。
 *
 * @param <T> 被累积值的类型
 */
public class AppenderChannel<T> implements Channel<List<T>> {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AppenderChannel.class);

    /**
     * 用于标识和移除List中某元素的函数式接口
     *
     * @param <T> List中元素的类型
     */
    @FunctionalInterface
    public interface RemoveIdentifier<T> {
        /**
         * 比较指定元素与指定索引处元素的方法
         *
         * @param element  被比较的元素
         * @param atIndex  用于比较的元素的索引
         * @return 负数、0或者正数；分别代表小于、等于或大于被比较目标
         */
        int compareTo(T element, int atIndex );
    }

    /**
     * 表示用于"替换整个List"的新值包装类型
     * @param <T>
     */
    public record ReplaceAllWith<T>(List<T> newValues ) {

        /**
         * 用List创建ReplaceAllWith
         */
        public static <T> ReplaceAllWith<T> of(List<T> newValues ) {
            return new ReplaceAllWith<>(newValues);
        }
        /**
         * 用单值创建ReplaceAllWith
         */
        public static <T> ReplaceAllWith<T> of(T newValue ) {
            return new ReplaceAllWith<>( List.of(newValue) );
        }
    }

    /**
     * 不允许重复的Reducer实现（通过hash判断）
     * @param <T>
     */
    public static class ReducerDisallowDuplicate<T> implements Reducer<List<T>> {

        @Override
        public List<T> apply(List<T> left, List<T> right) {
            if (left == null) {
                return right;
            }
            for (T rValue : right) {
                // 移除重复；如果left中没有与rValue hash值等价的元素，则添加
                if (left.stream().noneMatch(lValue -> Objects.hash(lValue) == Objects.hash(rValue))) {
                    left.add(rValue);
                }
            }
            return left;
        }
    }

    /**
     * 允许重复的Reducer实现
     * @param <T>
     */
    public static class ReducerAllowDuplicate<T> implements Reducer<List<T>> {

        @Override
        public List<T> apply(List<T> left, List<T> right) {
            if (left == null) {
                return right;
            }
            left.addAll(right);
            return left;
        }
    }

    private final Reducer<List<T>> reducer;
    private final Supplier<List<T>> defaultProvider;

    /**
     * 返回当前reducer（如果有）
     */
    @Override
    public Optional<Reducer<List<T>>> getReducer() {
        return ofNullable(reducer);
    }

    /**
     * 返回默认值提供者（如果有）
     */
    @Override
    public Optional<Supplier<List<T>>> getDefault() {
        return ofNullable(defaultProvider);
    }

    /**
     * 构造带指定Reducer和默认值提供者的AppenderChannel
     *
     * @param reducer 合并List的Reducer
     * @param defaultProvider 默认List生成器
     */
    protected AppenderChannel( Reducer<List<T>> reducer,  Supplier<List<T>> defaultProvider ) {
        this.reducer = reducer;
        this.defaultProvider = defaultProvider;
    }

    /**
     * 基于 RemoveIdentifier 从指定List移除元素。
     * 会先复制原List，执行移除，返回不可变视图。
     *
     * @param list            原始list
     * @param removeIdentifier 元素匹配标识器
     * @return 移除后的不可变list
     */
    private List<T> remove(List<T> list, RemoveIdentifier<T> removeIdentifier ) {
        var result = new ArrayList<>(list);
        removeFromList(result, removeIdentifier);
        return unmodifiableList(result);
    }

    /**
     * 从List中移除第一个匹配 RemoveIdentifier 的元素
     *
     * @param result 待操作list
     * @param removeIdentifier 匹配器
     */
    private void removeFromList(List<T> result, RemoveIdentifier<T> removeIdentifier ) {
        // 用迭代器安全移除元素
        var iterator = result.iterator();
        int index = 0;
        while (iterator.hasNext()) {
            T element = iterator.next();
            if (removeIdentifier.compareTo(element, index++) == 0) {
                iterator.remove();
            }
        }
    }

    /**
     * 表示用于移除操作的数据对象
     * 
     * @param <T> 老值的类型
     */
    record RemoveData<T>( List<T> oldValues, List<?> newValues) {
        // 拷贝构造器，保证list可变
        public RemoveData {
            oldValues = new ArrayList<>(oldValues);
            newValues = new ArrayList<>(newValues);
        }
    };

    /**
     * 处理移除标记的逻辑，返回移除后的新结果
     *
     * @param oldValues 老值
     * @param newValues 新值（包含可能的RemoveIdentifier）
     * @return 移除匹配项后的RemoveData
     */
    @SuppressWarnings("unchecked")
    private RemoveData<T> evaluateRemoval(List<T> oldValues, List<?> newValues ) {
        final var result = new RemoveData<>( oldValues, newValues );

        newValues.stream()
                .filter( value -> value instanceof RemoveIdentifier<?> )
                .forEach( value -> {
                    result.newValues().remove( value );
                    var removeIdentifier = (RemoveIdentifier<T>) value;
                    removeFromList( result.oldValues(), removeIdentifier );
                });
        return result;
    }

    /**
     * 校验新值的类型，并强制转换为指定类型List
     */
    @SuppressWarnings("unchecked")
    protected List<T> validateNewValues(List<?> list  ) {
        return (List<T>)list;
    }

    /**
     * 更新指定key的通道内容
     * 
     * @param key     要更新的key
     * @param oldValue    旧值
     * @param newValue    新值（如为null返回旧值）
     * @return        更新后的新值（或旧值）
     * @throws UnsupportedOperationException   如果底层list不可变且不支持update
     */
    @SuppressWarnings("unchecked")
    public final Object update( String key, Object oldValue, Object newValue) {
        ;
        if( isMarkedForReset(newValue) ) {
            // 如果newValue为null或复位标志，则用默认值重置通道
            return getDefault().orElse(ArrayList::new).get();
        }
        if( isMarkedForRemoval(newValue) ) {
            return null;
        }

        boolean oldValueIsList = oldValue instanceof List<?>;

        try {
            if( newValue instanceof ReplaceAllWith<?> replaceAll ) {
                return List.copyOf(replaceAll.newValues());
            }
            if( oldValueIsList && newValue instanceof RemoveIdentifier<?> ) {
                return remove( (List<T>)oldValue, (RemoveIdentifier<T>)newValue);
            }
            List<?> list = null;
            if (newValue instanceof List) {
                list = (List<Object>) newValue;
            } else if (newValue.getClass().isArray()) {
                list = Arrays.asList((T[])newValue);
            }
            else {
                list = List.of(newValue);
            }
            if (list.isEmpty()) {
                return oldValue;
            }
            var typedList = validateNewValues(list);
            if( oldValueIsList ) {
                var result = evaluateRemoval( (List<T>)oldValue, typedList );
                return Channel.super.update(key, result.oldValues(), result.newValues());
            }
            return Channel.super.update(key, oldValue, typedList);
        }
        catch (UnsupportedOperationException ex) {
            log.error("不支持的操作：可能是channels被初始化为不可变List导致，请检查！");
            throw ex;
        }
    }

}