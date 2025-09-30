package io.emop.integrationtest.usecase.pipeline;

import io.emop.model.common.ItemRevision;
import io.emop.model.common.ModelObject;
import io.emop.model.common.UserContext;
import io.emop.model.query.Q;
import io.emop.model.query.tuple.Tuple2;
import io.emop.model.query.tuple.Tuple3;
import io.emop.model.relation.Relation;
import io.emop.integrationtest.util.TimerUtils;
import io.emop.service.S;
import io.emop.service.api.data.ObjectService;
import io.emop.service.api.data.ObjectServicePipeline;
import io.emop.service.api.domain.common.AssociateRelationService;
import io.emop.service.api.domain.common.AssociateRelationServicePipeline;
import io.emop.service.api.pipeline.CompositePipeline;
import io.emop.service.api.pipeline.ResultFuture;
import io.emop.service.api.relation.RelationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.emop.model.query.tuple.Tuples.tuple;
import static io.emop.integrationtest.util.Assertion.*;

/**
 * AssociateRelationService Pipeline测试用例
 * 演示关系服务的Pipeline功能和性能提升效果
 */
@RequiredArgsConstructor
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AssociateRelationServicePipelineTest {

    private static final String REV_ID = "50";
    private static final String DATE_CODE = String.valueOf(System.currentTimeMillis());
    private static final int BATCH_SIZE = 500;
    private static final RelationType TEST_RELATION_TYPE = new RelationType("TEST_RELATION", null, true);

    @BeforeAll
    public void setup() {
        UserContext.setCurrentUser(new UserContext(100L, List.of("admin")));
    }

    @Test
    @Order(1)
    public void testTraditionalVsPipelineRelations() {
        S.withStrongConsistency(this::testTraditionalVsPipelineRelationsImpl);
    }

    @Test
    @Order(2)
    public void testPipelineAppendRelations() {
        S.withStrongConsistency(this::testPipelineAppendRelationsImpl);
    }

    @Test
    @Order(3)
    public void testPipelineReplaceRelations() {
        S.withStrongConsistency(this::testPipelineReplaceRelationsImpl);
    }

    @Test
    @Order(4)
    public void testPipelineRemoveRelations() {
        S.withStrongConsistency(this::testPipelineRemoveRelationsImpl);
    }

    @Test
    @Order(5)
    public void testPipelineBatchQueries() {
        S.withStrongConsistency(this::testPipelineBatchQueriesImpl);
    }

    @Test
    @Order(6)
    public void testTupleQueryOptimization() {
        S.withStrongConsistency(this::testTupleQueryOptimizationImpl);
    }

    @Test
    @Order(7)
    public void testCompositePipelineWithRelations() {
        S.withStrongConsistency(this::testCompositePipelineWithRelationsImpl);
    }

    /**
     * 对比传统方式与Pipeline方式的关系操作性能
     */
    private void testTraditionalVsPipelineRelationsImpl() {
        log.info("=== 关系操作性能对比测试：传统方式 vs Pipeline方式 ===");

        // 准备测试数据
        List<ItemRevision> parents = prepareTestData(50, "parent");
        List<ItemRevision> children = prepareTestData(100, "child");

        ObjectService objectService = S.service(ObjectService.class);
        AssociateRelationService relationService = S.service(AssociateRelationService.class);

        List<ItemRevision> savedParents = objectService.saveAll(parents);
        List<ItemRevision> savedChildren = objectService.saveAll(children);

        // 传统方式：逐个创建关系
        long time1 = TimerUtils.measureExecutionTime("传统方式创建关系", () -> {
            for (int i = 0; i < savedParents.size(); i++) {
                ItemRevision parent = savedParents.get(i);
                // 每个parent关联2个child
                relationService.appendRelation(parent, TEST_RELATION_TYPE,
                        savedChildren.get(i * 2), savedChildren.get(i * 2 + 1));
            }
        }) / 1_000_000;

        // 清理关系
        cleanupRelations(savedParents);

        // Pipeline方式：批量创建关系
        long time2 = TimerUtils.measureExecutionTime("Pipeline批量创建关系", () -> {
            AssociateRelationServicePipeline pipeline = AssociateRelationService.pipeline();
            List<ResultFuture<List<Relation>>> futures = new ArrayList<>();

            for (int i = 0; i < savedParents.size(); i++) {
                ItemRevision parent = savedParents.get(i);
                List<ModelObject> childrenToAdd = List.of(savedChildren.get(i * 2), savedChildren.get(i * 2 + 1));

                ResultFuture<List<Relation>> future = pipeline.appendRelation(parent, TEST_RELATION_TYPE,
                        null, childrenToAdd);
                futures.add(future);
            }

            // 执行Pipeline
            pipeline.execute();

            // 验证结果
            int totalRelations = 0;
            for (ResultFuture<List<Relation>> future : futures) {
                try {
                    List<Relation> relations = future.get();
                    totalRelations += relations.size();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            assertEquals(savedParents.size() * 2, totalRelations);
        }) / 1_000_000;

        log.info("关系操作性能对比: 传统方式 {}ms, Pipeline方式 {}ms, 性能提升 {}倍",
                time1, time2, (double) time1 / time2);

        // 清理测试数据
        cleanupTestData(savedParents, savedChildren);
    }

    /**
     * 测试Pipeline的appendRelation功能
     */
    private void testPipelineAppendRelationsImpl() {
        log.info("=== Pipeline AppendRelation测试 ===");

        // 准备测试数据
        List<ItemRevision> parents = prepareTestData(20, "append-parent");
        List<ItemRevision> children = prepareTestData(40, "append-child");

        ObjectService objectService = S.service(ObjectService.class);
        List<ItemRevision> savedParents = objectService.saveAll(parents);
        List<ItemRevision> savedChildren = objectService.saveAll(children);

        TimerUtils.measureExecutionTime("Pipeline AppendRelation测试", () -> {
            AssociateRelationServicePipeline pipeline = AssociateRelationService.pipeline();
            List<ResultFuture<List<Relation>>> futures = new ArrayList<>();

            // 为每个parent添加2个child
            for (int i = 0; i < savedParents.size(); i++) {
                ItemRevision parent = savedParents.get(i);
                List<ModelObject> childrenToAdd = List.of(savedChildren.get(i * 2), savedChildren.get(i * 2 + 1));

                Map<String, Object> properties = new HashMap<>();
                properties.put("batchId", "test-batch-" + i);
                properties.put("priority", i % 3);

                ResultFuture<List<Relation>> future = pipeline.appendRelation(parent, TEST_RELATION_TYPE,
                        properties, childrenToAdd);
                futures.add(future);
            }

            // 执行Pipeline
            pipeline.execute();

            // 验证结果
            int totalRelations = 0;
            for (int i = 0; i < futures.size(); i++) {
                try {
                    List<Relation> relations = futures.get(i).get();
                    totalRelations += relations.size();

                    // 验证关系属性
                    for (Relation relation : relations) {
                        assertEquals("test-batch-" + i, relation.get_properties().get("batchId"));
                        assertEquals(i % 3, relation.get_properties().get("priority"));
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            assertEquals(savedParents.size() * 2, totalRelations);
            log.info("成功创建 {} 个关系", totalRelations);
        });

        // 清理测试数据
        cleanupTestData(savedParents, savedChildren);
    }

    /**
     * 测试Pipeline的replaceRelation功能
     */
    private void testPipelineReplaceRelationsImpl() {
        log.info("=== Pipeline ReplaceRelation测试 ===");

        // 准备测试数据
        List<ItemRevision> parents = prepareTestData(15, "replace-parent");
        List<ItemRevision> oldChildren = prepareTestData(30, "old-child");
        List<ItemRevision> newChildren = prepareTestData(45, "new-child");

        ObjectService objectService = S.service(ObjectService.class);
        AssociateRelationService relationService = S.service(AssociateRelationService.class);

        List<ItemRevision> savedParents = objectService.saveAll(parents);
        List<ItemRevision> savedOldChildren = objectService.saveAll(oldChildren);
        List<ItemRevision> savedNewChildren = objectService.saveAll(newChildren);

        // 先创建初始关系
        for (int i = 0; i < savedParents.size(); i++) {
            ItemRevision parent = savedParents.get(i);
            relationService.appendRelation(parent, TEST_RELATION_TYPE,
                    savedOldChildren.get(i * 2), savedOldChildren.get(i * 2 + 1));
        }

        TimerUtils.measureExecutionTime("Pipeline ReplaceRelation测试", () -> {
            AssociateRelationServicePipeline pipeline = AssociateRelationService.pipeline();
            List<ResultFuture<Void>> futures = new ArrayList<>();

            // 替换每个parent的关系
            for (int i = 0; i < savedParents.size(); i++) {
                ItemRevision parent = savedParents.get(i);
                List<ModelObject> newChildrenToAdd = List.of(
                        savedNewChildren.get(i * 3),
                        savedNewChildren.get(i * 3 + 1),
                        savedNewChildren.get(i * 3 + 2));

                Map<String, Object> properties = new HashMap<>();
                properties.put("replacedAt", System.currentTimeMillis());

                ResultFuture<Void> future = pipeline.replaceRelation(parent, TEST_RELATION_TYPE,
                        properties, newChildrenToAdd);
                futures.add(future);
            }

            // 执行Pipeline
            pipeline.execute();

            // 验证结果
            for (ResultFuture<Void> future : futures) {
                try {
                    future.get(); // 等待完成
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            // 验证关系已被替换
            for (ItemRevision parent : savedParents) {
                List<? extends ModelObject> children = relationService.findAllChildren(parent, TEST_RELATION_TYPE);
                assertEquals(3, children.size()); // 每个parent现在有3个child
            }

            log.info("成功替换 {} 个parent的关系", savedParents.size());
        });

        // 清理测试数据
        cleanupTestData(savedParents, savedOldChildren, savedNewChildren);
    }

    /**
     * 测试Pipeline的removeRelations功能
     */
    private void testPipelineRemoveRelationsImpl() {
        log.info("=== Pipeline RemoveRelations测试 ===");

        // 准备测试数据
        List<ItemRevision> parents = prepareTestData(10, "remove-parent");
        List<ItemRevision> children = prepareTestData(30, "remove-child");

        ObjectService objectService = S.service(ObjectService.class);
        AssociateRelationService relationService = S.service(AssociateRelationService.class);

        List<ItemRevision> savedParents = objectService.saveAll(parents);
        List<ItemRevision> savedChildren = objectService.saveAll(children);

        // 先创建关系
        for (int i = 0; i < savedParents.size(); i++) {
            ItemRevision parent = savedParents.get(i);
            relationService.appendRelation(parent, TEST_RELATION_TYPE,
                    savedChildren.get(i * 3), savedChildren.get(i * 3 + 1), savedChildren.get(i * 3 + 2));
        }

        TimerUtils.measureExecutionTime("Pipeline RemoveRelations测试", () -> {
            AssociateRelationServicePipeline pipeline = AssociateRelationService.pipeline();
            List<ResultFuture<Long>> futures = new ArrayList<>();

            // 移除每个parent的部分关系
            for (int i = 0; i < savedParents.size(); i++) {
                ItemRevision parent = savedParents.get(i);
                List<ModelObject> childrenToRemove = List.of(savedChildren.get(i * 3)); // 只移除第一个child

                ResultFuture<Long> future = pipeline.removeRelations(parent, TEST_RELATION_TYPE, childrenToRemove);
                futures.add(future);
            }

            // 执行Pipeline
            pipeline.execute();

            // 验证结果
            long totalRemoved = 0;
            for (ResultFuture<Long> future : futures) {
                try {
                    Long removed = future.get();
                    totalRemoved += removed;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            assertEquals((long) savedParents.size(), totalRemoved); // 每个parent移除了1个关系

            // 验证剩余关系
            for (ItemRevision parent : savedParents) {
                List<? extends ModelObject> children2 = relationService.findAllChildren(parent, TEST_RELATION_TYPE);
                assertEquals(2, children2.size()); // 每个parent现在有2个child
            }

            log.info("成功移除 {} 个关系", totalRemoved);
        });

        // 清理测试数据
        cleanupTestData(savedParents, savedChildren);
    }

    /**
     * 测试Pipeline的批量查询功能
     */
    private void testPipelineBatchQueriesImpl() {
        log.info("=== Pipeline批量查询测试 ===");

        // 准备测试数据
        List<ItemRevision> parents = prepareTestData(20, "query-parent");
        List<ItemRevision> children = prepareTestData(60, "query-child");

        ObjectService objectService = S.service(ObjectService.class);
        AssociateRelationService relationService = S.service(AssociateRelationService.class);

        List<ItemRevision> savedParents = objectService.saveAll(parents);
        List<ItemRevision> savedChildren = objectService.saveAll(children);

        // 先创建关系
        for (int i = 0; i < savedParents.size(); i++) {
            ItemRevision parent = savedParents.get(i);
            relationService.appendRelation(parent, TEST_RELATION_TYPE,
                    savedChildren.get(i * 3), savedChildren.get(i * 3 + 1), savedChildren.get(i * 3 + 2));
        }

        TimerUtils.measureExecutionTime("Pipeline批量查询测试", () -> {
            AssociateRelationServicePipeline pipeline = AssociateRelationService.pipeline();

            List<Long> parentIds = savedParents.stream().map(ModelObject::getId).collect(Collectors.toList());

            // 批量查询关系
            ResultFuture<Map<Long, List<ModelObject>>> childrenFuture = pipeline.findAllChildrenByPrimaryIds(
                    parentIds, List.of(TEST_RELATION_TYPE));

            // 执行Pipeline
            pipeline.execute();

            try {
                // 验证批量查询结果
                Map<Long, List<ModelObject>> childrenMap = childrenFuture.get();

                assertEquals(savedParents.size(), childrenMap.size());

                for (ItemRevision parent : savedParents) {
                    List<ModelObject> children2 = childrenMap.get(parent.getId());

                    assertNotNull(children2);
                    assertEquals(3, children2.size());
                }

                log.info("批量查询成功: {} 个parent的关系", savedParents.size());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // 清理测试数据
        cleanupTestData(savedParents, savedChildren);
    }

    /**
     * 测试Tuple查询优化
     */
    private void testTupleQueryOptimizationImpl() {
        log.info("=== Tuple查询优化测试 ===");

        // 准备测试数据
        List<ItemRevision> parents = prepareTestData(30, "tuple-parent");
        List<ItemRevision> children = prepareTestData(90, "tuple-child");

        ObjectService objectService = S.service(ObjectService.class);
        AssociateRelationService relationService = S.service(AssociateRelationService.class);

        List<ItemRevision> savedParents = objectService.saveAll(parents);
        List<ItemRevision> savedChildren = objectService.saveAll(children);

        // 先创建关系
        for (int i = 0; i < savedParents.size(); i++) {
            ItemRevision parent = savedParents.get(i);
            relationService.appendRelation(parent, TEST_RELATION_TYPE,
                    savedChildren.get(i * 3), savedChildren.get(i * 3 + 1), savedChildren.get(i * 3 + 2));
        }

        // 测试Tuple查询
        TimerUtils.measureExecutionTime("Tuple查询优化测试", () -> {
            // 1. 测试批量检查关系是否存在
            List<Tuple3<Long, String, Long>> relationTuples = new ArrayList<>();
            for (int i = 0; i < savedParents.size(); i++) {
                ItemRevision parent = savedParents.get(i);
                for (int j = 0; j < 3; j++) {
                    relationTuples.add(tuple(parent.getId(), TEST_RELATION_TYPE.getName(),
                            savedChildren.get(i * 3 + j).getId()));
                }
            }

            // 使用tuple查询批量检查关系存在性
            List<Relation> existingRelations = Q.result(Relation.class)
                    .whereTuples("primaryId, relationName, secondaryId", relationTuples)
                    .query();

            assertEquals(savedParents.size() * 3, existingRelations.size());

            // 2. 测试批量查询(primaryId, relationName)组合
            List<Tuple2<Long, String>> primaryRelationTuples = savedParents.stream()
                    .map(parent -> tuple(parent.getId(), TEST_RELATION_TYPE.getName()))
                    .collect(Collectors.toList());

            List<Relation> allRelations = Q.result(Relation.class)
                    .whereTuples("primaryId, relationName", primaryRelationTuples)
                    .query();

            assertEquals(savedParents.size() * 3, allRelations.size());

            // 3. 测试批量删除优化 - 先删除部分关系
            List<Tuple3<Long, String, Long>> deleteSpecificTuples = new ArrayList<>();
            for (int i = 0; i < 5; i++) { // 只删除前5个parent的第一个child关系
                ItemRevision parent = savedParents.get(i);
                deleteSpecificTuples.add(tuple(parent.getId(), TEST_RELATION_TYPE.getName(),
                        savedChildren.get(i * 3).getId()));
            }

            // 验证删除优化
            long deletedCount = Q.result(Relation.class)
                    .whereTuples("primaryId, relationName, secondaryId", deleteSpecificTuples)
                    .delete();

            assertEquals((long) 5, deletedCount);

            log.info("Tuple查询测试成功: 检查了 {} 个关系三元组, 查询了 {} 个关系二元组, 删除了 {} 个关系",
                    relationTuples.size(), primaryRelationTuples.size(), deletedCount);
        });

        // 清理测试数据
        cleanupTestData(savedParents, savedChildren);
    }

    /**
     * 测试CompositePipeline跨服务协调
     */
    private void testCompositePipelineWithRelationsImpl() {
        log.info("=== CompositePipeline跨服务协调测试 ===");

        TimerUtils.measureExecutionTime("CompositePipeline跨服务协调测试", () -> {
            // 准备数据
            List<ItemRevision> parents = prepareTestData(10, "composite-parent");
            List<ItemRevision> children = prepareTestData(30, "composite-child");

            // 创建不同服务的Pipeline
            ObjectServicePipeline objectPipeline = ObjectService.pipeline();
            AssociateRelationServicePipeline relationPipeline = AssociateRelationService.pipeline();

            // 1. 保存对象
            List<ResultFuture<ModelObject>> parentFutures = parents.stream()
                    .map(objectPipeline::save)
                    .collect(Collectors.toList());

            List<ResultFuture<ModelObject>> childFutures = children.stream()
                    .map(objectPipeline::save)
                    .collect(Collectors.toList());

            // 2. 创建关系(需要等待对象保存完成)
            List<ResultFuture<List<Relation>>> relationFutures = new ArrayList<>();
            for (int i = 0; i < parents.size(); i++) {
                List<ModelObject> childrenToAdd = List.of(children.get(i * 3), children.get(i * 3 + 1), children.get(i * 3 + 2));

                ResultFuture<List<Relation>> future = relationPipeline.appendRelation(
                        parents.get(i), TEST_RELATION_TYPE, null, childrenToAdd);
                relationFutures.add(future);
            }

            // 3. 使用CompositePipeline按顺序执行
            CompositePipeline.of(objectPipeline, relationPipeline)
                    .sequential()  // 确保先保存对象，再创建关系
                    .execute();

            // 4. 验证结果
            List<ModelObject> savedParents = parentFutures.stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.toList());

            List<ModelObject> savedChildren = childFutures.stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.toList());

            int totalRelations = relationFutures.stream()
                    .mapToInt(future -> {
                        try {
                            return future.get().size();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .sum();

            assertEquals(parents.size(), savedParents.size());
            assertEquals(children.size(), savedChildren.size());
            assertEquals(parents.size() * 3, totalRelations);

            log.info("CompositePipeline测试成功: 保存了 {} 个parent, {} 个child, 创建了 {} 个关系",
                    savedParents.size(), savedChildren.size(), totalRelations);

            // 清理测试数据
            cleanupTestData(savedParents, savedChildren);
        });
    }

    // 工具方法

    private List<ItemRevision> prepareTestData(int count, String prefix) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(idx -> {
                    ItemRevision rev = ItemRevision.newModel(prefix + "-" + DATE_CODE + "-" + idx, REV_ID);
                    rev.setName("Pipeline关系测试对象-" + prefix + "-" + idx);
                    return rev;
                })
                .collect(Collectors.toList());
    }

    private void cleanupRelations(List<ItemRevision> parents) {
        AssociateRelationService relationService = S.service(AssociateRelationService.class);
        for (ItemRevision parent : parents) {
            relationService.removeRelations(parent, TEST_RELATION_TYPE);
        }
    }

    private void cleanupTestData(List<?>... objectLists) {
        ObjectService objectService = S.service(ObjectService.class);
        for (List<?> objects : objectLists) {
            if (objects != null && !objects.isEmpty()) {
                List<Long> ids = objects.stream()
                        .filter(obj -> obj instanceof ModelObject)
                        .map(obj -> ((ModelObject) obj).getId())
                        .collect(Collectors.toList());
                if (!ids.isEmpty()) {
                    objectService.forceDelete(ids);
                }
            }
        }
    }
}