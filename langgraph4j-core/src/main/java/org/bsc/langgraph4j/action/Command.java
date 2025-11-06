package org.bsc.langgraph4j.action;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 表示在 LangGraph4j 图中 {@link CommandAction} 的执行结果。
 * 一个 {@code Command} 封装了图接下来的指令，包括跳转的目标节点名称和
 * 需要合并到 {@link org.bsc.langgraph4j.state.AgentState} 的状态更新映射。
 *
 * @param gotoNode 下一步要执行的节点名称。
 * @param update   {@link Map} 类型，包含需要合并到当前 agent state 的键值对。空 map 表示没有状态变更。
 */
public record Command(String gotoNode, Map<String,Object> update) {

    /** 空操作指令（无跳转节点，仅空的状态变更） */
    private static final Command EMPTY_COMMAND = new Command(Map.of());

    /**
     * 获取空操作指令。
     *
     * @return 一个无状态变更也无跳转节点的命令
     */
    public static Command emptyCommand() {
        return EMPTY_COMMAND;
    }

    /**
     * 获取必须存在的目标节点名称。
     *
     * @return 不为 null 的跳转节点名称
     * @throws NullPointerException 如果 gotoNode 为 null
     */
    public String gotoNode() {
        return Objects.requireNonNull(gotoNode, "gotoNode cannot be null");
    }

    /**
     * 获取合并到状态的属性映射。如未设置，返回空 map。
     *
     * @return 状态变更 map，永不为 null
     */
    public Map<String,Object> update() {
        return Optional.ofNullable(update).orElseGet(Map::of);
    }

    /**
     * 安全地获取跳转节点名称。
     *
     * @return 跳转节点名称的 Optional（可能为 null，对应 empty）
     */
    public Optional<String> gotoNodeSafe() {
        return Optional.ofNullable(gotoNode);
    }

    /**
     * 校验逻辑：gotoNode 和 update 不允许同时为 null。
     */
    public Command {
        if( gotoNode == null && update == null ) {
            throw new IllegalArgumentException("gotoNode and update cannot both be null");
        }
    }

    /**
     * 构造仅指定跳转节点、不包含状态变更的命令。
     * @param gotoNode 跳转节点名称，可为 null
     */
    public Command(String gotoNode) {
        this(gotoNode, null);
    }

    /**
     * 构造仅包含状态更新、无跳转节点的命令。
     * @param update 状态变更键值对 map
     */
    public Command(Map<String,Object> update) {
        this(null, update);
    }
}