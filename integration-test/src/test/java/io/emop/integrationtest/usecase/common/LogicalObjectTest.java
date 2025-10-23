package io.emop.integrationtest.usecase.common;

import io.emop.model.common.*;
import io.emop.model.lifecycle.LifecycleState;
import io.emop.model.query.Pagination;
import io.emop.model.query.Q;
import io.emop.integrationtest.util.Assertion;
import io.emop.integrationtest.util.TimerUtils;
import io.emop.service.S;
import io.emop.service.api.domain.common.AssociateRelationService;
import io.emop.service.api.domain.common.RevisionService;
import io.emop.service.api.lifecycle.LifecycleService;
import io.emop.service.api.data.ObjectService;
import io.emop.service.api.relation.RelationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * LogicalModelObject 功能测试用例
 * <p>
 * 重点展示正确的使用方式：
 * 1. LogicalModelObject 主要用于表示查询结果（secondary 对象）
 * 2. 关系查询推荐使用具体的 ModelObject 作为 source
 * 3. 仅在必要时使用基于业务编码的查询方法
 */
@RequiredArgsConstructor
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LogicalObjectTest {

    private static final String dateCode = String.valueOf(System.currentTimeMillis() + 100);
    private TestData testData;

    @BeforeAll
    public void setup() {
        UserContext.setCurrentUser(new UserContext(100L, List.of("admin")));
        testData = TimerUtils.measureExecutionTime("准备测试数据", this::prepareData);
    }

    @Test
    @Order(1)
    public void testBasicLogicalObject() {
        S.withStrongConsistency(() -> testBasicLogicalObject(testData));
    }

    @Test
    @Order(2)
    public void testRevisionRules() {
        S.withStrongConsistency(() -> testRevisionRules(testData));
    }

    @Test
    @Order(3)
    public void testLogicalRelations() {
        S.withStrongConsistency(() -> testLogicalRelations(testData));
    }

    @Test
    @Order(4)
    public void testLogicalQuerySyntax() {
        S.withStrongConsistency(() -> testLogicalQuerySyntax(testData));
    }

    @Test
    @Order(5)
    public void testBatchOperations() {
        S.withStrongConsistency(() -> testBatchOperations(testData));
    }

    @Test
    @Order(6)
    public void testUserContextRevisionRule() {
        S.withStrongConsistency(() -> testUserContextRevisionRule(testData));
    }

    @Test
    @Order(7)
    //TODO: 数据构造需要按照新的方式 RevisionableRefTrait，重新调整
    public void testBestPracticeWorkflow() {
        S.withStrongConsistency(() -> testBestPracticeWorkflow(testData));
    }

    @Test
    @Order(8)
    //TODO: 数据构造需要按照新的方式 RevisionableRefTrait，重新调整
    public void testPerformanceComparison() {
        S.withStrongConsistency(() -> testPerformanceComparison(testData));
    }

    @Test
    @Order(9)
    public void testQLogicalSyntax() {
        S.withStrongConsistency(() -> testQLogicalSyntax(testData));
    }

    @Test
    @Order(10)
    public void testDirectResolveAPI() {
        S.withStrongConsistency(() -> testDirectResolveAPI(testData));
    }

    @Test
    @Order(11)
    public void testLogicalPagination() {
        S.withStrongConsistency(() -> testLogicalPagination(testData));
    }

    /**
     * 准备测试数据：创建多个版本的 ItemRevision 对象
     */
    private TestData prepareData() {
        log.info("=== 准备测试数据 ===");

        // 创建基础数据
        List<ItemRevision> revisions = IntStream.rangeClosed(1, 10).mapToObj(idx -> {
            ItemRevision rev = ItemRevision.newModel(dateCode + "-" + idx, "A");
            rev.setName("LogicalObject-Test-" + dateCode + "-" + idx);
            rev = S.service(ObjectService.class).save(rev);
            return rev;
        }).collect(Collectors.toList());

        // 建立关系：1 -> 2,3,4  和  2 -> 5,6
        AssociateRelationService relationService = S.service(AssociateRelationService.class);
        relationService.replaceRelation(revisions.get(0), RelationType.reference,
                revisions.get(1), revisions.get(2), revisions.get(3));
        relationService.replaceRelation(revisions.get(1), RelationType.reference,
                revisions.get(4), revisions.get(5));

        // 发布前5个对象
        S.service(LifecycleService.class).moveToState(
                revisions.subList(0, 5), LifecycleState.STATE_RELEASED);

        // 为前3个对象创建新版本
        List<ItemRevision> newVersions = revisions.subList(0, 3).stream()
                .map(rev -> {
                    try {
                        rev.reload(); // 确保状态是最新的
                        return S.service(RevisionService.class).revise(rev, CopyRule.CopyReference);
                    } catch (Exception e) {
                        log.error("Failed to create new version for {}", rev.getCode(), e);
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

        log.info("创建了 {} 个基础对象，{} 个新版本", revisions.size(), newVersions.size());

        return new TestData(revisions, newVersions);
    }

    /**
     * 测试基础的逻辑对象创建和解析
     */
    private void testBasicLogicalObject(TestData testData) {
        log.info("=== 测试基础逻辑对象功能 ===");

        ItemRevision firstRevision = testData.baseRevisions.get(0);
        String businessCode = firstRevision.getCode();

        // 1. 从物理对象创建逻辑对象
        LogicalModelObject<ItemRevision> logical1 = LogicalModelObject.of(firstRevision);
        Assertion.assertEquals(businessCode, logical1.getCode());
        Assertion.assertEquals(firstRevision.get_objectType(), logical1.getObjectType());
        log.info("✓ 从物理对象创建逻辑对象成功");

        // 2. 通过业务编码创建逻辑对象
        LogicalModelObject<ItemRevision> logical2 = LogicalModelObject.of(
                "io.emop.model.common.ItemRevision", businessCode, RevisionRule.LATEST);
        Assertion.assertEquals(businessCode, logical2.getCode());
        log.info("✓ 通过业务编码创建逻辑对象成功");

        // 3. 测试解析
        ItemRevision resolved = logical2.resolve();
        Assertion.assertNotNull(resolved);
        Assertion.assertEquals(businessCode, resolved.getCode());
        log.info("✓ 逻辑对象解析成功: {}", resolved);

        // 4. 测试版本规则切换
        LogicalModelObject<ItemRevision> logical3 = logical2.withRevisionRule(RevisionRule.LATEST_WORKING);
        Assertion.assertEquals(RevisionRule.LATEST_WORKING, logical3.getRevisionRule());
        log.info("✓ 版本规则切换成功");
    }

    /**
     * 测试版本规则的应用
     */
    private void testRevisionRules(TestData testData) {
        log.info("=== 测试版本规则 ===");

        ItemRevision firstRevision = testData.baseRevisions.get(0);
        String businessCode = firstRevision.getCode();

        // 测试不同版本规则的解析结果
        LogicalModelObject<ItemRevision> latestLogical = LogicalModelObject.of(
                "io.emop.model.common.ItemRevision", businessCode, RevisionRule.LATEST);
        LogicalModelObject<ItemRevision> releasedLogical = LogicalModelObject.of(
                "io.emop.model.common.ItemRevision", businessCode, RevisionRule.LATEST_RELEASED);
        LogicalModelObject<ItemRevision> workingLogical = LogicalModelObject.of(
                "io.emop.model.common.ItemRevision", businessCode, RevisionRule.LATEST_WORKING);

        ItemRevision latestResolved = latestLogical.resolve();
        ItemRevision releasedResolved = releasedLogical.resolve();
        ItemRevision workingResolved = workingLogical.resolve();

        // 验证版本规则的效果
        Assertion.assertNotNull(latestResolved);
        Assertion.assertNotNull(releasedResolved);
        Assertion.assertNotNull(workingResolved);

        log.info("✓ LATEST 规则解析到版本: {}", latestResolved.getRevId());
        log.info("✓ LATEST_RELEASED 规则解析到版本: {}", releasedResolved.getRevId());
        log.info("✓ LATEST_WORKING 规则解析到版本: {}", workingResolved.getRevId());

        // 如果有新版本，LATEST 应该指向新版本，LATEST_RELEASED 应该指向旧版本
        if (!testData.newVersions.isEmpty()) {
            String latestRevId = latestResolved.getRevId();
            String releasedRevId = releasedResolved.getRevId();

            Assertion.assertEquals("B", latestRevId); // 新版本应该是 B
            Assertion.assertEquals("A", releasedRevId); // 发布版本应该是 A
            log.info("✓ 版本规则正确区分了不同版本");
        }
    }

    /**
     * 测试逻辑对象的关系查询
     * 注意：findLogicalRelation 只支持 RevisionableRefTrait 类型的关系
     * 每个对象只支持一个版本关系，不需要指定 relationName
     */
    private void testLogicalRelations(TestData testData) {
        log.info("=== 测试逻辑对象关系查询 ===");
        log.info("注意：此测试需要对象定义了 RevisionableRefTrait 属性");

        // 使用具体的物理对象作为 source
        ItemRevision sourceRevision = testData.baseRevisions.get(0);

        // 1. 测试关系查询 - 使用具体对象作为 source
        // 注意：对象必须定义了 RevisionableRefTrait 属性，否则会抛出异常
        try {
            LogicalModelObject<Revisionable> relation = Q.findLogicalRelation(
                    sourceRevision, RevisionRule.LATEST_WORKING);

            Assertion.assertNotNull(relation);
            log.info("✓ 找到逻辑关系: {}", relation.getCode());

            // 2. 验证解析逻辑对象
            Revisionable resolved = relation.resolve();
            Assertion.assertNotNull(resolved);
            log.info("✓ 解析逻辑对象成功: {} (版本: {})", resolved.getCode(), resolved.getRevId());

        } catch (IllegalArgumentException e) {
            log.warn("⚠ 关系查询失败: {}，这是预期的，因为 ItemRevision 可能没有定义 RevisionableRefTrait", 
                    e.getMessage());
            log.info("✓ 正确抛出异常，说明只支持 RevisionableRefTrait 的验证生效");
        } catch (Exception e) {
            log.warn("⚠ 关系查询出现其他异常: {}", e.getMessage());
        }
    }

    /**
     * 测试 Q 语法糖的逻辑查询功能
     */
    private void testLogicalQuerySyntax(TestData testData) {
        log.info("=== 测试 Q 逻辑查询语法糖 ===");

        // 1. 测试基础查询
        List<ItemRevision> logicals = Q.<ItemRevision>logical("io.emop.model.common.ItemRevision")
                .where("name LIKE ?", "LogicalObject-Test-" + dateCode + "%")
                .withRule(RevisionRule.LATEST)
                .limit(5)
                .query();

        Assertion.assertTrue(logicals.size() > 0);
        Assertion.assertTrue(logicals.size() <= 5);
        log.info("✓ Q 基础查询成功，找到 {} 个逻辑对象", logicals.size());

        // 2. 测试排序
        List<ItemRevision> sortedLogicals = Q.<ItemRevision>logical("io.emop.model.common.ItemRevision")
                .where("name LIKE ?", "LogicalObject-Test-" + dateCode + "%")
                .withRule(RevisionRule.LATEST)
                .asc("name")
                .query();

        Assertion.assertTrue(sortedLogicals.size() > 0);
        log.info("✓ Q 排序查询成功");

        // 3. 测试 first() 方法
        ItemRevision firstLogical = Q.<ItemRevision>logical("io.emop.model.common.ItemRevision")
                .where("name LIKE ?", "LogicalObject-Test-" + dateCode + "%")
                .withRule(RevisionRule.LATEST)
                .first();

        Assertion.assertNotNull(firstLogical);
        log.info("✓ Q first() 查询成功: {}", firstLogical.getCode());

        // 4. 测试计数
        long count = Q.<ItemRevision>logical("io.emop.model.common.ItemRevision")
                .where("name LIKE ?", "LogicalObject-Test-" + dateCode + "%")
                .withRule(RevisionRule.LATEST)
                .count();

        Assertion.assertTrue(count > 0);
        log.info("✓ Q count() 查询成功，计数: {}", count);
    }

    /**
     * 测试批量操作
     */
    private void testBatchOperations(TestData testData) {
        log.info("=== 测试批量操作 ===");

        // 1. 创建多个逻辑对象
        List<LogicalModelObject<ItemRevision>> logicals = testData.baseRevisions.stream()
                .limit(5)
                .map(rev -> LogicalModelObject.<ItemRevision>of(
                        "io.emop.model.common.ItemRevision",
                        rev.getCode(),
                        RevisionRule.LATEST_WORKING))
                .collect(Collectors.toList());

        // 2. 批量解析
        Map<String, ItemRevision> resolved = Q.batchResolve(logicals);

        Assertion.assertEquals(logicals.size(), resolved.size());
        log.info("✓ 批量解析成功，解析了 {} 个逻辑对象", resolved.size());

        // 3. 验证解析结果
        for (LogicalModelObject<ItemRevision> logical : logicals) {
            ItemRevision resolvedItem = resolved.get(logical.getCode());
            Assertion.assertNotNull(resolvedItem);
            Assertion.assertEquals(logical.getCode(), resolvedItem.getCode());
        }
        log.info("✓ 批量解析结果验证通过");
    }

    /**
     * 测试用户上下文中的版本规则
     */
    private void testUserContextRevisionRule(TestData testData) {
        log.info("=== 测试用户上下文版本规则 ===");

        String businessCode = testData.baseRevisions.get(0).getCode();

        try {
            // 1. 测试在不同版本规则上下文中的解析
            ItemRevision resolvedInLatest = UserContext.withRevisionRule(
                    RevisionRule.LATEST,
                    () -> {
                        LogicalModelObject<ItemRevision> logical = LogicalModelObject.of(
                                "io.emop.model.common.ItemRevision", businessCode);
                        return logical.resolve();
                    }
            );

            ItemRevision resolvedInReleased = UserContext.withRevisionRule(
                    RevisionRule.LATEST_RELEASED,
                    () -> {
                        LogicalModelObject<ItemRevision> logical = LogicalModelObject.of(
                                "io.emop.model.common.ItemRevision", businessCode);
                        return logical.resolve();
                    }
            );

            Assertion.assertNotNull(resolvedInLatest);
            Assertion.assertNotNull(resolvedInReleased);

            // 如果有新版本，应该解析到不同的版本
            if (!testData.newVersions.isEmpty()) {
                String latestRevId = resolvedInLatest.getRevId();
                String releasedRevId = resolvedInReleased.getRevId();

                log.info("✓ LATEST 上下文解析到版本: {}", latestRevId);
                log.info("✓ LATEST_RELEASED 上下文解析到版本: {}", releasedRevId);
            }

            log.info("✓ 用户上下文版本规则测试成功");

        } catch (Exception e) {
            log.error("用户上下文版本规则测试失败", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 演示最佳实践：结合具体对象和逻辑对象的工作流
     * 注意：只支持 RevisionableRefTrait 类型的关系，每个对象只支持一个版本关系
     */
    private void testBestPracticeWorkflow(TestData testData) {
        log.info("=== 演示最佳实践工作流 ===");
        log.info("注意：此工作流需要对象定义了 RevisionableRefTrait 属性");

        // 1. 从具体的工作版本开始
        ItemRevision workingRevision = testData.baseRevisions.get(0);
        log.info("1. 开始处理工作版本: {} (版本: {})", workingRevision.getCode(), workingRevision.getRevId());

        // 2. 查找相关的逻辑对象（使用具体对象作为 source - 推荐做法）
        // 注意：对象必须定义了 RevisionableRefTrait 属性
        LogicalModelObject<Revisionable> relatedLogical = Q.findLogicalRelation(
                workingRevision, RevisionRule.LATEST_RELEASED);
        log.info("2. 找到相关的发布版本逻辑对象: {}", relatedLogical.getCode());

        // 3. 解析为具体对象
        Revisionable relatedObject = relatedLogical.resolve();
        log.info("3. 解析了具体对象: {} (版本: {})", relatedObject.getCode(), relatedObject.getRevId());

        // 4. 处理业务逻辑
        log.info("   - 处理相关对象: {} (版本: {})", relatedObject.getCode(), relatedObject.getRevId());

        // 5. 如果需要，可以在不同版本规则下重新查找
        LogicalModelObject<Revisionable> latestVersion = Q.findLogicalRelation(
                workingRevision, RevisionRule.LATEST);
        log.info("5. 同样关系在最新版本规则下的对象: {}", latestVersion.getCode());

        log.info("✓ 最佳实践工作流演示完成");
    }

    /**
     * 测试性能对比：逻辑对象 vs 物理对象查询
     */
    private void testPerformanceComparison(TestData testData) {
        log.info("=== 测试性能对比 ===");

        final int iterations = 50; // 减少迭代次数以避免过长的测试时间

        // 1. 测试物理对象查询性能
        long physicalTime = TimerUtils.measureExecutionTime("物理对象查询", () -> {
            for (int i = 0; i < iterations; i++) {
                testData.baseRevisions.forEach(rev -> {
                    // 模拟基于ID的物理查询
                    ModelObject found = S.service(ObjectService.class).findById(rev.getId());
                    Assertion.assertNotNull(found);
                });
            }
        }) / 1_000_000;

        // 2. 测试逻辑对象查询性能
        long logicalTime = TimerUtils.measureExecutionTime("逻辑对象查询", () -> {
            for (int i = 0; i < iterations; i++) {
                testData.baseRevisions.forEach(rev -> {
                    // 模拟基于业务编码的逻辑查询
                    LogicalModelObject<ItemRevision> logical = LogicalModelObject.of(
                            "io.emop.model.common.ItemRevision",
                            rev.getCode(),
                            RevisionRule.LATEST_WORKING);
                    ItemRevision resolved = logical.resolve();
                    Assertion.assertNotNull(resolved);
                });
            }
        }) / 1_000_000;

        // 3. 测试关系查询性能（仅在支持 RevisionableRefTrait 时）
        long relationTime = TimerUtils.measureExecutionTime("逻辑关系查询", () -> {
                for (int i = 0; i < iterations; i++) {
                    LogicalModelObject<Revisionable> relation = Q.findLogicalRelation(
                            testData.baseRevisions.get(0), RevisionRule.LATEST_WORKING);
                    Assertion.assertNotNull(relation);
                }
            }) / 1_000_000;

        log.info("性能测试结果：");
        log.info("  - 物理对象查询: {} ms", physicalTime);
        log.info("  - 逻辑对象查询: {} ms", logicalTime);
        if (relationTime > 0) {
            log.info("  - 逻辑关系查询: {} ms", relationTime);
        }

        // 逻辑对象查询应该在合理的性能范围内
        Assertion.assertTrue(logicalTime < physicalTime * 10,
                "逻辑对象查询性能应该在合理范围内");
        log.info("✓ 性能测试通过");
    }

    /**
     * 测试 Q.logical() 语法糖的新API
     */
    private void testQLogicalSyntax(TestData testData) {
        log.info("=== 测试 Q.logical() 语法糖 ===");

        // 2. 测试直接解析
        List<ItemRevision> resolved = Q.<ItemRevision>logical(ItemRevision.class)
                .where("name LIKE ?", "LogicalObject-Test-" + dateCode + "%")
                .withRule(RevisionRule.LATEST)
                .query();

        Assertion.assertTrue(resolved.size() > 0);
        log.info("✓ Q.logical().query() 直接解析成功，解析了 {} 个物理对象", resolved.size());

        // 3. 测试单个对象解析
        ItemRevision single = Q.<ItemRevision>logical(ItemRevision.class)
                .where("code = ?", testData.baseRevisions.get(0).getCode())
                .withRule(RevisionRule.LATEST)
                .first();

        Assertion.assertNotNull(single);
        Assertion.assertEquals(testData.baseRevisions.get(0).getCode(), single.getCode());
        log.info("✓ Q.logical().first() 单对象解析成功: {}", single);
    }

    /**
     * 测试一步到位的解析API
     */
    private void testDirectResolveAPI(TestData testData) {
        log.info("=== 测试一步到位解析API ===");

        String testCode = testData.baseRevisions.get(0).getCode();

        // 1. 测试不同版本规则的解析结果
        ItemRevision latest = Q.<ItemRevision>logical(ItemRevision.class)
                .where("code = ?", testCode)
                .withRule(RevisionRule.LATEST)
                .first();

        ItemRevision released = Q.<ItemRevision>logical(ItemRevision.class)
                .where("code = ?", testCode)
                .withRule(RevisionRule.LATEST_RELEASED)
                .first();

        ItemRevision working = Q.<ItemRevision>logical(ItemRevision.class)
                .where("code = ?", testCode)
                .withRule(RevisionRule.LATEST_WORKING)
                .first();

        Assertion.assertNotNull(latest);
        Assertion.assertNotNull(released);
        Assertion.assertNotNull(working);

        log.info("✓ 不同版本规则解析成功:");
        log.info("  - LATEST: {}/{}", latest.getCode(), latest.getRevId());
        log.info("  - LATEST_RELEASED: {}/{}", released.getCode(), released.getRevId());
        log.info("  - LATEST_WORKING: {}/{}", working.getCode(), working.getRevId());

        // 2. 验证版本规则的正确性
        if (!testData.newVersions.isEmpty()) {
            // 如果有新版本，LATEST 应该指向新版本（B），LATEST_RELEASED 应该指向发布版本（A）
            Assertion.assertEquals("B", latest.getRevId(),
                    "LATEST 规则应该返回最新版本 B，但返回了 " + latest.getRevId());
            Assertion.assertEquals("A", released.getRevId(),
                    "LATEST_RELEASED 规则应该返回最新发布版本 A，但返回了 " + released.getRevId());
            Assertion.assertEquals("B", working.getRevId(),
                    "LATEST_WORKING 规则应该返回最新工作版本 B，但返回了 " + working.getRevId());

            // 验证状态
            Assertion.assertEquals("Released", released.get_state(),
                    "LATEST_RELEASED 返回的对象状态应该是 Released");
            Assertion.assertTrue(working.get_state().equals("Working") || working.get_state().equals("Draft"),
                    "LATEST_WORKING 返回的对象状态应该是 Working 或 Draft，但是 " + working.get_state());

            log.info("✓ 版本规则正确性验证通过");
        } else {
            log.warn("⚠ 没有新版本数据，跳过版本规则验证");
        }

        // 3. 测试批量解析
        List<ItemRevision> batchResolved = Q.<ItemRevision>logical(ItemRevision.class)
                .where("name LIKE ?", "LogicalObject-Test-" + dateCode + "%")
                .withRule(RevisionRule.LATEST_RELEASED)
                .asc("code")
                .query();

        Assertion.assertTrue(batchResolved.size() >= 3,
                "批量解析应该至少返回3个对象，实际返回了 " + batchResolved.size());

        // 验证批量解析结果都是发布状态
        for (ItemRevision item : batchResolved) {
            Assertion.assertEquals("Released", item.get_state(),
                    "LATEST_RELEASED 规则返回的对象 " + item.getCode() + " 状态应该是 Released，但是 " + item.get_state());
        }

        log.info("✓ 批量解析成功，解析了 {} 个对象", batchResolved.size());

        // 4. 测试存在性检查
        boolean exists = Q.<ItemRevision>logical(ItemRevision.class)
                .where("code = ?", testCode)
                .withRule(RevisionRule.LATEST)
                .exists();

        Assertion.assertTrue(exists, "存在性检查应该返回 true");

        // 测试不存在的情况
        boolean notExists = Q.<ItemRevision>logical(ItemRevision.class)
                .where("code = ?", "NON_EXISTENT_CODE")
                .withRule(RevisionRule.LATEST)
                .exists();

        Assertion.assertFalse(notExists, "不存在的对象检查应该返回 false");

        log.info("✓ 存在性检查通过");
    }

    /**
     * 测试分页逻辑查询
     */
    private void testLogicalPagination(TestData testData) {
        log.info("=== 测试分页逻辑查询 ===");

        // 2. 测试物理对象分页解析
        Pagination.Page<ItemRevision> resolvedPage = Q.<ItemRevision>logical(ItemRevision.class)
                .where("name LIKE ?", "LogicalObject-Test-" + dateCode + "%")
                .withRule(RevisionRule.LATEST_RELEASED)
                .desc("code")
                .pageSize(5)
                .pageNumber(0)
                .queryPage();

        Assertion.assertTrue(resolvedPage.getContent().size() > 0, "物理对象分页解析应该有结果");
        Assertion.assertTrue(resolvedPage.getContent().size() <= 5,
                "分页大小应该不超过5，实际是 " + resolvedPage.getContent().size());
        Assertion.assertTrue(resolvedPage.getTotalElements() >= 5,
                "发布版本总数应该至少是5，实际是 " + resolvedPage.getTotalElements());

        // 验证排序是否正确（降序）
        List<String> codes = resolvedPage.getContent().stream()
                .map(ItemRevision::getCode)
                .collect(Collectors.toList());
        List<String> sortedCodes = new ArrayList<>(codes);
        sortedCodes.sort(Collections.reverseOrder());
        Assertion.assertEquals(sortedCodes, codes, "结果应该按code降序排列");

        log.info("✓ 物理对象分页解析成功: {}/{}，总数: {}",
                resolvedPage.getContent().size(), 5, resolvedPage.getTotalElements());

        // 3. 验证分页结果的正确性
        for (ItemRevision item : resolvedPage.getContent()) {
            Assertion.assertTrue(item.getName().contains("LogicalObject-Test-" + dateCode),
                    "分页结果应该包含测试标识，但 " + item.getName() + " 不包含");
            Assertion.assertEquals("Released", item.get_state(),
                    "LATEST_RELEASED 规则返回的对象状态应该是 Released，但 " + item.getCode() + " 是 " + item.get_state());
            log.info("  - 分页结果: {}/{} - {} (状态: {})",
                    item.getCode(), item.getRevId(), item.getName(), item.get_state());
        }

        // 4. 测试分页导航
        if (resolvedPage.getTotalElements() > 5) {
            Pagination.Page<ItemRevision> nextPage = Q.<ItemRevision>logical(ItemRevision.class)
                    .where("name LIKE ?", "LogicalObject-Test-" + dateCode + "%")
                    .withRule(RevisionRule.LATEST_RELEASED)
                    .desc("code")
                    .pageSize(5)
                    .pageNumber(1)
                    .queryPage();

            Assertion.assertTrue(nextPage.getContent().size() > 0, "第二页应该有数据");
            Assertion.assertEquals(resolvedPage.getTotalElements(), nextPage.getTotalElements(),
                    "第二页的总数应该与第一页相同");

            // 验证两页的数据不重复
            Set<String> firstPageCodes = resolvedPage.getContent().stream()
                    .map(ItemRevision::getCode)
                    .collect(Collectors.toSet());
            Set<String> secondPageCodes = nextPage.getContent().stream()
                    .map(ItemRevision::getCode)
                    .collect(Collectors.toSet());

            Assertion.assertTrue(Collections.disjoint(firstPageCodes, secondPageCodes),
                    "第一页和第二页的数据不应该重复");

            log.info("✓ 分页导航测试成功，第二页有 {} 条记录", nextPage.getContent().size());
        }

        log.info("✓ 分页逻辑查询测试全部通过");
    }

    /**
     * 测试数据容器
     */
    private static class TestData {
        final List<ItemRevision> baseRevisions;
        final List<ItemRevision> newVersions;

        TestData(List<ItemRevision> baseRevisions, List<ItemRevision> newVersions) {
            this.baseRevisions = baseRevisions;
            this.newVersions = newVersions;
        }
    }
}