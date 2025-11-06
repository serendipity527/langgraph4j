package org.bsc.langgraph4j;

import org.bsc.langgraph4j.action.*;
import org.bsc.langgraph4j.internal.edge.Edge;
import org.bsc.langgraph4j.internal.edge.EdgeCondition;
import org.bsc.langgraph4j.internal.edge.EdgeValue;
import org.bsc.langgraph4j.internal.node.Node;
import org.bsc.langgraph4j.internal.node.SubCompiledGraphNode;
import org.bsc.langgraph4j.internal.node.SubStateGraphNode;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.AgentStateFactory;
import org.bsc.langgraph4j.state.Channel;

import java.util.*;

import static java.lang.String.format;
import static java.util.Collections.unmodifiableMap;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.bsc.langgraph4j.state.AgentState.MARK_FOR_REMOVAL;
import static org.bsc.langgraph4j.utils.CollectionsUtils.mergeMap;

/**
 * 表示带有节点和边的状态图。
 *
 * @param <State> 与图关联的状态类型
 */
public class StateGraph<State extends AgentState> {

    /**
     * 内部枚举，表示与图状态相关的各种错误消息。
     */
    public enum Errors {
        invalidNodeIdentifier("[%s] is not a valid node id!"),
        invalidEdgeIdentifier("[%s] is not a valid edge sourceId!"),
        duplicateNodeError("node with id: %s already exist!"),
        duplicateEdgeError("edge with id: %s already exist!"),
        duplicateConditionalEdgeError("conditional edge from '%s' already exist!"),
        edgeMappingIsEmpty("edge mapping is empty!"),
        missingEntryPoint("missing Entry Point"),
        entryPointNotExist("entryPoint: %s doesn't exist!"),
        finishPointNotExist("finishPoint: %s doesn't exist!"),
        missingNodeReferencedByEdge("edge sourceId '%s' refers to undefined node!"),
        missingNodeInEdgeMapping("edge mapping for sourceId: %s contains a not existent nodeId %s!"),
        invalidEdgeTarget("edge sourceId: %s has an initialized target value!"),
        duplicateEdgeTargetError("edge [%s] has duplicate targets %s!"),
        unsupportedConditionalEdgeOnParallelNode("parallel node doesn't support conditional branch, but on [%s] a conditional branch on %s have been found!"),
        illegalMultipleTargetsOnParallelNode("parallel node [%s] must have only one target, but %s have been found!"),
        interruptionNodeNotExist("node '%s' configured as interruption doesn't exist!");

        private final String errorMessage;

        Errors(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        /**
         * 创建带有格式化错误信息的 GraphStateException。
         *
         * @param args 用于格式化错误消息的参数
         * @return 新的 GraphStateException
         */
        public GraphStateException exception(Object... args) {
            return new GraphStateException(format(errorMessage, (Object[]) args));
        }
    }

    /**
     * 结束节点标识符
     */
    public static String END = "__END__";
    /**
     * 开始节点标识符
     */
    public static String START = "__START__";

    /**
     * 图的节点集合
     */
    final Nodes<State> nodes = new Nodes<>();
    /**
     * 图的边集合
     */
    final Edges<State> edges = new Edges<>();

    /**
     * 状态通道映射
     */
    private final Map<String, Channel<?>> channels;

    /**
     * 状态序列化器
     */
    private final StateSerializer<State> stateSerializer;

    /**
     * 构造状态图
     * @param channels 图的状态 schema
     * @param stateSerializer 状态序列化器
     */
    public StateGraph(Map<String, Channel<?>> channels,
                      StateSerializer<State> stateSerializer) {
        this.channels = channels;
        this.stateSerializer = Objects.requireNonNull(stateSerializer, "stateSerializer cannot be null");
    }

    /**
     * 用指定的状态序列化器构造状态图。
     * @param stateSerializer 状态序列化器
     */
    public StateGraph(StateSerializer<State> stateSerializer) {
        this(Map.of(), stateSerializer);
    }

    /**
     * 基于状态工厂构造状态图。
     * @param stateFactory 用于创建 agent 状态的工厂
     */
    public StateGraph(AgentStateFactory<State> stateFactory) {
        this(Map.of(), stateFactory);

    }

