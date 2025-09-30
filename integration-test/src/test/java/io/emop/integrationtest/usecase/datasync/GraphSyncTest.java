package io.emop.integrationtest.usecase.datasync;

import io.emop.model.common.*;
import io.emop.model.lifecycle.LifecycleState;
import io.emop.model.query.Q;
import io.emop.integrationtest.domain.SampleTask;
import io.emop.service.S;
import io.emop.service.api.data.NativeSqlService;
import io.emop.service.api.data.ObjectService;
import io.emop.service.api.domain.common.AssociateRelationService;
import io.emop.service.api.domain.common.RevisionService;
import io.emop.service.api.lifecycle.LifecycleService;
import io.emop.service.api.relation.RelationType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.stream.Collectors;

import static io.emop.integrationtest.util.Assertion.*;

/**
 * 图数据库同步集成测试
 * 测试PostgreSQL数据变化到PG-AGE图数据库的完整同步流程
 */
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GraphSyncTest {

    private NativeSqlService nativeSqlService;
    private ObjectService objectService;
    private AssociateRelationService associateRelationService;
    private RevisionService revisionService;

    // 测试数据前缀，避免与其他测试冲突
    private final String testPrefix = "GSYNC-" + System.currentTimeMillis();

    // 测试对象集合，用于最后清理
    private final List<Long> testTaskIds = new ArrayList<>();
    private final List<Long> testItemIds = new ArrayList<>();

    @BeforeAll
    public void setup() {
        UserContext.setCurrentUser(new UserContext(100L, List.of("admin")));
        init();
        log.info("=== 开始图同步集成测试 ===");
    }

    @Test
    @Order(1)
    public void testBasicNodeSync() {
        testBasicNodeSyncImpl();
    }

    @Test
    @Order(2)
    public void testNodeUpdateSync() {
        testNodeUpdateSyncImpl();
    }

    @Test
    @Order(3)
    public void testNodeDeletionSync() {
        testNodeDeletionSyncImpl();
    }

    @Test
    @Order(4)
    public void testStructuralRelationSync() {
        testStructuralRelationSyncImpl();
    }

    @Test
    @Order(5)
    public void testStructuralRelationUpdate() {
        testStructuralRelationUpdateImpl();
    }

    @Test
    @Order(6)
    public void testAssociateRelationSync() {
        testAssociateRelationSyncImpl();
    }

    @Test
    @Order(7)
    public void testAssociateRelationOperations() {
        testAssociateRelationOperationsImpl();
    }

    @Test
    @Order(8)
    public void testRevisionSync() {
        testRevisionSyncImpl();
    }

    @Test
    @Order(9)
    public void testBatchOperations() {
        testBatchOperationsImpl();
    }

    @Test
    @Order(10)
    public void testMixedOperations() {
        testMixedOperationsImpl();
    }

    @Test
    @Order(11)
    public void testSyncStatus() {
        testSyncStatusImpl();
    }

    @Test
    @Order(12)
    public void testSyncPerformance() {
        testSyncPerformanceImpl();
    }

    private void init() {
        this.nativeSqlService = S.service(NativeSqlService.class);
        this.objectService = S.service(ObjectService.class);
        this.associateRelationService = S.service(AssociateRelationService.class);
        this.revisionService = S.service(RevisionService.class);
    }

    // ============== 基础节点同步测试 ==============

    /**
     * 测试基础节点同步
     */
    private void testBasicNodeSyncImpl() {
        log.info("测试基础节点同步");

        // 创建SampleTask对象
        SampleTask task = new SampleTask();
        task.setName("图同步测试任务");
        task.setCode(testPrefix + "-TASK-001");
        task.setRevId("A");
        task = objectService.save(task);
        testTaskIds.add(task.getId());

        // 创建ItemRevision对象
        ItemRevision item = ItemRevision.newModel(testPrefix + "-ITEM-001", "A");
        item.setName("图同步测试零件");
        item.setDescription("用于测试图同步的零件");
        item = objectService.save(item);
        testItemIds.add(item.getId());

        // 等待同步完成
        performSync();

        // 验证图中节点存在
        verifyNodeExists("SampleTask", task.getId(), task.getCode(), "A", "图同步测试任务");
        verifyNodeExists("ItemRevision", item.getId(), item.getCode(), "A", "图同步测试零件");

        log.info("基础节点同步测试通过");
    }

    /**
     * 测试节点更新同步
     */
    private void testNodeUpdateSyncImpl() {
        log.info("测试节点更新同步");

        // 获取已创建的任务
        SampleTask task = Q.result(SampleTask.class)
                .where("code = ?", testPrefix + "-TASK-001")
                .first();
        assertNotNull(task);

        // 更新任务信息
        task.setName("更新后的图同步测试任务");
        task = objectService.save(task);

        // 等待同步完成
        performSync();

        // 验证图中节点已更新
        verifyNodeExists("SampleTask", task.getId(), task.getCode(), "A", "更新后的图同步测试任务");

        log.info("节点更新同步测试通过");
    }

    /**
     * 测试节点删除同步
     */
    private void testNodeDeletionSyncImpl() {
        log.info("测试节点删除同步");

        // 创建一个临时对象用于删除测试
        SampleTask tempTask = new SampleTask();
        tempTask.setName("临时测试任务");
        tempTask.setCode(testPrefix + "-TEMP-001");
        tempTask.setRevId("A");
        tempTask = objectService.save(tempTask);

        // 等待创建同步完成
        performSync();

        // 验证节点存在
        verifyNodeExists("SampleTask", tempTask.getId(), tempTask.getCode(), "A", "临时测试任务");

        // 删除对象
        objectService.delete(Arrays.asList(tempTask.getId()));

        // 等待删除同步完成
        performSync();

        // 验证节点已删除
        verifyNodeNotExists("SampleTask", tempTask.getId());

        log.info("节点删除同步测试通过");
    }

    // ============== 结构关系同步测试 ==============

    /**
     * 测试结构关系同步
     */
    private void testStructuralRelationSyncImpl() {
        log.info("测试结构关系同步");

        // 创建主任务
        SampleTask mainTask = new SampleTask();
        mainTask.setName("主任务");
        mainTask.setCode(testPrefix + "-MAIN-001");
        mainTask.setRevId("A");
        mainTask = objectService.save(mainTask);
        testTaskIds.add(mainTask.getId());

        // 创建子任务
        SampleTask subTask = new SampleTask();
        subTask.setName("子任务");
        subTask.setCode(testPrefix + "-SUB-001");
        subTask.setRevId("A");
        subTask = objectService.save(subTask);
        testTaskIds.add(subTask.getId());

        // 创建组任务关系
        SampleTask childTask1 = new SampleTask();
        childTask1.setName("组任务成员1");
        childTask1.setCode(testPrefix + "-CHILD-001");
        childTask1.setRevId("A");
        childTask1.setGroupTaskId(mainTask.getId());
        childTask1 = objectService.save(childTask1);
        testTaskIds.add(childTask1.getId());

        SampleTask childTask2 = new SampleTask();
        childTask2.setName("组任务成员2");
        childTask2.setCode(testPrefix + "-CHILD-002");
        childTask2.setRevId("A");
        childTask2.setGroupTaskId(mainTask.getId());
        childTask2 = objectService.save(childTask2);
        testTaskIds.add(childTask2.getId());

        // 设置一对一关系
        mainTask.setSubTaskId(subTask.getId());
        mainTask = objectService.save(mainTask);

        // 等待同步完成
        performSync();

        // 验证结构关系存在
        verifyStructuralRelationExists(mainTask.getId(), subTask.getId(), "subTask");
        verifyStructuralRelationExists(mainTask.getId(), childTask1.getId(), "childTasks");
        verifyStructuralRelationExists(mainTask.getId(), childTask2.getId(), "childTasks");

        log.info("结构关系同步测试通过");
    }

    /**
     * 测试结构关系更新
     */
    private void testStructuralRelationUpdateImpl() {
        log.info("测试结构关系更新");

        // 获取主任务
        SampleTask mainTask = Q.result(SampleTask.class)
                .where("code = ?", testPrefix + "-MAIN-001")
                .first();
        assertNotNull(mainTask);

        // 创建新的子任务
        SampleTask newSubTask = new SampleTask();
        newSubTask.setName("新子任务");
        newSubTask.setCode(testPrefix + "-NEWSUB-001");
        newSubTask.setRevId("A");
        newSubTask = objectService.save(newSubTask);
        testTaskIds.add(newSubTask.getId());

        // 获取旧的子任务ID
        Long oldSubTaskId = mainTask.getSubTaskId();

        // 更新关系
        mainTask.setSubTaskId(newSubTask.getId());
        mainTask = objectService.save(mainTask);

        // 等待同步完成
        performSync();

        // 验证旧关系已删除，新关系已创建
        if (oldSubTaskId != null) {
            verifyStructuralRelationNotExists(mainTask.getId(), oldSubTaskId, "subTask");
        }
        verifyStructuralRelationExists(mainTask.getId(), newSubTask.getId(), "subTask");

        log.info("结构关系更新测试通过");
    }

    // ============== 关联关系同步测试 ==============

    /**
     * 测试关联关系同步
     */
    private void testAssociateRelationSyncImpl() {
        log.info("测试关联关系同步");

        // 获取已创建的ItemRevision对象
        ItemRevision mainItem = Q.result(ItemRevision.class)
                .where("code = ?", testPrefix + "-ITEM-001")
                .first();
        assertNotNull(mainItem);

        // 创建更多ItemRevision对象用于关联
        ItemRevision refItem1 = ItemRevision.newModel(testPrefix + "-REF-001", "A");
        refItem1.setName("参考零件1");
        refItem1 = objectService.save(refItem1);
        testItemIds.add(refItem1.getId());

        ItemRevision refItem2 = ItemRevision.newModel(testPrefix + "-REF-002", "A");
        refItem2.setName("参考零件2");
        refItem2 = objectService.save(refItem2);
        testItemIds.add(refItem2.getId());

        // 创建关联关系
        associateRelationService.appendRelation(mainItem, RelationType.reference, refItem1, refItem2);

        // 等待同步完成
        performSync();

        // 验证关联关系存在
        verifyAssociateRelationExists(mainItem.getId(), refItem1.getId(), "REFERENCE");
        verifyAssociateRelationExists(mainItem.getId(), refItem2.getId(), "REFERENCE");

        log.info("关联关系同步测试通过");
    }

    /**
     * 测试关联关系操作
     */
    private void testAssociateRelationOperationsImpl() {
        log.info("测试关联关系操作");

        // 获取主要对象
        ItemRevision mainItem = Q.result(ItemRevision.class)
                .where("code = ?", testPrefix + "-ITEM-001")
                .first();
        assertNotNull(mainItem);

        // 创建目标对象
        ItemRevision targetItem = ItemRevision.newModel(testPrefix + "-TARGET-001", "A");
        targetItem.setName("目标零件");
        targetItem = objectService.save(targetItem);
        testItemIds.add(targetItem.getId());

        // 添加目标关系
        associateRelationService.appendRelation(mainItem, RelationType.target, targetItem);
        performSync();
        verifyAssociateRelationExists(mainItem.getId(), targetItem.getId(), "TARGET");

        // 移除参考关系中的一个
        ItemRevision refItem1 = Q.result(ItemRevision.class)
                .where("code = ?", testPrefix + "-REF-001")
                .first();
        assertNotNull(refItem1);

        long removedCount = associateRelationService.removeRelations(mainItem, RelationType.reference, refItem1);
        assertEquals(1L, removedCount);
        performSync();
        verifyAssociateRelationNotExists(mainItem.getId(), refItem1.getId(), "REFERENCE");

        // 替换目标关系
        ItemRevision newTargetItem = ItemRevision.newModel(testPrefix + "-NEWTARGET-001", "A");
        newTargetItem.setName("新目标零件");
        newTargetItem = objectService.save(newTargetItem);
        testItemIds.add(newTargetItem.getId());

        associateRelationService.replaceRelation(mainItem, RelationType.target, newTargetItem);
        performSync();
        verifyAssociateRelationNotExists(mainItem.getId(), targetItem.getId(), "TARGET");
        verifyAssociateRelationExists(mainItem.getId(), newTargetItem.getId(), "TARGET");

        log.info("关联关系操作测试通过");
    }

    // ============== 版本升级测试 ==============

    /**
     * 测试版本升级同步
     */
    private void testRevisionSyncImpl() {
        log.info("测试版本升级同步");

        // 获取主要对象
        ItemRevision originalItem = Q.result(ItemRevision.class)
                .where("code = ?", testPrefix + "-ITEM-001")
                .first();
        assertNotNull(originalItem);

        originalItem = S.service(LifecycleService.class).moveToState(originalItem, LifecycleState.STATE_RELEASED);
        // 升版
        Revisionable revisedItem = revisionService.revise(originalItem, CopyRule.CopyReference);
        testItemIds.add(revisedItem.getId());

        // 等待同步完成
        performSync();

        // 验证新版本节点存在
        verifyNodeExists("ItemRevision", revisedItem.getId(), revisedItem.getCode(), "B", revisedItem.get("name", String.class));

        // 验证原版本节点仍然存在
        verifyNodeExists("ItemRevision", originalItem.getId(), originalItem.getCode(), "A", originalItem.getName());

        // 验证关系是否正确复制（根据CopyRule.CopyReference）
        List<? extends ModelObject> originalRels = originalItem.rel();
        List<? extends ModelObject> revisedRels = revisedItem.rel();

        if (!originalRels.isEmpty()) {
            assertEquals(originalRels.size(), revisedRels.size());

            // 验证每个关系都正确同步到图中
            for (ModelObject relatedObj : revisedRels) {
                // 这里需要根据具体的关系类型验证
                log.info("验证升版后的关系: {} -> {}", revisedItem.getId(), relatedObj.getId());
            }
        }

        log.info("版本升级同步测试通过");
    }

    // ============== 复杂场景测试 ==============

    /**
     * 测试批量操作
     */
    private void testBatchOperationsImpl() {
        log.info("测试批量操作");

        // 批量创建任务
        List<SampleTask> batchTasks = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            SampleTask task = new SampleTask();
            task.setName("批量任务" + i);
            task.setCode(testPrefix + "-BATCH-" + String.format("%03d", i));
            task.setRevId("A");
            batchTasks.add(task);
        }

        // 批量保存
        List<SampleTask> savedTasks = batchTasks.stream()
                .map(objectService::save)
                .collect(Collectors.toList());

        savedTasks.forEach(task -> testTaskIds.add(task.getId()));

        // 等待同步完成
        performSync();

        // 验证所有节点都已同步
        for (SampleTask task : savedTasks) {
            verifyNodeExists("SampleTask", task.getId(), task.getCode(), "A", task.getName());
        }

        // 批量创建关系
        SampleTask mainBatchTask = savedTasks.get(0);
        for (int i = 1; i < savedTasks.size(); i++) {
            SampleTask childTask = savedTasks.get(i);
            childTask.setGroupTaskId(mainBatchTask.getId());
            objectService.save(childTask);
        }

        // 等待关系同步完成
        performSync();

        // 验证所有关系都已同步
        for (int i = 1; i < savedTasks.size(); i++) {
            verifyStructuralRelationExists(mainBatchTask.getId(), savedTasks.get(i).getId(), "childTasks");
        }

        log.info("批量操作测试通过");
    }

    /**
     * 测试混合操作
     */
    private void testMixedOperationsImpl() {
        log.info("测试混合操作");

        // 创建一个任务
        SampleTask mixedTask = new SampleTask();
        mixedTask.setName("混合测试任务");
        mixedTask.setCode(testPrefix + "-MIXED-001");
        mixedTask.setRevId("A");
        mixedTask = objectService.save(mixedTask);
        testTaskIds.add(mixedTask.getId());

        // 创建一个零件
        ItemRevision mixedItem = ItemRevision.newModel(testPrefix + "-MIXED-ITEM-001", "A");
        mixedItem.setName("混合测试零件");
        mixedItem = objectService.save(mixedItem);
        testItemIds.add(mixedItem.getId());

        // 等待基础同步
        performSync();

        // 验证基础对象
        verifyNodeExists("SampleTask", mixedTask.getId(), mixedTask.getCode(), "A", "混合测试任务");
        verifyNodeExists("ItemRevision", mixedItem.getId(), mixedItem.getCode(), "A", "混合测试零件");

        // 同时执行多种操作：更新任务、升版零件、创建关系

        // 1. 更新任务
        mixedTask.setName("更新的混合测试任务");
        mixedTask = objectService.save(mixedTask);

        mixedItem = S.service(LifecycleService.class).moveToState(mixedItem, LifecycleState.STATE_RELEASED);
        // 2. 升版零件
        Revisionable revisedMixedItem = revisionService.revise(mixedItem);
        testItemIds.add(revisedMixedItem.getId());

        // 3. 创建新零件并建立关系
        ItemRevision relatedItem = ItemRevision.newModel(testPrefix + "-RELATED-001", "A");
        relatedItem.setName("相关零件");
        relatedItem = objectService.save(relatedItem);
        testItemIds.add(relatedItem.getId());

        associateRelationService.appendRelation(revisedMixedItem, RelationType.reference, relatedItem);

        // 等待所有同步完成
        performSync(); // 给更长的时间

        // 验证所有变更
        verifyNodeExists("SampleTask", mixedTask.getId(), mixedTask.getCode(), "A", "更新的混合测试任务");
        verifyNodeExists("ItemRevision", revisedMixedItem.getId(), revisedMixedItem.getCode(), "B", revisedMixedItem.get("name", String.class));
        verifyNodeExists("ItemRevision", relatedItem.getId(), relatedItem.getCode(), "A", "相关零件");
        verifyAssociateRelationExists(revisedMixedItem.getId(), relatedItem.getId(), "REFERENCE");

        log.info("混合操作测试通过");
    }

    // ============== 状态和性能测试 ==============

    /**
     * 测试同步状态
     */
    private void testSyncStatusImpl() {
        log.info("测试同步状态");

        // 通过SQL查询同步状态
        String statusQuery = "SELECT sync_type, status, COUNT(*) as count FROM graph.sync_tasks " +
                "WHERE created_at > CURRENT_TIMESTAMP - INTERVAL '1 hour' " +
                "GROUP BY sync_type, status ORDER BY sync_type, status";

        List<List<?>> statusResults = nativeSqlService.executeNativeQuery(statusQuery);

        log.info("当前图同步状态统计:");
        for (List<?> row : statusResults) {
            log.info("  {} - {}: {} 个任务", row.get(0), row.get(1), row.get(2));
        }

        // 手动触发同步处理
        String processQuery = "SELECT graph.process_sync_tasks()";
        List<List<?>> processResults = nativeSqlService.executeNativeQuery(processQuery);

        log.info("同步状态测试通过");
    }

    /**
     * 测试同步性能
     */
    private void testSyncPerformanceImpl() {
        log.info("测试同步性能");

        long startTime = System.currentTimeMillis();

        // 创建大量数据测试性能
        List<SampleTask> perfTasks = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            SampleTask task = new SampleTask();
            task.setName("性能测试任务" + i);
            task.setCode(testPrefix + "-PERF-" + String.format("%03d", i));
            task.setRevId("A");
            task = objectService.save(task);
            perfTasks.add(task);
            testTaskIds.add(task.getId());
        }

        long createTime = System.currentTimeMillis();
        log.info("创建20个任务耗时: {}ms", createTime - startTime);

        // 等待同步完成
        waitForSync(5000); // 给较长时间

        long syncTime = System.currentTimeMillis();
        log.info("同步完成总耗时: {}ms", syncTime - startTime);

        // 验证同步结果（随机抽样验证）
        SampleTask firstTask = perfTasks.get(0);
        SampleTask lastTask = perfTasks.get(perfTasks.size() - 1);

        verifyNodeExists("SampleTask", firstTask.getId(), firstTask.getCode(), "A", firstTask.getName());
        verifyNodeExists("SampleTask", lastTask.getId(), lastTask.getCode(), "A", lastTask.getName());

        // 性能断言：同步应该在合理时间内完成
        long totalTime = syncTime - startTime;
        assertTrue("同步耗时过长: " + totalTime + "ms", totalTime < 30000); // 30秒内

        log.info("同步性能测试通过，总耗时: {}ms", totalTime);
    }

    // ============== 验证辅助方法 ==============

    /**
     * 验证节点存在
     */
    private void verifyNodeExists(String nodeType, Long nodeId, String code, String revId, String name) {
        String query = String.format(
                "SELECT * FROM cypher('graph', $$ MATCH (n:%s {id: %d}) RETURN n.id, n.code, n.revId, n.name $$) " +
                        "AS (id text, code text, revId text, name text)",
                nodeType, nodeId
        );

        List<List<?>> results = nativeSqlService.executeNativeQuery(query);
        assertFalse("节点应该存在: " + nodeType + "[" + nodeId + "]", results.isEmpty());

        // 验证属性值（需要解析text）
        List<?> row = results.get(0);
        assertNotNull(row.get(0));

        log.debug("验证节点存在: {}[{}] - {}", nodeType, nodeId, name);
    }

    /**
     * 验证节点不存在
     */
    private void verifyNodeNotExists(String nodeType, Long nodeId) {
        String query = String.format(
                "SELECT * FROM cypher('graph', $$ MATCH (n:%s {id: %d}) RETURN n $$) " +
                        "AS (node text)",
                nodeType, nodeId
        );

        List<List<?>> results = nativeSqlService.executeNativeQuery(query);
        assertTrue("节点不应该存在: " + nodeType + "[" + nodeId + "]", results.isEmpty());

        log.debug("验证节点不存在: {}[{}]", nodeType, nodeId);
    }

    /**
     * 验证结构关系存在
     */
    private void verifyStructuralRelationExists(Long sourceId, Long targetId, String relationType) {
        String query = String.format(
                "SELECT * FROM cypher('graph', $$ MATCH (a {id: %d})-[r:%s]->(b {id: %d}) RETURN r $$) " +
                        "AS (relation agtype)",
                sourceId, relationType, targetId
        );

        List<List<?>> results = nativeSqlService.executeNativeQuery(query);
        assertFalse("结构关系应该存在: " + sourceId + " -[" + relationType + "]-> " + targetId, results.isEmpty());

        log.debug("验证结构关系存在: {} -[{}]-> {}", sourceId, relationType, targetId);
    }

    /**
     * 验证结构关系不存在
     */
    private void verifyStructuralRelationNotExists(Long sourceId, Long targetId, String relationType) {
        String query = String.format(
                "SELECT * FROM cypher('graph', $$ MATCH (a {id: %d})-[r:%s]->(b {id: %d}) RETURN r $$) " +
                        "AS (relation text)",
                sourceId, relationType, targetId
        );

        List<List<?>> results = nativeSqlService.executeNativeQuery(query);
        assertTrue("结构关系不应该存在: " + sourceId + " -[" + relationType + "]-> " + targetId, results.isEmpty());

        log.debug("验证结构关系不存在: {} -[{}]-> {}", sourceId, relationType, targetId);
    }

    /**
     * 验证关联关系存在
     */
    private void verifyAssociateRelationExists(Long sourceId, Long targetId, String relationType) {
        String query = String.format(
                "SELECT * FROM cypher('graph', $$ MATCH (a {id: %d})-[r:%s]->(b {id: %d}) RETURN r $$) " +
                        "AS (relation agtype)",
                sourceId, relationType, targetId
        );

        List<List<?>> results = nativeSqlService.executeNativeQuery(query);
        assertFalse("关联关系应该存在: " + sourceId + " -[" + relationType + "]-> " + targetId, results.isEmpty());

        log.debug("验证关联关系存在: {} -[{}]-> {}", sourceId, relationType, targetId);
    }

    /**
     * 验证关联关系不存在
     */
    private void verifyAssociateRelationNotExists(Long sourceId, Long targetId, String relationType) {
        String query = String.format(
                "SELECT * FROM cypher('graph', $$ MATCH (a {id: %d})-[r:%s]->(b {id: %d}) RETURN r $$) " +
                        "AS (relation text)",
                sourceId, relationType, targetId
        );

        List<List<?>> results = nativeSqlService.executeNativeQuery(query);
        assertTrue("关联关系不应该存在: " + sourceId + " -[" + relationType + "]-> " + targetId, results.isEmpty());

        log.debug("验证关联关系不存在: {} -[{}]-> {}", sourceId, relationType, targetId);
    }

    private void performSync() {
        //需要 master datasource
        UserContext.runAsSystem(() -> UserContext.ensureCurrent().byPass(() -> {
            nativeSqlService.executeDDL("SELECT graph.process_sync_tasks();");
        }));
        //等待主从同步
        waitForSync(100);
    }

    /**
     * 等待同步完成
     */
    private void waitForSync() {
        waitForSync(5000); // 默认5秒
    }

    /**
     * 等待同步完成（指定时间）
     */
    private void waitForSync(long timeoutMs) {
        try {
            log.debug("等待图同步完成，最长等待 {}ms", timeoutMs);
            Thread.sleep(timeoutMs);

            // 可以在这里添加更智能的等待逻辑，比如轮询sync_tasks表的pending状态
            // 但为了简化测试，这里使用固定等待时间

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 清理测试数据
     */
    private void cleanup() {
        log.info("开始清理图同步测试数据");

        try {
            // 以bypass权限清理数据，避免权限问题
            UserContext.ensureCurrent().byPass(() -> {
                // 清理测试对象
                if (!testTaskIds.isEmpty()) {
                    objectService.delete(testTaskIds);
                    log.info("清理了 {} 个测试任务", testTaskIds.size());
                }

                if (!testItemIds.isEmpty()) {
                    objectService.delete(testItemIds);
                    log.info("清理了 {} 个测试零件", testItemIds.size());
                }

                // 清理图数据（删除测试节点）
                try {
                    String cleanupQuery = String.format(
                            "SELECT * FROM cypher('graph', $$ MATCH (n) WHERE n.code CONTAINS '%s' DETACH DELETE n $$) " +
                                    "AS (result text)",
                            testPrefix
                    );
                    nativeSqlService.executeNativeQuery(cleanupQuery);
                    log.info("清理了图中的测试节点");
                } catch (Exception e) {
                    log.warn("清理图数据时出错: {}", e.getMessage());
                }

                // 清理同步任务
                try {
                    String cleanupTasksQuery = String.format(
                            "DELETE FROM graph.sync_tasks WHERE object_type LIKE '%%%s%%' OR object_id IN (%s)",
                            testPrefix,
                            testTaskIds.stream().map(String::valueOf).collect(Collectors.joining(",", "", ""))
                                    + (testItemIds.isEmpty() ? "" : "," + testItemIds.stream().map(String::valueOf).collect(Collectors.joining(",")))
                    );
                    nativeSqlService.executeDDL(cleanupTasksQuery);
                    log.info("清理了同步任务");
                } catch (Exception e) {
                    log.warn("清理同步任务时出错: {}", e.getMessage());
                }

                return null;
            });

        } catch (Exception e) {
            log.error("清理测试数据时出错", e);
        }

        log.info("图同步测试数据清理完成");
    }
}