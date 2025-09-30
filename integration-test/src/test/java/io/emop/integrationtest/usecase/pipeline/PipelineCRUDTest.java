package io.emop.integrationtest.usecase.pipeline;

import io.emop.model.common.ItemRevision;
import io.emop.model.common.ModelObject;
import io.emop.model.common.UserContext;
import io.emop.integrationtest.util.TimerUtils;
import io.emop.service.S;
import io.emop.service.api.data.ObjectService;
import io.emop.service.api.data.ObjectServicePipeline;
import io.emop.service.api.pipeline.PipelineExecutor;
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
 * Pipeline批量操作测试用例
 * 演示Pipeline框架的使用方法和性能提升效果
 */
@RequiredArgsConstructor
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PipelineCRUDTest {

    private static final String revId = "50";
    private static final String dateCode = String.valueOf(System.currentTimeMillis());
    private static final int batchSize = 1000;

    @BeforeAll
    public void setup() {
        UserContext.setCurrentUser(new UserContext(100L, List.of("admin")));
    }

    @Test
    @Order(1)
    public void testTraditionalVsPipeline() {
        S.withStrongConsistency(this::testTraditionalVsPipelineImpl);
    }

    @Test
    @Order(2)
    public void testPipelineReadOperations() {
        S.withStrongConsistency(this::testPipelineReadOperationsImpl);
    }

    @Test
    @Order(3)
    public void testPipelineWriteOperations() {
        S.withStrongConsistency(this::testPipelineWriteOperationsImpl);
    }

    @Test
    @Order(4)
    public void testPipelineMixedOperations() {
        S.withStrongConsistency(this::testPipelineMixedOperationsImpl);
    }

    @Test
    @Order(5)
    public void testPipelineConfigurationOptions() {
        S.withStrongConsistency(this::testPipelineConfigurationOptionsImpl);
    }

    /**
     * 对比传统方式与Pipeline方式的性能差异
     */
    private void testTraditionalVsPipelineImpl() {
        log.info("=== 性能对比测试：传统方式 vs Pipeline方式 ===");

        // 先准备测试数据
        List<ItemRevision> testData = prepareTestData(batchSize);
        ObjectService objectService = S.service(ObjectService.class);
        List<ItemRevision> savedObjects = objectService.saveAll(testData);
        List<Long> testIds = savedObjects.stream().map(ModelObject::getId).collect(Collectors.toList());

        // 传统方式：逐个查询
        long time1 = TimerUtils.measureExecutionTime("传统方式逐个查询 " + batchSize + " 个对象", () -> {
            List<ModelObject> results = new ArrayList<>();
            for (Long id : testIds) {
                ModelObject obj = objectService.findById(id);
                results.add(obj);
            }
            assertEquals(batchSize, results.size());
        });

        // Pipeline方式：批量查询
        long time2 = TimerUtils.measureExecutionTime("Pipeline批量查询 " + batchSize + " 个对象", () -> {
            ObjectServicePipeline pipeline = ObjectService.pipeline();

            // 收集操作
            List<ResultFuture<ModelObject>> futures = new ArrayList<>();
            for (Long id : testIds) {
                ResultFuture<ModelObject> future = pipeline.findById(id);
                futures.add(future);
            }

            // 执行Pipeline
            PipelineExecutor.PipelineExecutionResult execution = pipeline.execute();

            // 获取结果
            List<ModelObject> results = new ArrayList<>();
            for (ResultFuture<ModelObject> future : futures) {
                try {
                    ModelObject obj = future.get();
                    results.add(obj);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            assertEquals(batchSize, results.size());

        });
        // 打印性能统计
        log.info("Pipeline性能统计: {}个操作 -> {}次RPC调用, 性能提升{}倍",
                batchSize, 1, time1 / time2);
        // 清理测试数据
        objectService.forceDelete(testIds);
    }

    /**
     * 测试Pipeline只读操作
     */
    private void testPipelineReadOperationsImpl() {
        log.info("=== Pipeline只读操作测试 ===");

        // 准备测试数据
        List<ItemRevision> testData = prepareTestData(100);
        ObjectService objectService = S.service(ObjectService.class);
        List<ItemRevision> savedObjects = objectService.saveAll(testData);
        List<Long> testIds = savedObjects.stream().map(ModelObject::getId).collect(Collectors.toList());

        TimerUtils.measureExecutionTime("Pipeline只读操作测试", () -> {
            ObjectServicePipeline pipeline = ObjectService.pipeline().readonly();

            // 混合各种查询操作
            List<ResultFuture<ModelObject>> findFutures = new ArrayList<>();
            List<ResultFuture<Boolean>> existsFutures = new ArrayList<>();

            for (int i = 0; i < testIds.size(); i++) {
                Long id = testIds.get(i);
                findFutures.add(pipeline.findById(id));
                existsFutures.add(pipeline.exists(id));
            }

            // 执行Pipeline
            PipelineExecutor.PipelineExecutionResult execution = pipeline.execute();

            // 验证结果
            for (int i = 0; i < testIds.size(); i++) {
                try {
                    ModelObject obj = findFutures.get(i).get();
                    Boolean exists = existsFutures.get(i).get();

                    assertNotNull(obj);
                    assertTrue(exists);
                    assertEquals(testIds.get(i), obj.getId());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            log.info("只读操作测试完成: {}个findById + {}个exists操作",
                    findFutures.size(), existsFutures.size());
        });

        // 清理测试数据
        objectService.forceDelete(testIds);
    }

    /**
     * 测试Pipeline写操作
     */
    private void testPipelineWriteOperationsImpl() {
        log.info("=== Pipeline写操作测试 ===");

        TimerUtils.measureExecutionTime("Pipeline批量保存测试", () -> {
            ObjectService objectService = S.service(ObjectService.class);
            ObjectServicePipeline pipeline = ObjectService.pipeline();

            List<ItemRevision> objectsToSave = prepareTestData(100);
            List<ResultFuture<ModelObject>> saveFutures = new ArrayList<>();

            // 收集保存操作
            for (ItemRevision obj : objectsToSave) {
                ResultFuture<ModelObject> future = pipeline.save(obj);
                saveFutures.add(future);
            }

            // 执行Pipeline
            PipelineExecutor.PipelineExecutionResult execution = pipeline.execute();

            // 验证结果
            List<Long> savedIds = new ArrayList<>();
            for (ResultFuture<ModelObject> future : saveFutures) {
                try {
                    ModelObject saved = future.get();
                    assertNotNull(saved);
                    assertNotNull(saved.getId());
                    savedIds.add(saved.getId());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            assertEquals(100, savedIds.size());
            log.info("批量保存测试完成: {}个对象保存成功", savedIds.size());

            // 清理测试数据
            objectService.forceDelete(savedIds);
        });
    }

    /**
     * 测试Pipeline混合操作
     */
    private void testPipelineMixedOperationsImpl() {
        log.info("=== Pipeline混合操作测试 ===");

        TimerUtils.measureExecutionTime("Pipeline混合操作测试", () -> {
            ObjectService objectService = S.service(ObjectService.class);

            // 先准备一些已存在的数据
            List<ItemRevision> existingData = prepareTestData(50);
            List<ItemRevision> existingObjects = objectService.saveAll(existingData);
            List<Long> existingIds = existingObjects.stream().map(ModelObject::getId).collect(Collectors.toList());

            ObjectServicePipeline pipeline = ObjectService.pipeline();

            // 混合操作：查询现有对象 + 保存新对象
            List<ResultFuture<ModelObject>> findFutures = new ArrayList<>();
            List<ResultFuture<ModelObject>> saveFutures = new ArrayList<>();

            // 查询现有对象
            for (Long id : existingIds) {
                findFutures.add(pipeline.findById(id));
            }

            // 保存新对象
            List<ItemRevision> newObjects = prepareTestData(30, "mixed-test");
            for (ItemRevision obj : newObjects) {
                saveFutures.add(pipeline.save(obj));
            }

            // 执行Pipeline
            PipelineExecutor.PipelineExecutionResult execution = pipeline.execute();

            // 验证结果
            List<Long> allIds = new ArrayList<>(existingIds);

            // 验证查询结果
            for (int i = 0; i < findFutures.size(); i++) {
                try {
                    ModelObject found = findFutures.get(i).get();
                    assertNotNull(found);
                    assertEquals(existingIds.get(i), found.getId());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            // 验证保存结果
            for (ResultFuture<ModelObject> future : saveFutures) {
                try {
                    ModelObject saved = future.get();
                    assertNotNull(saved);
                    assertNotNull(saved.getId());
                    allIds.add(saved.getId());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            log.info("混合操作测试完成: {}个查询 + {}个保存", findFutures.size(), saveFutures.size());

            // 清理所有测试数据
            objectService.forceDelete(allIds);
        });
    }

    /**
     * 测试Pipeline配置选项
     */
    private void testPipelineConfigurationOptionsImpl() {
        log.info("=== Pipeline配置选项测试 ===");

        ObjectService objectService = S.service(ObjectService.class);

        assertException(() -> {
            ObjectServicePipeline readonlyPipeline = ObjectService.pipeline().readonly();
            ItemRevision testObj = prepareTestData(1).get(0);
            readonlyPipeline.save(testObj);  // 这应该在execute时抛出异常
            readonlyPipeline.execute();
        });

        // 测试disableTransaction配置
        TimerUtils.measureExecutionTime("无事务Pipeline测试", () -> {
            ObjectServicePipeline noTxPipeline = ObjectService.pipeline().disableTransaction();

            List<ItemRevision> testObjects = prepareTestData(10, "no-tx-test");
            List<ResultFuture<ModelObject>> futures = new ArrayList<>();

            for (ItemRevision obj : testObjects) {
                futures.add(noTxPipeline.save(obj));
            }

            PipelineExecutor.PipelineExecutionResult execution = noTxPipeline.execute();

            List<Long> savedIds = new ArrayList<>();
            for (ResultFuture<ModelObject> future : futures) {
                try {
                    ModelObject saved = future.get();
                    savedIds.add(saved.getId());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            log.info("无事务Pipeline测试完成: {}个对象保存成功", savedIds.size());

            // 清理测试数据
            objectService.forceDelete(savedIds);
        });

        log.info("Pipeline配置选项测试完成");
    }

    /**
     * 准备测试数据
     */
    private List<ItemRevision> prepareTestData(int count) {
        return prepareTestData(count, "pipeline-test");
    }

    private List<ItemRevision> prepareTestData(int count, String prefix) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(idx -> {
                    ItemRevision rev = ItemRevision.newModel(prefix + "-" + dateCode + "-" + idx, revId);
                    rev.setName("Pipeline测试对象-" + dateCode + "-" + idx);
                    return rev;
                })
                .collect(Collectors.toList());
    }
}