    /**
     * 构造方法，带有通道与状态工厂参数
     * @param channels 图的状态 schema
     * @param stateFactory 状态工厂
     */
    public StateGraph(Map<String, Channel<?>> channels, AgentStateFactory<State> stateFactory) {
        this(channels, new ObjectStreamStateSerializer<>(stateFactory));
    }

    /**
     * 获取当前状态序列化器
     * @return 状态序列化器
     */
    public StateSerializer<State> getStateSerializer() {
        return stateSerializer;
    }

    /**
     * 获取 AgentStateFactory
     * @return agent 状态工厂
     */
    public final AgentStateFactory<State> getStateFactory() {
        return stateSerializer.stateFactory();
    }

    /**
     * 获取不可变通道映射
     * @return channel 映射
     */
    public Map<String, Channel<?>> getChannels() {
        return unmodifiableMap(channels);
    }

    /**
     * 添加节点到图中
     * @param id 节点标识符
     * @param action 节点对应的异步动作
     * @throws GraphStateException 当节点 id 不合法或已存在时抛出
     */
    public StateGraph<State> addNode(String id, AsyncNodeAction<State> action) throws GraphStateException {
        return addNode(id, AsyncNodeActionWithConfig.of(action));
    }

    /**
     * 添加节点到图中
     * @param id 节点标识符
     * @param action 节点动作
     * @return 当前状态图
     * @throws GraphStateException 如果节点已存在或 id 不合法
     */
    public StateGraph<State> addNode(String id, AsyncNodeActionWithConfig<State> action) throws GraphStateException {
        if (Objects.equals(id, END)) {
            throw Errors.invalidNodeIdentifier.exception(END);
        }
        Node<State> node = new Node<>(id, (config) -> action);

        if (nodes.elements.contains(node)) {
            throw Errors.duplicateNodeError.exception(id);
        }

        nodes.elements.add(node);
        return this;
    }

    /**
     * 添加一个带有条件边行为的节点
     * @param id 节点 id
     * @param action 用于决定下一个目标节点的动作
     * @param mappings 条件到目标节点的映射
     * @throws GraphStateException 如果 id 不合法，映射为空或节点已存在
     */
    public StateGraph<State> addNode(String id, AsyncCommandAction<State> action, Map<String, String> mappings) throws GraphStateException {
        // 简单实现，将节点行为置空，并添加条件边
        return addNode(id, (state, config) -> completedFuture(Map.of()))
                .addConditionalEdges(id, action, mappings);

        /*
        // 另外一种实现方式
        final var nextNodeIdProperty = format("%s_next_node", id );

        return addNode( id, ( state, config ) ->
            action.apply( state, config ).thenApply( command ->
                mergeMap( Map.of( nextNodeIdProperty, command.gotoNode()) , command.update() ) )

        ).addConditionalEdges( id, ( state, config ) ->
            completedFuture(
                    new Command( state.<String>value( nextNodeIdProperty )
                                    .orElseThrow( () -> new IllegalStateException(format("state property '%s' has not been specified! ", nextNodeIdProperty) )),
                            Map.of( nextNodeIdProperty, MARK_FOR_REMOVAL ) ))
        , mappings );
        */
    }

    /**
     * 添加一个已编译子图作为节点
     * 子图与父图共享相同的状态
     * @param id 子图节点标识符
     * @param subGraph 已编译子图
     * @return 当前状态图
     * @throws GraphStateException 如果 id 不合法或节点已存在
     */
    public StateGraph<State> addNode(String id, CompiledGraph<State> subGraph) throws GraphStateException {
        if (Objects.equals(id, END)) {
            throw Errors.invalidNodeIdentifier.exception(END);
        }

        var node = new SubCompiledGraphNode<>(id, subGraph);

        if (nodes.elements.contains(node)) {
            throw Errors.duplicateNodeError.exception(id);
        }

        nodes.elements.add(node);
        return this;
    }

    /**
     * 添加子图作为节点 (已废弃)
     * @param id 子图节点标识符
     * @param subGraph 已编译子图
     * @return 当前状态图
     * @throws GraphStateException 如果 id 不合法或节点已存在
     * @deprecated 使用 addNode(String, CompiledGraph<State>) 替代
     */
    @Deprecated(forRemoval = true)
    public StateGraph<State> addSubgraph(String id, CompiledGraph<State> subGraph) throws GraphStateException {
        return addNode(id, subGraph);
    }

