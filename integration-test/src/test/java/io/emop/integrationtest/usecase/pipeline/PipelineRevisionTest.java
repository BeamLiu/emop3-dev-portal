package io.emop.integrationtest.usecase.pipeline;

import io.emop.model.common.CopyRule;
import io.emop.model.common.ItemRevision;
import io.emop.model.common.ModelObject;
import io.emop.model.common.UserContext;
import io.emop.model.common.Revisionable.CriteriaByCodeAndRevId;
import io.emop.model.common.Revisionable.CriteriaByRevCode;
import io.emop.model.lifecycle.LifecycleState;
import io.emop.integrationtest.util.TimerUtils;
import io.emop.service.S;
import io.emop.service.api.data.ObjectService;
import io.emop.service.api.domain.common.RevisionService;
import io.emop.service.api.domain.common.RevisionServicePipeline;
import io.emop.service.api.lifecycle.LifecycleService;
import io.emop.service.api.pipeline.ResultFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.emop.integrationtest.util.Assertion.*;

/**
 * RevisionService Pipeline增强测试用例
 * 验证版本管理功能正确性、新增批量方法和性能提升效果
 */
@RequiredArgsConstructor
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PipelineRevisionTest {

    private static final String revId = "A";
    private static final int batchSize = 200;
    private static final int smallBatchSize = 10;

    @BeforeAll
    public void setup() {
        UserContext.setCurrentUser(new UserContext(100L, List.of("admin")));
    }

    @Test
    @Order(1)
    public void testBasicReviseFunctionality() {
        S.withStrongConsistency(this::testBasicReviseFunctionalityImpl);
    }

    @Test
    @Order(2)
    public void testBasicQueryFunctionality() {
        S.withStrongConsistency(this::testBasicQueryFunctionalityImpl);
    }

    @Test
    @Order(3)
    public void testNewBatchQueryMethods() {
        S.withStrongConsistency(this::testNewBatchQueryMethodsImpl);
    }

    @Test
    @Order(4)
    public void testComplexQueryScenarios() {
        S.withStrongConsistency(this::testComplexQueryScenariosImpl);
    }

    @Test
    @Order(5)
    public void testPerformanceComparison() {
        S.withStrongConsistency(this::testPerformanceComparisonImpl);
    }

    @Test
    @Order(6)
    public void testEdgeCases() {
        S.withStrongConsistency(this::testEdgeCasesImpl);
    }

    @Test
    @Order(7)
    public void testReviseWithProperties() {
        S.withStrongConsistency(this::testReviseWithPropertiesImpl);
    }

    @Test
    @Order(8)
    public void testPipelineReviseWithProperties() {
        S.withStrongConsistency(this::testPipelineReviseWithPropertiesImpl);
    }

    /**
     * 测试基本版本创建功能
     */
    private void testBasicReviseFunctionalityImpl() {
        String dateCode = String.valueOf(System.currentTimeMillis());
        log.info("=== 测试Pipeline版本创建功能 ===");

        ObjectService objectService = S.service(ObjectService.class);
        List<ItemRevision> testData = prepareTestData(dateCode, smallBatchSize);
        List<ItemRevision> savedObjects = objectService.saveAll(testData);

        // ✅ 必须先移动到发布状态才能进行版本修订
        List<ItemRevision> releasedObjects = S.service(LifecycleService.class).moveToState(savedObjects, LifecycleState.STATE_RELEASED);

        TimerUtils.measureExecutionTime("Pipeline版本创建功能测试", () -> {
            RevisionServicePipeline pipeline = RevisionService.pipeline();

            // 收集版本创建操作
            List<ResultFuture<ItemRevision>> futures = new ArrayList<>();
            for (ItemRevision obj : releasedObjects) {
                futures.add(pipeline.revise(obj, CopyRule.NoCopy));
            }

            // 执行Pipeline
            pipeline.execute();

            // 验证结果
            for (int i = 0; i < futures.size(); i++) {
                try {
                    ItemRevision result = futures.get(i).get();
                    assertNotNull(result);
                    assertNotNull(result.getId());
                    assertEquals("B", result.getRevId()); // 版本应该从A升级到B
                    assertEquals(releasedObjects.get(i).getCode(), result.getCode());
                    assertNotEquals(releasedObjects.get(i).getId(), result.getId()); // 新版本应该有不同的ID
                } catch (Exception e) {
                    throw new RuntimeException("验证版本创建结果失败", e);
                }
            }

            log.info("✅ Pipeline版本创建功能测试通过: {}个版本", futures.size());
        });

        // 清理测试数据：需要删除原始对象和新创建的版本
        List<Long> allIds = new ArrayList<>();
        allIds.addAll(savedObjects.stream().map(ModelObject::getId).collect(Collectors.toList()));
        // 注意：新创建的版本会在Pipeline执行后产生，这里简化处理
        objectService.forceDelete(allIds);
    }

    /**
     * 测试基本查询功能
     */
    private void testBasicQueryFunctionalityImpl() {
        final String dateCode = String.valueOf(System.currentTimeMillis());
        log.info("=== 测试Pipeline查询功能 ===");

        ObjectService objectService = S.service(ObjectService.class);
        List<ItemRevision> testData = prepareTestData(dateCode, smallBatchSize);
        List<ItemRevision> savedObjects = objectService.saveAll(testData);

        TimerUtils.measureExecutionTime("Pipeline查询功能测试", () -> {
            RevisionServicePipeline pipeline = RevisionService.pipeline().readonly();

            // 收集查询操作
            List<ResultFuture<ItemRevision>> futures = new ArrayList<>();
            for (ItemRevision obj : savedObjects) {
                CriteriaByRevCode<ItemRevision> criteria = new CriteriaByRevCode<>(
                        ItemRevision.class.getSimpleName(), obj.getCode());
                futures.add(pipeline.queryLatestRevision(criteria));
            }

            // 执行Pipeline
            pipeline.execute();

            // 验证结果
            for (int i = 0; i < futures.size(); i++) {
                try {
                    ItemRevision result = futures.get(i).get();
                    assertNotNull(result);
                    assertEquals(savedObjects.get(i).getCode(), result.getCode());
                    assertEquals(revId, result.getRevId());
                    assertEquals(savedObjects.get(i).getId(), result.getId());
                } catch (Exception e) {
                    throw new RuntimeException("验证查询结果失败", e);
                }
            }

            log.info("✅ Pipeline查询功能测试通过: {}个查询", futures.size());
        });

        // 清理测试数据
        objectService.forceDelete(savedObjects.stream().map(ModelObject::getId).collect(Collectors.toList()));
    }

    /**
     * ✅ 测试新增的批量查询方法
     */
    private void testNewBatchQueryMethodsImpl() {
        final String dateCode = String.valueOf(System.currentTimeMillis());
        log.info("=== 测试新增批量查询方法 ===");

        ObjectService objectService = S.service(ObjectService.class);
        RevisionService revisionService = S.service(RevisionService.class);

        // 准备多版本测试数据
        List<ItemRevision> testData = prepareTestData(dateCode, smallBatchSize);
        List<ItemRevision> savedObjects = objectService.saveAll(testData);

        // ✅ 创建发布版本 - 必须先发布才能进行版本修订
        List<ItemRevision> releasedObjects = S.service(LifecycleService.class).moveToState(savedObjects, LifecycleState.STATE_RELEASED);

        // 创建B版本（从发布的A版本修订）
        List<ItemRevision> bVersions = revisionService.revise(releasedObjects, CopyRule.NoCopy);

        // ✅ 将B版本也移动到发布状态，才能再次修订
        List<ItemRevision> releasedBVersions = S.service(LifecycleService.class).moveToState(bVersions, LifecycleState.STATE_RELEASED);

        // 创建C版本（从发布的B版本修订）
        List<ItemRevision> cVersions = revisionService.revise(releasedBVersions, CopyRule.NoCopy);

        // 发布部分C版本
        List<ItemRevision> someReleasedC = S.service(LifecycleService.class).moveToState(
                cVersions.subList(0, smallBatchSize / 2), LifecycleState.STATE_RELEASED);

        List<String> codes = savedObjects.stream().map(ItemRevision::getCode).collect(Collectors.toList());

        TimerUtils.measureExecutionTime("新增批量查询方法测试", () -> {
            // 测试1: queryLatestReleasedBatch
            log.info("测试 queryLatestReleasedBatch 方法");
            List<ItemRevision> latestReleasedResults = revisionService.queryLatestReleasedBatch(codes, ItemRevision.class.getSimpleName());

            // 验证结果：前半部分应该是C版本(已发布)，后半部分应该是B版本(已发布的B版本)
            for (int i = 0; i < latestReleasedResults.size(); i++) {
                ItemRevision result = latestReleasedResults.get(i);
                if (i < smallBatchSize / 2) {
                    // 前半部分应该返回发布的C版本
                    assertEquals("C", result.getRevId(), "前半部分应该是发布的C版本");
                } else {
                    // 后半部分应该返回发布的B版本（因为C版本未发布）
                    assertEquals("B", result.getRevId(), "后半部分应该是发布的B版本");
                }
                assertEquals(codes.get(i), result.getCode());
            }

            // 测试2: queryRevisionsBatch
            log.info("测试 queryRevisionsBatch 方法");
            List<List<ItemRevision>> allRevisionsResults = revisionService.queryRevisionsBatch(codes, ItemRevision.class.getSimpleName());

            assertEquals(codes.size(), allRevisionsResults.size(), "返回的列表数量应该等于输入codes数量");

            for (int i = 0; i < allRevisionsResults.size(); i++) {
                List<ItemRevision> revisions = allRevisionsResults.get(i);
                assertEquals(3, revisions.size(), "每个code应该有3个版本: A, B, C");

                // 验证版本排序（应该按revId升序）
                assertEquals("A", revisions.get(0).getRevId());
                assertEquals("B", revisions.get(1).getRevId());
                assertEquals("C", revisions.get(2).getRevId());

                // 验证都是同一个code
                String expectedCode = codes.get(i);
                for (ItemRevision rev : revisions) {
                    assertEquals(expectedCode, rev.getCode());
                }
            }

            log.info("✅ 新增批量查询方法测试通过");
        });

        // 清理测试数据
        List<Long> allIds = new ArrayList<>();
        allIds.addAll(savedObjects.stream().map(ModelObject::getId).collect(Collectors.toList()));
        allIds.addAll(bVersions.stream().map(ModelObject::getId).collect(Collectors.toList()));
        allIds.addAll(cVersions.stream().map(ModelObject::getId).collect(Collectors.toList()));
        objectService.forceDelete(allIds);
    }

    /**
     * ✅ 测试复杂查询场景
     */
    private void testComplexQueryScenariosImpl() {
        final String dateCode = String.valueOf(System.currentTimeMillis());
        log.info("=== 测试复杂查询场景 ===");

        ObjectService objectService = S.service(ObjectService.class);
        RevisionService revisionService = S.service(RevisionService.class);

        // 准备测试数据
        List<ItemRevision> testData = prepareTestData(dateCode, smallBatchSize, "complex-test");
        List<ItemRevision> savedObjects = objectService.saveAll(testData);

        // ✅ 必须先发布才能进行版本修订
        List<ItemRevision> releasedObjects = S.service(LifecycleService.class).moveToState(savedObjects, LifecycleState.STATE_RELEASED);
        List<ItemRevision> bVersions = revisionService.revise(releasedObjects, CopyRule.NoCopy);

        TimerUtils.measureExecutionTime("复杂查询场景测试", () -> {
            RevisionServicePipeline pipeline = RevisionService.pipeline().readonly();

            // 混合查询：既有queryLatestRevision，也有queryRevision
            List<ResultFuture<ItemRevision>> latestFutures = new ArrayList<>();
            List<ResultFuture<ItemRevision>> specificFutures = new ArrayList<>();
            List<ResultFuture<List<ItemRevision>>> allRevisionsFutures = new ArrayList<>();

            for (int i = 0; i < savedObjects.size(); i++) {
                ItemRevision obj = savedObjects.get(i);

                // 查询最新版本
                CriteriaByRevCode<ItemRevision> latestCriteria = new CriteriaByRevCode<>(
                        ItemRevision.class.getSimpleName(), obj.getCode());
                latestFutures.add(pipeline.queryLatestRevision(latestCriteria));

                // 查询特定版本（A版本）
                CriteriaByCodeAndRevId<ItemRevision> specificCriteria = new CriteriaByCodeAndRevId<>(
                        ItemRevision.class.getSimpleName(), obj.getCode(), "A");
                specificFutures.add(pipeline.queryRevision(specificCriteria));

                // 查询所有版本
                allRevisionsFutures.add(pipeline.queryRevisions(latestCriteria));
            }

            // 执行Pipeline
            pipeline.execute();

            // 验证结果
            for (int i = 0; i < savedObjects.size(); i++) {
                try {
                    // 验证最新版本查询（应该是B版本）
                    ItemRevision latestResult = latestFutures.get(i).get();
                    assertNotNull(latestResult);
                    assertEquals("B", latestResult.getRevId());
                    assertEquals(savedObjects.get(i).getCode(), latestResult.getCode());

                    // 验证特定版本查询（应该是A版本）
                    ItemRevision specificResult = specificFutures.get(i).get();
                    assertNotNull(specificResult);
                    assertEquals("A", specificResult.getRevId());
                    assertEquals(savedObjects.get(i).getCode(), specificResult.getCode());
                    assertEquals(savedObjects.get(i).getId(), specificResult.getId());

                    // 验证所有版本查询
                    List<ItemRevision> allRevisionsResult = allRevisionsFutures.get(i).get();
                    assertEquals(2, allRevisionsResult.size()); // 应该有A和B两个版本
                    assertEquals("A", allRevisionsResult.get(0).getRevId());
                    assertEquals("B", allRevisionsResult.get(1).getRevId());

                } catch (Exception e) {
                    throw new RuntimeException("验证复杂查询结果失败", e);
                }
            }

            log.info("✅ 复杂查询场景测试通过: {}个对象的混合查询", savedObjects.size());
        });

        // 清理测试数据
        List<Long> allIds = new ArrayList<>();
        allIds.addAll(savedObjects.stream().map(ModelObject::getId).collect(Collectors.toList()));
        allIds.addAll(bVersions.stream().map(ModelObject::getId).collect(Collectors.toList()));
        objectService.forceDelete(allIds);
    }

    /**
     * ✅ 测试边界条件和异常情况
     */
    private void testEdgeCasesImpl() {
        log.info("=== 测试边界条件和异常情况 ===");

        RevisionService revisionService = S.service(RevisionService.class);

        TimerUtils.measureExecutionTime("边界条件测试", () -> {
            // 测试空列表
            List<ItemRevision> emptyResults = revisionService.queryLatestRevisions(new ArrayList<>(), ItemRevision.class.getSimpleName());
            assertTrue(emptyResults.isEmpty(), "空列表查询应该返回空结果");

            List<ItemRevision> emptyReleasedResults = revisionService.queryLatestReleasedBatch(new ArrayList<>(), ItemRevision.class.getSimpleName());
            assertTrue(emptyReleasedResults.isEmpty(), "空列表查询发布版本应该返回空结果");

            List<List<ItemRevision>> emptyBatchResults = revisionService.queryRevisionsBatch(new ArrayList<>(), ItemRevision.class.getSimpleName());
            assertTrue(emptyBatchResults.isEmpty(), "空列表批量查询版本应该返回空结果");

            // 测试不存在的code
            List<String> nonExistentCodes = List.of("non-existent-code-1", "non-existent-code-2");
            List<ItemRevision> nonExistentResults = revisionService.queryLatestRevisions(nonExistentCodes, ItemRevision.class.getSimpleName());
            assertEquals(2, nonExistentResults.size(), "应该返回与输入codes数量相同的结果");
            assertNull(nonExistentResults.get(0), "不存在的code应该返回null");
            assertNull(nonExistentResults.get(1), "不存在的code应该返回null");

            // 测试Pipeline空操作
            RevisionServicePipeline emptyPipeline = RevisionService.pipeline().readonly();
            emptyPipeline.execute(); // 应该能正常执行而不报错

            log.info("✅ 边界条件测试通过");
        });
    }

    /**
     * 性能对比测试：传统方式 vs Pipeline方式 vs 批量API方式
     */
    private void testPerformanceComparisonImpl() {
        final String dateCode = String.valueOf(System.currentTimeMillis());
        log.info("=== 版本管理性能对比测试 ===");

        ObjectService objectService = S.service(ObjectService.class);
        RevisionService revisionService = S.service(RevisionService.class);

        // 准备测试数据
        List<ItemRevision> testData = prepareTestData(dateCode, batchSize);
        List<ItemRevision> savedObjects = objectService.saveAll(testData);

        // ✅ 必须先发布才能进行版本修订
        List<ItemRevision> releasedObjects = S.service(LifecycleService.class).moveToState(savedObjects, LifecycleState.STATE_RELEASED);

        // 测试1：传统逐个调用方式
        long traditionalTime = TimerUtils.measureExecutionTime("传统方式逐个版本创建 " + batchSize + " 个对象", () -> {
            for (ItemRevision obj : releasedObjects) {
                revisionService.revise(obj, CopyRule.NoCopy);
            }
        }) / 1_000_000;

        // 重新准备数据（因为上面已经创建了新版本）
        List<ItemRevision> testData2 = prepareTestData(dateCode, batchSize, "pipeline-test");
        List<ItemRevision> savedObjects2 = objectService.saveAll(testData2);
        // ✅ 必须先发布才能进行版本修订
        List<ItemRevision> releasedObjects2 = S.service(LifecycleService.class).moveToState(savedObjects2, LifecycleState.STATE_RELEASED);

        // 测试2：Pipeline批量方式
        long pipelineTime = TimerUtils.measureExecutionTime("Pipeline批量版本创建 " + batchSize + " 个对象", () -> {
            RevisionServicePipeline pipeline = RevisionService.pipeline();
            List<ResultFuture<ItemRevision>> futures = new ArrayList<>();

            for (ItemRevision obj : releasedObjects2) {
                futures.add(pipeline.revise(obj, CopyRule.NoCopy));
            }
            pipeline.execute();

            // 验证结果
            for (ResultFuture<ItemRevision> future : futures) {
                try {
                    ItemRevision result = future.get();
                    assertNotNull(result);
                    assertEquals("B", result.getRevId());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }) / 1_000_000;

        // 测试3：使用原生批量API方式
        List<ItemRevision> testData3 = prepareTestData(dateCode, batchSize, "batch-api-test");
        List<ItemRevision> savedObjects3 = objectService.saveAll(testData3);
        // ✅ 必须先发布才能进行版本修订
        List<ItemRevision> releasedObjects3 = S.service(LifecycleService.class).moveToState(savedObjects3, LifecycleState.STATE_RELEASED);

        long batchApiTime = TimerUtils.measureExecutionTime("原生批量API版本创建 " + batchSize + " 个对象", () -> {
            List<ItemRevision> results = revisionService.revise(releasedObjects3, CopyRule.NoCopy);
            assertEquals(batchSize, results.size());
            for (ItemRevision result : results) {
                assertEquals("B", result.getRevId());
            }
        }) / 1_000_000;

        // ✅ 测试4：不同查询方法的性能对比
        List<String> codes = savedObjects.stream().map(ItemRevision::getCode).collect(Collectors.toList());

        long traditionalQueryTime = TimerUtils.measureExecutionTime("传统方式逐个查询 " + batchSize + " 个最新版本", () -> {
            for (String code : codes) {
                CriteriaByRevCode<ItemRevision> criteria = new CriteriaByRevCode<>(
                        ItemRevision.class.getSimpleName(), code);
                revisionService.queryLatestRevision(criteria);
            }
        }) / 1_000_000;

        long pipelineQueryTime = TimerUtils.measureExecutionTime("Pipeline批量查询 " + batchSize + " 个最新版本", () -> {
            RevisionServicePipeline pipeline = RevisionService.pipeline().readonly();
            List<ResultFuture<ItemRevision>> futures = new ArrayList<>();

            for (String code : codes) {
                CriteriaByRevCode<ItemRevision> criteria = new CriteriaByRevCode<>(
                        ItemRevision.class.getSimpleName(), code);
                futures.add(pipeline.queryLatestRevision(criteria));
            }
            pipeline.execute();

            for (ResultFuture<ItemRevision> future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }) / 1_000_000;

        // ✅ 测试5：新增批量方法的性能
        long batchQueryTime = TimerUtils.measureExecutionTime("原生批量API查询 " + batchSize + " 个最新版本", () -> {
            List<ItemRevision> results = revisionService.queryLatestRevisions(codes, ItemRevision.class.getSimpleName());
            assertEquals(batchSize, results.size());
        }) / 1_000_000;

        long batchReleasedQueryTime = TimerUtils.measureExecutionTime("原生批量API查询 " + batchSize + " 个最新发布版本", () -> {
            List<ItemRevision> results = revisionService.queryLatestReleasedBatch(codes, ItemRevision.class.getSimpleName());
            // 注意：由于我们有发布的A版本，所以应该能查到对应数量的结果
            assertEquals(batchSize, results.size());
            // 验证返回的都是发布状态的版本
            for (ItemRevision result : results) {
                assertNotNull(result, "应该能查到发布版本");
                assertEquals("A", result.getRevId(), "最新发布版本应该是A版本");
            }
        }) / 1_000_000;

        // 性能统计
        double pipelineSpeedup = (double) traditionalTime / pipelineTime;
        double batchApiSpeedup = (double) traditionalTime / batchApiTime;
        double querySpeedup = (double) traditionalQueryTime / pipelineQueryTime;
        double batchQuerySpeedup = (double) traditionalQueryTime / batchQueryTime;

        log.info("🚀 版本管理性能对比结果 ({} 个对象):", batchSize);
        log.info("   === 版本创建性能 ===");
        log.info("   传统逐个调用: {}ms", traditionalTime);
        log.info("   Pipeline方式:  {}ms (提升 {}x)", pipelineTime, pipelineSpeedup);
        log.info("   原生批量API:  {}ms (提升 {}x)", batchApiTime, batchApiSpeedup);
        log.info("   === 版本查询性能 ===");
        log.info("   传统逐个查询: {}ms", traditionalQueryTime);
        log.info("   Pipeline查询:  {}ms (提升 {}x)", pipelineQueryTime, querySpeedup);
        log.info("   原生批量查询: {}ms (提升 {}x)", batchQueryTime, batchQuerySpeedup);
        log.info("   批量发布查询: {}ms", batchReleasedQueryTime);
        log.info("   Pipeline vs 批量API (创建): {}x", (double) batchApiTime / pipelineTime);
        log.info("   Pipeline vs 批量API (查询): {}x", (double) batchQueryTime / pipelineQueryTime);

        // 验证性能提升符合预期
        assertTrue(pipelineSpeedup > 1.5, "Pipeline版本创建性能应该至少提升1.5倍");
        assertTrue(batchApiSpeedup > 1.5, "批量API版本创建性能应该至少提升1.5倍");
        assertTrue(querySpeedup > 2.0, "Pipeline查询性能应该至少提升2倍");
        assertTrue(batchQuerySpeedup > 2.0, "批量查询性能应该至少提升2倍");

        log.info("✅ 性能提升符合预期要求");

        // 清理测试数据
        List<Long> allIds = new ArrayList<>();
        allIds.addAll(savedObjects.stream().map(ModelObject::getId).collect(Collectors.toList()));
        allIds.addAll(savedObjects2.stream().map(ModelObject::getId).collect(Collectors.toList()));
        allIds.addAll(savedObjects3.stream().map(ModelObject::getId).collect(Collectors.toList()));
        objectService.forceDelete(allIds);
    }

    /**
     * 测试修订时更新属性功能
     */
    private void testReviseWithPropertiesImpl() {
        final String dateCode = String.valueOf(System.currentTimeMillis());
        log.info("=== 测试修订时更新属性功能 ===");

        ObjectService objectService = S.service(ObjectService.class);
        RevisionService revisionService = S.service(RevisionService.class);

        // 准备测试数据
        List<ItemRevision> testData = prepareTestData(dateCode, smallBatchSize, "revise-props-test");
        List<ItemRevision> savedObjects = objectService.saveAll(testData);

        // 必须先发布才能进行版本修订
        List<ItemRevision> releasedObjects = S.service(LifecycleService.class).moveToState(savedObjects, LifecycleState.STATE_RELEASED);

        TimerUtils.measureExecutionTime("修订时更新属性测试", () -> {
            // 测试单个修订并更新属性
            ItemRevision firstObj = releasedObjects.get(0);
            String originalName = firstObj.getName();
            
            java.util.Map<String, Object> properties = new java.util.HashMap<>();
            properties.put("name", "更新后的名称-" + dateCode);
            
            RevisionService.ReviseRequest<ItemRevision> request = new RevisionService.ReviseRequest<>(
                    firstObj, CopyRule.NoCopy, properties);
            
            ItemRevision revisedObj = revisionService.reviseByRequest(request);
            
            assertNotNull(revisedObj);
            assertEquals("B", revisedObj.getRevId());
            assertEquals("更新后的名称-" + dateCode, revisedObj.getName());
            assertNotEquals(originalName, revisedObj.getName());
            log.info("✅ 单个修订更新属性成功: {} -> {}", originalName, revisedObj.getName());

            // 测试批量修订并更新属性（统一属性）
            List<ItemRevision> batchObjects = releasedObjects.subList(1, 4);
            
            java.util.Map<String, Object> batchProperties = new java.util.HashMap<>();
            batchProperties.put("name", "批量更新的名称-" + dateCode);
            
            List<RevisionService.ReviseRequest<ItemRevision>> batchRequests = batchObjects.stream()
                    .map(obj -> new RevisionService.ReviseRequest<>(obj, CopyRule.NoCopy, batchProperties))
                    .collect(Collectors.toList());
            
            List<ItemRevision> revisedBatch = revisionService.reviseByRequests(batchRequests);
            
            assertEquals(3, revisedBatch.size());
            for (ItemRevision item : revisedBatch) {
                assertEquals("B", item.getRevId());
                assertEquals("批量更新的名称-" + dateCode, item.getName());
            }
            log.info("✅ 批量修订更新属性成功: {} 个对象", revisedBatch.size());

            // 测试批量修订并更新属性（每个对象不同属性）
            List<ItemRevision> individualObjects = releasedObjects.subList(4, 7);
            
            List<RevisionService.ReviseRequest<ItemRevision>> individualRequests = new ArrayList<>();
            for (int i = 0; i < individualObjects.size(); i++) {
                java.util.Map<String, Object> props = new java.util.HashMap<>();
                props.put("name", "独立更新-" + i + "-" + dateCode);
                individualRequests.add(new RevisionService.ReviseRequest<>(individualObjects.get(i), CopyRule.NoCopy, props));
            }
            
            List<ItemRevision> individualRevised = revisionService.reviseByRequests(individualRequests);
            
            assertEquals(3, individualRevised.size());
            for (int i = 0; i < individualRevised.size(); i++) {
                ItemRevision item = individualRevised.get(i);
                assertEquals("B", item.getRevId());
                assertEquals("独立更新-" + i + "-" + dateCode, item.getName());
            }
            log.info("✅ 批量修订独立属性更新成功: {} 个对象", individualRevised.size());
        });

        // 清理测试数据
        List<Long> allIds = new ArrayList<>();
        allIds.addAll(savedObjects.stream().map(ModelObject::getId).collect(Collectors.toList()));
        objectService.forceDelete(allIds);
    }

    /**
     * 测试Pipeline修订时更新属性功能
     */
    private void testPipelineReviseWithPropertiesImpl() {
        final String dateCode = String.valueOf(System.currentTimeMillis());
        log.info("=== 测试Pipeline修订时更新属性功能 ===");

        ObjectService objectService = S.service(ObjectService.class);

        // 准备测试数据
        List<ItemRevision> testData = prepareTestData(dateCode, smallBatchSize, "pipeline-props-test");
        List<ItemRevision> savedObjects = objectService.saveAll(testData);

        // 必须先发布才能进行版本修订
        List<ItemRevision> releasedObjects = S.service(LifecycleService.class).moveToState(savedObjects, LifecycleState.STATE_RELEASED);

        TimerUtils.measureExecutionTime("Pipeline修订更新属性测试", () -> {
            RevisionServicePipeline pipeline = RevisionService.pipeline();

            // 为每个对象准备不同的属性更新
            List<ResultFuture<ItemRevision>> futures = new ArrayList<>();
            for (int i = 0; i < releasedObjects.size(); i++) {
                ItemRevision obj = releasedObjects.get(i);
                
                java.util.Map<String, Object> properties = new java.util.HashMap<>();
                properties.put("name", "Pipeline更新-" + i + "-" + dateCode);
                
                RevisionService.ReviseRequest<ItemRevision> request = new RevisionService.ReviseRequest<>(
                        obj, CopyRule.NoCopy, properties);
                
                futures.add(pipeline.revise(request));
            }

            // 执行Pipeline
            pipeline.execute();

            // 验证结果
            for (int i = 0; i < futures.size(); i++) {
                try {
                    ItemRevision result = futures.get(i).get();
                    assertNotNull(result);
                    assertEquals("B", result.getRevId());
                    assertEquals("Pipeline更新-" + i + "-" + dateCode, result.getName());
                    log.info("对象 {} 修订后名称: {}", result.getCode(), result.getName());
                } catch (Exception e) {
                    throw new RuntimeException("验证Pipeline修订结果失败", e);
                }
            }

            log.info("✅ Pipeline修订更新属性测试通过: {} 个对象", futures.size());
        });

        // 清理测试数据
        List<Long> allIds = new ArrayList<>();
        allIds.addAll(savedObjects.stream().map(ModelObject::getId).collect(Collectors.toList()));
        objectService.forceDelete(allIds);
    }

    /**
     * 准备测试数据
     */
    private List<ItemRevision> prepareTestData(String dateCode, int count) {
        return prepareTestData(dateCode, count, "revision-pipeline-test");
    }

    private List<ItemRevision> prepareTestData(String dateCode, int count, String prefix) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(idx -> {
                    ItemRevision rev = ItemRevision.newModel(prefix + "-" + dateCode + "-" + idx, revId);
                    rev.setName("Revision测试对象-" + dateCode + "-" + idx);
                    return rev;
                })
                .collect(Collectors.toList());
    }
}