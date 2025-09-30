package io.emop.integrationtest.usecase.pipeline;

import io.emop.model.common.ItemRevision;
import io.emop.model.common.ModelObject;
import io.emop.model.common.UserContext;
import io.emop.model.query.Q;
import io.emop.integrationtest.util.TimerUtils;
import io.emop.service.S;
import io.emop.service.api.data.ObjectService;
import io.emop.service.api.data.ObjectServicePipeline;
import io.emop.service.api.pipeline.CompositePipeline;
import io.emop.service.api.pipeline.PipelineExecutor;
import io.emop.service.api.pipeline.ResultFuture;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.emop.integrationtest.util.Assertion.*;

/**
 * CompositePipeline跨服务协调测试
 * 重点验证：事务完整性、性能优势、单次RPC调用
 */
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CompositePipelineTest {

    private static final String revId = "60";
    private static final String dateCode = String.valueOf(System.currentTimeMillis());

    @BeforeAll
    public void setup() {
        UserContext.setCurrentUser(new UserContext(100L, List.of("admin")));
    }

    @Test
    @Order(1)
    public void testSingleRpcCallVerification() {
        S.withStrongConsistency(this::testSingleRpcCallVerificationImpl);
    }

    @Test
    @Order(2)
    public void testTransactionRollback() {
        S.withStrongConsistency(this::testTransactionRollbackImpl);
    }

    @Test
    @Order(3)
    public void testSequentialVsParallelPerformance() {
        S.withStrongConsistency(this::testSequentialVsParallelPerformanceImpl);
    }

    /**
     * 核心测试：验证CompositePipeline确实只产生单次RPC调用
     */
    private void testSingleRpcCallVerificationImpl() {
        log.info("=== CompositePipeline单次RPC调用验证 ===");

        TimerUtils.measureExecutionTime("单次RPC调用验证", () -> {
            ObjectService objectService = S.service(ObjectService.class);

            // 创建3个独立的Pipeline
            ObjectServicePipeline pipeline1 = ObjectService.pipeline();
            ObjectServicePipeline pipeline2 = ObjectService.pipeline();
            ObjectServicePipeline pipeline3 = ObjectService.pipeline();

            // 准备测试数据
            List<ItemRevision> batch1 = prepareTestData(10, "rpc-test-1");
            List<ItemRevision> batch2 = prepareTestData(8, "rpc-test-2");
            List<ItemRevision> batch3 = prepareTestData(5, "rpc-test-3");

            // 在不同Pipeline中添加操作
            List<ResultFuture<ModelObject>> futures1 = batch1.stream()
                    .map(pipeline1::save)
                    .collect(Collectors.toList());

            List<ResultFuture<ModelObject>> futures2 = batch2.stream()
                    .map(pipeline2::save)
                    .collect(Collectors.toList());

            List<ResultFuture<ModelObject>> futures3 = batch3.stream()
                    .map(pipeline3::save)
                    .collect(Collectors.toList());

            // 记录开始时间
            long startTime = System.currentTimeMillis();

            // 使用CompositePipeline统一执行
            CompositePipeline composite = CompositePipeline.of(pipeline1, pipeline2, pipeline3);

            log.info("CompositePipeline包含{}个操作，来自{}个子Pipeline",
                    composite.getTotalOperationCount(), composite.getSubPipelines().size());

            PipelineExecutor.PipelineExecutionResult execution = composite.execute();

            long executionTime = System.currentTimeMillis() - startTime;

            // 验证执行统计
            PipelineExecutor.ExecutionStats stats = execution.getStats();
            log.info("执行统计: {}个操作 -> {}次RPC调用, 执行时间: {}ms",
                    stats.getTotalOperations(), stats.getActualRpcCalls(), executionTime);

            // 关键验证：确保只有1次RPC调用
            assertEquals(1, stats.getActualRpcCalls(), "CompositePipeline应该只产生1次RPC调用");
            assertEquals(23, stats.getTotalOperations(), "总操作数应该是所有子Pipeline操作数之和");

            // 验证所有结果都正确
            List<Long> allSavedIds = new ArrayList<>();

            futures1.forEach(future -> {
                try {
                    ModelObject saved = future.get(1, TimeUnit.SECONDS);
                    assertNotNull(saved);
                    allSavedIds.add(saved.getId());
                } catch (Exception e) {
                    throw new RuntimeException("Future获取失败", e);
                }
            });

            futures2.forEach(future -> {
                try {
                    ModelObject saved = future.get(1, TimeUnit.SECONDS);
                    assertNotNull(saved);
                    allSavedIds.add(saved.getId());
                } catch (Exception e) {
                    throw new RuntimeException("Future获取失败", e);
                }
            });

            futures3.forEach(future -> {
                try {
                    ModelObject saved = future.get(1, TimeUnit.SECONDS);
                    assertNotNull(saved);
                    allSavedIds.add(saved.getId());
                } catch (Exception e) {
                    throw new RuntimeException("Future获取失败", e);
                }
            });

            assertEquals(23, allSavedIds.size(), "所有操作都应该成功");

            log.info("✅ 单次RPC验证通过: {}个操作通过1次RPC调用完成，用时{}ms",
                    allSavedIds.size(), executionTime);

            // 清理测试数据
            objectService.forceDelete(allSavedIds);
        });
    }

    /**
     * 事务回滚测试：验证CompositePipeline的事务完整性
     */
    private void testTransactionRollbackImpl() {
        log.info("=== CompositePipeline事务回滚测试 ===");

        TimerUtils.measureExecutionTime("事务回滚测试", () -> {
            ObjectService objectService = S.service(ObjectService.class);

            // 准备测试数据
            List<ItemRevision> validObjects = prepareTestData(3, "tx-valid");

            // 创建一个会导致异常的对象（故意设置null id来触发异常）
            ItemRevision invalidObject = new ItemRevision("invalidObjecType");
            invalidObject.setName("这个对象会导致保存失败");
            // 可以通过设置无效的业务字段来模拟业务异常

            ObjectServicePipeline pipeline1 = ObjectService.pipeline();
            ObjectServicePipeline pipeline2 = ObjectService.pipeline();

            // 在pipeline1中添加有效操作
            List<ResultFuture<ModelObject>> validFutures = validObjects.stream()
                    .map(pipeline1::save)
                    .collect(Collectors.toList());

            // 在pipeline2中添加会失败的操作
            ResultFuture<ModelObject> invalidFuture = pipeline2.save(invalidObject);

            try {
                // 使用CompositePipeline执行，期望事务回滚
                CompositePipeline composite = CompositePipeline.of(pipeline1, pipeline2);
                composite.execute();

                // 如果所有操作都成功了，验证结果
                List<Long> savedIds = new ArrayList<>();
                validFutures.forEach(future -> {
                    try {
                        ModelObject saved = future.get();
                        savedIds.add(saved.getId());
                    } catch (Exception e) {
                        log.error("获取结果失败", e);
                    }
                });

                try {
                    ModelObject invalidSaved = invalidFuture.get();
                    savedIds.add(invalidSaved.getId());
                } catch (Exception e) {
                    log.info("预期的异常操作失败: {}", e.getMessage());
                }

                log.info("事务测试: {}个对象保存成功", savedIds.size());

                // 清理数据
                if (!savedIds.isEmpty()) {
                    objectService.forceDelete(savedIds);
                }
            } catch (Exception e) {
                log.info("✅ 事务回滚测试通过，请忽略控制台错误: 捕获到预期异常 - {}", e.getMessage());
                // 验证没有对象被保存（事务回滚生效）
                assertEquals(0l, Q.result(ItemRevision.class).where("code like 'tx-valid-%'").count(), "事务回滚后不应该有任何对象被保存");
            }
        });
    }

    /**
     * 性能对比测试：sequential vs parallel执行模式
     */
    private void testSequentialVsParallelPerformanceImpl() {
        log.info("=== Sequential vs Parallel性能对比测试 ===");

        ObjectService objectService = S.service(ObjectService.class);
        List<ItemRevision> testData = prepareTestData(50, "perf-test");

        // 测试Parallel模式（默认）
        long parallelTime = TimerUtils.measureExecutionTime("Parallel模式", () -> {
            ObjectServicePipeline pipeline1 = ObjectService.pipeline();
            ObjectServicePipeline pipeline2 = ObjectService.pipeline();
            ObjectServicePipeline pipeline3 = ObjectService.pipeline();

            // 分配操作到不同pipeline
            List<ResultFuture<ModelObject>> futures1 = testData.subList(0, 17).stream()
                    .map(pipeline1::save)
                    .collect(Collectors.toList());

            List<ResultFuture<ModelObject>> futures2 = testData.subList(17, 34).stream()
                    .map(pipeline2::save)
                    .collect(Collectors.toList());

            List<ResultFuture<ModelObject>> futures3 = testData.subList(34, 50).stream()
                    .map(pipeline3::save)
                    .collect(Collectors.toList());

            // Parallel执行
            CompositePipeline parallelComposite = CompositePipeline.of(pipeline1, pipeline2, pipeline3);
            PipelineExecutor.PipelineExecutionResult parallelResult = parallelComposite.execute();

            // 验证结果并收集ID
            List<Long> savedIds = new ArrayList<>();
            futures1.forEach(f -> {
                try {
                    savedIds.add(f.get().getId());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            futures2.forEach(f -> {
                try {
                    savedIds.add(f.get().getId());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            futures3.forEach(f -> {
                try {
                    savedIds.add(f.get().getId());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            PipelineExecutor.ExecutionStats stats = parallelResult.getStats();
            log.info("Parallel模式统计: {}个操作, {}次RPC调用",
                    stats.getTotalOperations(), stats.getActualRpcCalls());

            // 清理
            objectService.forceDelete(savedIds);
        });

        // 测试Sequential模式
        long sequentialTime = TimerUtils.measureExecutionTime("Sequential模式", () -> {
            ObjectServicePipeline pipeline1 = ObjectService.pipeline();
            ObjectServicePipeline pipeline2 = ObjectService.pipeline();
            ObjectServicePipeline pipeline3 = ObjectService.pipeline();

            // 分配相同的操作
            List<ResultFuture<ModelObject>> futures1 = testData.subList(0, 17).stream()
                    .map(pipeline1::save)
                    .collect(Collectors.toList());

            List<ResultFuture<ModelObject>> futures2 = testData.subList(17, 34).stream()
                    .map(pipeline2::save)
                    .collect(Collectors.toList());

            List<ResultFuture<ModelObject>> futures3 = testData.subList(34, 50).stream()
                    .map(pipeline3::save)
                    .collect(Collectors.toList());

            // Sequential执行
            CompositePipeline sequentialComposite = CompositePipeline.of(pipeline1, pipeline2, pipeline3)
                    .sequential();
            PipelineExecutor.PipelineExecutionResult sequentialResult = sequentialComposite.execute();

            // 验证结果并收集ID
            List<Long> savedIds = new ArrayList<>();
            futures1.forEach(f -> {
                try {
                    savedIds.add(f.get().getId());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            futures2.forEach(f -> {
                try {
                    savedIds.add(f.get().getId());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            futures3.forEach(f -> {
                try {
                    savedIds.add(f.get().getId());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            PipelineExecutor.ExecutionStats stats = sequentialResult.getStats();
            log.info("Sequential模式统计: {}个操作, {}次RPC调用",
                    stats.getTotalOperations(), stats.getActualRpcCalls());

            // 清理
            objectService.forceDelete(savedIds);
        });

        // 性能对比分析
        double speedRatio = (double) sequentialTime / parallelTime;
        log.info("🏃 性能对比结果:");
        log.info("  Parallel模式: {}ms", parallelTime / 1_000_000);
        log.info("  Sequential模式: {}ms", sequentialTime / 1_000_000);
        log.info("  Sequential/Parallel比值: {}", speedRatio);

        if (speedRatio > 1.1) {
            log.info("  ✅ Sequential模式确实比Parallel模式慢 {}%", (speedRatio - 1) * 100);
        } else if (speedRatio < 0.9) {
            log.warn("  ⚠️  意外结果: Sequential模式比Parallel模式快，可能需要检查实现");
        } else {
            log.info("  📊 两种模式性能相近，差异在10%以内");
        }
    }

    private List<ItemRevision> prepareTestData(int count, String prefix) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(idx -> {
                    ItemRevision rev = ItemRevision.newModel(prefix + "-" + dateCode + "-" + idx, revId);
                    rev.setName("CompositePipeline测试对象-" + dateCode + "-" + idx);
                    return rev;
                })
                .collect(Collectors.toList());
    }
}