    /**
     * 添加 StateGraph 类型的子图作为节点
     * 子图与父图共享同一个状态
     * @param id 子图节点标识符
     * @param subGraph 子图（将在父图编译时编译自身）
     * @return 当前状态图
     * @throws GraphStateException 如果 id 不合法或节点已存在
     */
    public StateGraph<State> addNode(String id, StateGraph<State> subGraph) throws GraphStateException {
        if (Objects.equals(id, END)) {
            throw Errors.invalidNodeIdentifier.exception(END);
        }

        subGraph.validateGraph();

        var node = new SubStateGraphNode<>(id, subGraph);

        if (nodes.elements.contains(node)) {
            throw Errors.duplicateNodeError.exception(id);
        }

        nodes.elements.add(node);
        return this;
    }

    /**
     * 添加子图作为节点 (已废弃)
     * 子图与父图共享同一个状态
     * @param id 子图节点标识符
     * @param subGraph 子图
     * @return 当前状态图
     * @throws GraphStateException 如果 id 不合法或节点已存在
     * @deprecated 使用 add(String, StateGraph<State>) 替代
     */
    @Deprecated(forRemoval = true)
    public StateGraph<State> addSubgraph(String id, StateGraph<State> subGraph) throws GraphStateException {
        return addNode(id, subGraph);
    }

    /**
     * 添加一条无条件边
     * @param sourceId 源节点 id
     * @param targetId 目标节点 id
     * @throws GraphStateException 如果边已存在或 id 不合法
     */
    public StateGraph<State> addEdge(String sourceId, String targetId) throws GraphStateException {
        if (Objects.equals(sourceId, END)) {
            throw Errors.invalidEdgeIdentifier.exception(END);
        }

        var newEdge = new Edge<>(sourceId, new EdgeValue<State>(targetId));

        int index = edges.elements.indexOf(newEdge);
        if (index >= 0) {
            var newTargets = new ArrayList<>(edges.elements.get(index).targets());
            newTargets.add(newEdge.target());
            edges.elements.set(index, new Edge<>(sourceId, newTargets));
        } else {
            edges.elements.add(newEdge);
        }

        return this;
    }

    /**
     * 添加条件边
     * @param sourceId 源节点 id
     * @param condition 条件判断逻辑
     * @param mappings 条件到目标节点的映射
     * @throws GraphStateException 如果边已存在、id 不合法或条件映射为空
     */
    public StateGraph<State> addConditionalEdges(String sourceId, AsyncCommandAction<State> condition, Map<String, String> mappings) throws GraphStateException {
        if (Objects.equals(sourceId, END)) {
            throw Errors.invalidEdgeIdentifier.exception(END);
        }
        if (mappings == null || mappings.isEmpty()) {
            throw Errors.edgeMappingIsEmpty.exception(sourceId);
        }

        var newEdge = new Edge<>(sourceId, new EdgeValue<>(new EdgeCondition<>(condition, mappings)));

        if (edges.elements.contains(newEdge)) {
            throw Errors.duplicateConditionalEdgeError.exception(sourceId);
        } else {
            edges.elements.add(newEdge);
        }
        return this;
    }

    /**
     * 添加条件边的另一重载（通过 AsyncEdgeAction 包装）
     * @param sourceId 源节点 id
     * @param condition 条件判断逻辑
     * @param mappings 条件到目标节点的映射
     * @throws GraphStateException 如果边已存在、id 不合法或条件映射为空
     */
    public StateGraph<State> addConditionalEdges(String sourceId, AsyncEdgeAction<State> condition, Map<String, String> mappings) throws GraphStateException {
        return addConditionalEdges(sourceId, AsyncCommandAction.of(condition), mappings);
    }

    /**
     * 验证整个图的结构以及所有节点和边的合法性
     * @throws GraphStateException 校验失败时抛出
     */
    void validateGraph() throws GraphStateException {
        for (var node : nodes.elements) {
            node.validate();
        }

        var edgeStart = edges.edgeBySourceId(START)
                .orElseThrow(Errors.missingEntryPoint::exception);

        edgeStart.validate(nodes);

        for (Edge<State> edge : edges.elements) {
            edge.validate(nodes);
        }
    }

