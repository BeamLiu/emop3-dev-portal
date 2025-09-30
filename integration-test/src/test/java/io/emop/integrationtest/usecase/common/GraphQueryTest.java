package io.emop.integrationtest.usecase.common;

import io.emop.model.common.ItemRevision;
import io.emop.model.common.UserContext;
import io.emop.service.S;
import io.emop.service.api.data.GraphQueryService;
import io.emop.service.api.data.NativeSqlService;
import io.emop.service.api.data.ObjectService;
import io.emop.service.api.domain.common.AssociateRelationService;
import io.emop.service.api.relation.RelationType;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static io.emop.integrationtest.util.Assertion.*;

/**
 * 基于Predicate的NodeQuery实现的测试用例
 */
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GraphQueryTest {
    private final String testPrefix = "GRAPH-INTEGRATION-" + System.currentTimeMillis();
    private List<ItemRevision> items = new ArrayList<>();

    /**
     * 准备测试数据
     */
    @BeforeAll
    public void prepareTestData() {
        UserContext.setCurrentUser(new UserContext(100l, List.of("admin")));
        log.info("准备测试数据");

        // 创建测试物料
        items = IntStream.range(0, 5)
                .mapToObj(i -> {
                    ItemRevision item = ItemRevision.newModel(testPrefix + "-ITEM-" + i, "A");
                    item.setName("集成测试物料" + i);
                    item.setDescription("用于前后端集成测试的物料" + i);
                    return S.service(ObjectService.class).save(item);
                })
                .toList();

        // 建立关系网络
        AssociateRelationService relationService = S.service(AssociateRelationService.class);

        // 创建一个星形网络：items[0] 作为中心节点
        ItemRevision centerItem = items.get(0);
        for (int i = 1; i < items.size(); i++) {
            relationService.appendRelation(centerItem, RelationType.reference, items.get(i));
        }

        // 创建一些其他关系
        relationService.appendRelation(items.get(1), RelationType.target, items.get(2));
        relationService.appendRelation(items.get(2), RelationType.reference, items.get(3));

        log.info("创建了{}个测试物料，建立了关系网络", items.size());

        performSync();
    }

    @SneakyThrows
    private void performSync() {
        //需要 master datasource
        UserContext.runAsSystem(() -> UserContext.ensureCurrent().byPass(() -> {
            S.service(NativeSqlService.class).executeDDL("SELECT graph.process_sync_tasks();");
        }));
        //等待主从同步
        Thread.sleep(100);
    }


    /**
     * 测试基本Cypher查询功能
     */
    @Test
    public void testBasicCypherQuery() {
        log.info("测试基本Cypher查询功能");

        GraphQueryService graphService = S.service(GraphQueryService.class);

        // 测试简单的节点查询
        String query = "MATCH (n) WHERE n.code CONTAINS '" + testPrefix + "' RETURN n LIMIT 10";

        GraphQueryService.GraphQueryResult result = graphService.executeGraphQuery(query, Collections.emptyMap());

        assertNotNull(result);
        assertNotNull(result.getNodes());
        assertTrue("应该查询到测试节点", result.getNodes().size() > 0);

        // 验证返回的节点确实是我们创建的测试数据
        boolean foundTestNode = result.getNodes().stream()
                .anyMatch(node -> {
                    String name = node.getLabel();
                    return name != null && name.contains("集成测试物料");
                });

        assertTrue("应该找到测试创建的节点", foundTestNode);

        log.info("基本Cypher查询测试通过，查询到{}个节点，{}条边",
                result.getNodes().size(), result.getEdges().size());
    }

    /**
     * 测试节点搜索功能
     */
    @Test
    public void testNodeSearch() {
        log.info("测试节点搜索功能");

        GraphQueryService graphService = S.service(GraphQueryService.class);
        // 搜索包含"集成测试"关键词的节点
        String searchQuery = "MATCH (n) WHERE n.name CONTAINS '集成测试' AND n.code CONTAINS '" + testPrefix + "'  RETURN n LIMIT 20";

        GraphQueryService.GraphQueryResult result = graphService.executeGraphQuery(searchQuery, Collections.emptyMap());

        assertNotNull(result);
        assertTrue("搜索应该找到相关节点", result.getNodes().size() > 0);

        // 验证搜索结果的准确性
        boolean allRelevant = result.getNodes().stream()
                .allMatch(node -> {
                    String name = node.getLabel();
                    return name != null && name.contains("集成测试");
                });

        assertTrue("所有搜索结果都应该包含关键词", allRelevant);

        log.info("节点搜索测试通过，找到{}个相关节点", result.getNodes().size());
    }

    /**
     * 测试节点邻居查询
     */
    @Test
    public void testNodeNeighbors() {
        Long nodeId = items.get(0).getId();
        log.info("测试节点邻居查询，节点ID: {}", nodeId);

        GraphQueryService graphService = S.service(GraphQueryService.class);

        // 查询指定节点的邻居
        String neighborQuery = String.format(
                "MATCH (n {id: %d})-[r]-(m) RETURN n, r, m", nodeId);

        GraphQueryService.GraphQueryResult result = graphService.executeGraphQuery(neighborQuery, Collections.emptyMap());

        assertNotNull(result);

        // 中心节点应该有邻居
        assertTrue("中心节点应该有邻居", result.getNodes().size() > 1);
        assertTrue("应该有关系边", result.getEdges().size() > 0);

        // 验证邻居查询的准确性
        boolean hasCenterNode = result.getNodes().stream()
                .anyMatch(node -> node.getObjId().equals(nodeId));

        assertTrue("结果应该包含查询的中心节点", hasCenterNode);

        log.info("节点邻居查询测试通过，找到{}个节点，{}条边",
                result.getNodes().size(), result.getEdges().size());
    }

    /**
     * 测试完整图结构查询
     */
    @Test
    public void testGraphStructureQuery() {
        log.info("测试完整图结构查询");

        GraphQueryService graphService = S.service(GraphQueryService.class);

        // 查询测试数据的完整关系网络
        String graphQuery = """
                MATCH (n:ItemRevision)-[r:REFERENCE]-(m:ItemRevision) WHERE n.code =~ '%s' RETURN n, r, m
                """;
        GraphQueryService.GraphQueryResult result = graphService.executeGraphQuery(graphQuery.formatted(testPrefix), Collections.emptyMap());

        assertNotNull(result);
        assertNotNull(result.getNodes());
        assertNotNull(result.getEdges());

        log.info("图结构查询结果 - 节点数: {}, 边数: {}",
                result.getNodes().size(), result.getEdges().size());

        // 验证图结构的完整性
        if (!result.getEdges().isEmpty()) {
            // 检查边的源和目标节点是否都在节点列表中
            List<Long> nodeIds = result.getNodes().stream()
                    .map(GraphQueryService.GraphQueryResult.GraphNode::getId)
                    .toList();

            boolean allEdgesValid = result.getEdges().stream()
                    .allMatch(edge ->
                            nodeIds.contains(edge.getSource()) &&
                                    nodeIds.contains(edge.getTarget()));

            assertTrue("所有边的源和目标节点都应该在节点列表中", allEdgesValid);
        }

        log.info("完整图结构查询测试通过");
    }

    /**
     * 测试错误处理
     */
    @Test
    public void testErrorHandling() {
        log.info("执行错误处理测试");

        // 测试语法错误的查询
        String invalidQuery = "INVALID CYPHER SYNTAX";

        assertException(() -> {
            S.service(GraphQueryService.class).executeGraphQuery(invalidQuery, Collections.emptyMap());
        });

        log.info("错误处理测试完成");
    }
}