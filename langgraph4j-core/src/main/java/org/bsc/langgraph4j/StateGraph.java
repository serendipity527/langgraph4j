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
 * 表示一个有节点和边的状态图。
 *
 * @param <State> 与图关联的状态类型
 */
public class StateGraph<State extends AgentState> {

    /**
     * 枚举：图状态相关的各种错误信息
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
        interruptionNodeNotExist( "node '%s' configured as interruption doesn't exist!")
        ;

        private final String errorMessage;

        Errors(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        /**
         * 使用格式化参数创建新的GraphStateException异常。
         * @param args 格式化参数
         * @return 新的GraphStateException
         */
        public GraphStateException exception(Object... args) {
            return new GraphStateException(format(errorMessage, (Object[]) args));
        }
    }

    /** 结束节点常量 */
    public static String END = "__END__";
    /** 起始节点常量 */
    public static String START = "__START__";

    /** 节点集合 */
    final Nodes<State> nodes = new Nodes<>();
    /** 边集合 */
    final Edges<State> edges = new Edges<>();

    /** 状态通道定义 */
    private final Map<String, Channel<?>> channels;

    /** 状态序列化器 */
    private final StateSerializer<State> stateSerializer;

    /**
     * 构造函数，使用通道映射和状态序列化器
     *
     * @param channels 图的状态模式定义
     * @param stateSerializer 状态序列化器
     */
    public StateGraph(Map<String, Channel<?>> channels,
                      StateSerializer<State> stateSerializer) {
        this.channels = channels;
        this.stateSerializer = Objects.requireNonNull(stateSerializer, "stateSerializer cannot be null");
    }

    /**
     * 使用给定的序列化器构造新的StateGraph。
     *
     * @param stateSerializer 状态序列化器
     */
    public StateGraph(StateSerializer<State> stateSerializer) {
        this(Map.of(), stateSerializer);
    }

    /**
     * 使用给定状态工厂构造新的StateGraph。
     *
     * @param stateFactory 用于创建AgentState的工厂
     */
    public StateGraph(AgentStateFactory<State> stateFactory) {
        this(Map.of(), stateFactory);
    }

    /**
     * 构造函数，使用通道映射和状态工厂
     *
     * @param channels 图的状态模式定义
     * @param stateFactory 状态工厂
     */
    public StateGraph(Map<String, Channel<?>> channels, AgentStateFactory<State> stateFactory) {
        this(channels, new ObjectStreamStateSerializer<>(stateFactory));
    }

    /**
     * 获取状态序列化器
     */
    public StateSerializer<State> getStateSerializer() {
        return stateSerializer;
    }

    /**
     * 获取状态工厂
     */
    public final AgentStateFactory<State> getStateFactory() {
        return stateSerializer.stateFactory();
    }

    /**
     * 获取状态通道的不可变映射
     */
    public Map<String, Channel<?>> getChannels() {
        return unmodifiableMap(channels);
    }

    /**
     * 添加节点到图中。
     *
     * @param id 节点标识
     * @param action 节点的执行动作
     * @throws GraphStateException 如果节点标识无效或节点已存在
     */
    public StateGraph<State> addNode(String id, AsyncNodeAction<State> action) throws GraphStateException {
        return addNode(id, AsyncNodeActionWithConfig.of(action));
    }

    /**
     * 添加节点到图中。
     *
     * @param id 节点标识
     * @param action 节点的执行动作
     * @return this
     * @throws GraphStateException 如果节点标识无效或节点已存在
     */
    public StateGraph<State> addNode(String id, AsyncNodeActionWithConfig<State> action) throws GraphStateException {
        if (Objects.equals(id, END)) {
            throw Errors.invalidNodeIdentifier.exception(END);
        }
        // 创建新节点
        Node<State> node = new Node<>(id, (config) -> action);

        if (nodes.elements.contains(node)) {
            throw Errors.duplicateNodeError.exception(id);
        }

        nodes.elements.add(node);
        return this;
    }

    /**
     * 添加包含条件边的节点（判定节点）。
     *
     * @param id 节点标识
     * @param action 判定后跳转逻辑
     * @param mappings 各条件对应的目标节点
     * @throws GraphStateException 如果节点标识无效、映射为空或节点已存在
     */
    public StateGraph<State> addNode(String id, AsyncCommandAction<State> action, Map<String, String> mappings) throws GraphStateException {

        // 更加简洁的实现
        return addNode(id, (state, config) -> completedFuture(Map.of()))
                .addConditionalEdges(id, action, mappings);

        /*
        // 另一种实现（暂不启用）
        final var nextNodeIdProperty = format("%s_next_node", id);

        return addNode(id, (state, config) ->
            action.apply(state, config).thenApply(command ->
                mergeMap(Map.of(nextNodeIdProperty, command.gotoNode()), command.update()))
        ).addConditionalEdges(id, (state, config) ->
            completedFuture(
                new Command(
                    state.<String>value(nextNodeIdProperty)
                        .orElseThrow(() -> new IllegalStateException(format("state property '%s' has not been specified! ", nextNodeIdProperty))),
                    Map.of(nextNodeIdProperty, MARK_FOR_REMOVAL)
                )
            )
        , mappings);
        */
    }

    /**
     * 添加已编译子图为节点，子图与父图共享状态
     *
     * @param id 节点标识
     * @param subGraph 已编译子图
     * @return 当前StateGraph实例
     * @throws GraphStateException 若节点标识无效或节点已存在
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
     * 添加已编译子图为节点（已弃用，建议用addNode）
     *
     * @deprecated 请改用 addNode(String, CompiledGraph<State>) 方法
     */
    @Deprecated(forRemoval = true)
    public StateGraph<State> addSubgraph(String id, CompiledGraph<State> subGraph) throws GraphStateException {
        return addNode(id, subGraph);
    }

    /**
     * 添加子图为节点，子图将随父图编译，二者共享状态
     *
     * @param id 节点标识
     * @param subGraph 待编译子图
     * @return 当前StateGraph实例
     * @throws GraphStateException 若节点标识无效或节点已存在
     */
    public StateGraph<State> addNode(String id, StateGraph<State> subGraph) throws GraphStateException {
        if (Objects.equals(id, END)) {
            throw Errors.invalidNodeIdentifier.exception(END);
        }

        // 先验证子图
        subGraph.validateGraph();

        var node = new SubStateGraphNode<>(id, subGraph);

        if (nodes.elements.contains(node)) {
            throw Errors.duplicateNodeError.exception(id);
        }

        nodes.elements.add(node);
        return this;
    }

    /**
     * 添加子图为节点（已弃用，建议用addNode）
     *
     * @deprecated 请改用 addNode(String, StateGraph<State>) 方法
     */
    @Deprecated(forRemoval = true)
    public StateGraph<State> addSubgraph(String id, StateGraph<State> subGraph) throws GraphStateException {
        return addNode(id, subGraph);
    }

    /**
     * 添加边到图中
     *
     * @param sourceId 源节点标识
     * @param targetId 目标节点标识
     * @throws GraphStateException 如果边标识无效或边已存在
     */
    public StateGraph<State> addEdge(String sourceId, String targetId) throws GraphStateException {
        if (Objects.equals(sourceId, END)) {
            throw Errors.invalidEdgeIdentifier.exception(END);
        }

        var newEdge = new Edge<>(sourceId, new EdgeValue<State>(targetId));

        int index = edges.elements.indexOf(newEdge);
        if (index >= 0) {
            // 已存在同sourceId的边，追加目标
            var newTargets = new ArrayList<>(edges.elements.get(index).targets());
            newTargets.add(newEdge.target());
            edges.elements.set(index, new Edge<>(sourceId, newTargets));
        } else {
            // 添加新边
            edges.elements.add(newEdge);
        }
        return this;
    }

    /**
     * 添加条件边到图中
     *
     * @param sourceId 源节点标识
     * @param condition 用于判断的异步动作
     * @param mappings 条件到目标节点的映射
     * @throws GraphStateException 如果边标识无效、映射为空或边已存在
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
     * 添加条件边到图（基于AsyncEdgeAction适配AsyncCommandAction）
     *
     * @param sourceId 源节点标识
     * @param condition 条件动作
     * @param mappings 条件到目标节点的映射
     * @throws GraphStateException 如果边标识无效、映射为空或边已存在
     */
    public StateGraph<State> addConditionalEdges(String sourceId, AsyncEdgeAction<State> condition, Map<String, String> mappings) throws GraphStateException {
        return addConditionalEdges(sourceId, AsyncCommandAction.of(condition), mappings);
    }

    /**
     * 校验整个状态图的合法性
     * @throws GraphStateException 图结构、逻辑或状态不合法时抛出
     */
    void validateGraph() throws GraphStateException {
        for (var node : nodes.elements) {
            node.validate();
        }

        // 必须存在起始节点
        var edgeStart = edges.edgeBySourceId(START)
                .orElseThrow(Errors.missingEntryPoint::exception);

        edgeStart.validate(nodes);

        for (Edge<State> edge : edges.elements) {
            edge.validate(nodes);
        }
    }

    /**
     * 编译状态图为已编译图
     *
     * @param config 编译配置
     * @return 编译后的图对象
     * @throws GraphStateException 如果存在图的非法状态
     */
    public CompiledGraph<State> compile(CompileConfig config) throws GraphStateException {
        Objects.requireNonNull(config, "config cannot be null");

        validateGraph();

        return new CompiledGraph<>(this, config);
    }

    /**
     * 编译状态图为已编译图（使用默认配置）
     *
     * @return 编译后的图对象
     * @throws GraphStateException 如果存在图的非法状态
     */
    public CompiledGraph<State> compile() throws GraphStateException {
        return compile(CompileConfig.builder().build());
    }

    /**
     * 生成状态图的可绘制图表示
     *
     * @param type 图类型
     * @param title 图标题
     * @param printConditionalEdges 是否输出条件边
     * @return 状态图的图形表示描述
     */
    public GraphRepresentation getGraph(GraphRepresentation.Type type, String title, boolean printConditionalEdges) {

        String content = type.generator.generate(nodes, edges, title, printConditionalEdges);

        return new GraphRepresentation(type, content);
    }

    /**
     * 生成状态图的可绘制图表示（默认打印条件边）
     *
     * @param type 图类型
     * @param title 图标题
     * @return 状态图的图形表示描述
     */
    public GraphRepresentation getGraph(GraphRepresentation.Type type, String title) {

        String content = type.generator.generate(nodes, edges, title, true);

        return new GraphRepresentation(type, content);
    }

    /**
     * 节点集合结构，集合元素有序且唯一
     */
    public static class Nodes<State extends AgentState> {
        public final Set<Node<State>> elements;

        public Nodes(Collection<Node<State>> elements) {
            this.elements = new LinkedHashSet<>(elements);
        }

        public Nodes() {
            this.elements = new LinkedHashSet<>();
        }

        /**
         * 检查集合中是否存在指定id的节点
         * @param id 节点id
         * @return 是否存在
         */
        public boolean anyMatchById(String id) {
            return elements.stream()
                    .anyMatch(n -> Objects.equals(n.id(), id));
        }

        /**
         * 获取所有子状态图节点
         * @return 子状态图节点列表
         */
        public List<SubStateGraphNode<State>> onlySubStateGraphNodes() {
            return elements.stream()
                    .filter(n -> n instanceof SubStateGraphNode<State>)
                    .map(n -> (SubStateGraphNode<State>) n)
                    .toList();
        }

        /**
         * 获取除去子状态图节点的节点列表
         * @return 普通节点列表
         */
        public List<Node<State>> exceptSubStateGraphNodes() {
            return elements.stream()
                    .filter(n -> !(n instanceof SubStateGraphNode<State>))
                    .toList();
        }
    }

    /**
     * 边集合结构
     */
    public static class Edges<State extends AgentState> {

        public final List<Edge<State>> elements;

        public Edges(Collection<Edge<State>> elements) {
            this.elements = new LinkedList<>(elements);
        }

        public Edges() {
            this.elements = new LinkedList<>();
        }

        /**
         * 根据源节点id查找边
         * @param sourceId 源节点id
         * @return 对应边对象（可为空）
         */
        public Optional<Edge<State>> edgeBySourceId(String sourceId) {
            return elements.stream()
                    .filter(e -> Objects.equals(e.sourceId(), sourceId))
                    .findFirst();
        }

        /**
         * 查找以指定目标节点为target的所有边
         * @param targetId 目标节点id
         * @return 边的列表
         */
        public List<Edge<State>> edgesByTargetId(String targetId) {
            return elements.stream()
                    .filter(e -> e.anyMatchByTargetId(targetId)).toList();
        }
    }

}