    /**
     * 编译状态图为可运行的已编译图
     * @param config 编译配置
     * @return 已编译图
     * @throws GraphStateException 如果图中存在不合法结构
     */
    public CompiledGraph<State> compile(CompileConfig config) throws GraphStateException {
        Objects.requireNonNull(config, "config cannot be null");

        validateGraph();

        return new CompiledGraph<>(this, config);
    }

    /**
     * 默认使用默认配置编译状态图
     * @return 已编译图
     * @throws GraphStateException 如果图不合法
     */
    public CompiledGraph<State> compile() throws GraphStateException {
        return compile(CompileConfig.builder().build());
    }

    /**
     * 生成可视化/可绘制的状态图描述
     * @param type 图类型
     * @param title 图标题
     * @param printConditionalEdges 是否显示条件边
     * @return 图形表示
     */
    public GraphRepresentation getGraph(GraphRepresentation.Type type, String title, boolean printConditionalEdges) {
        String content = type.generator.generate(nodes, edges, title, printConditionalEdges);
        return new GraphRepresentation(type, content);
    }

    /**
     * 生成可视化/可绘制的状态图描述（条件边默认显示）
     * @param type 图类型
     * @param title 图标题
     * @return 图形表示
     */
    public GraphRepresentation getGraph(GraphRepresentation.Type type, String title) {
        String content = type.generator.generate(nodes, edges, title, true);
        return new GraphRepresentation(type, content);
    }

    /**
     * 节点内部类。包含本状态图所有节点的集合及常用操作。
     */
    public static class Nodes<State extends AgentState> {
        /**
         * 节点集合，按插入顺序
         */
        public final Set<Node<State>> elements;

        /**
         * 使用现有集合构造
         * @param elements 节点集合
         */
        public Nodes(Collection<Node<State>> elements) {
            this.elements = new LinkedHashSet<>(elements);
        }

        /**
         * 默认构造，空集合
         */
        public Nodes() {
            this.elements = new LinkedHashSet<>();
        }

        /**
         * 判断集合中是否有指定 id 的节点
         * @param id 节点 id
         * @return 是否命中
         */
        public boolean anyMatchById(String id) {
            return elements.stream()
                    .anyMatch(n -> Objects.equals(n.id(), id));
        }

        /**
         * 仅返回子图节点（SubStateGraphNode）
         * @return 子图节点列表
         */
        public List<SubStateGraphNode<State>> onlySubStateGraphNodes() {
            return elements.stream()
                    .filter(n -> n instanceof SubStateGraphNode<State>)
                    .map(n -> (SubStateGraphNode<State>) n)
                    .toList();
        }

        /**
         * 返回非子图节点（非 SubStateGraphNode）
         * @return 非子图节点列表
         */
        public List<Node<State>> exceptSubStateGraphNodes() {
            return elements.stream()
                    .filter(n -> !(n instanceof SubStateGraphNode<State>))
                    .toList();
        }
    }

    /**
     * 边的内部类。包含本图所有边的集合及常用操作。
     */
    public static class Edges<State extends AgentState> {

        /**
         * 边集合，按插入顺序
         */
        public final List<Edge<State>> elements;

        /**
         * 以已有集合构造
         * @param elements 边集合
         */
        public Edges(Collection<Edge<State>> elements) {
            this.elements = new LinkedList<>(elements);
        }

        /**
         * 默认构造，空集合
         */
        public Edges() {
            this.elements = new LinkedList<>();
        }

        /**
         * 获取指定源节点的边（只返回第一个命中的）
         * @param sourceId 源节点 id
         * @return 可选的边
         */
        public Optional<Edge<State>> edgeBySourceId(String sourceId) {
            return elements.stream()
                    .filter(e -> Objects.equals(e.sourceId(), sourceId))
                    .findFirst();
        }

        /**
         * 查找所有目标节点 id 为 targetId 的边
         * @param targetId 目标节点 id
         * @return 所有命中边的集合
         */
        public List<Edge<State>> edgesByTargetId(String targetId) {
            return elements.stream()
                    .filter(e -> e.anyMatchByTargetId(targetId)).toList();
        }
    }

}
