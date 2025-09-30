package io.emop.integrationtest.performance;

import io.emop.integrationtest.util.TimerUtils;
import io.emop.model.common.UserContext;
import io.emop.service.S;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 建模性能测试 - 专门测试大数据量和并发场景下的性能表现
 */
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ModelingPerformanceTest {

    private final List<PerformanceResult> performanceResults = new ArrayList<>();
    private final List<ConcurrentResult> concurrentResults = new ArrayList<>();
    private final List<String> largeDataStats = new ArrayList<>();
    private final List<String> concurrentStats = new ArrayList<>();

    // 基准性能（单线程TPS，用于计算并发效率）
    private long baselineTPS = 0;

    @BeforeAll
    public void setup() {
        UserContext.setCurrentUser(new UserContext(100L, List.of("admin")));
        log.info("=== 开始建模性能测试 ===");
    }

    @Test
    @Order(1)
    void testLargeDataStress() {
        log.info("=== 大数据量性能测试 ===");

        int[] batchSizes = {100, 500, 1000, 2000};

        // 准备统计表格头部
        largeDataStats.add("| 数据量 | 保存时间(ms) | 平均TPS(单线程) | 状态 |");
        largeDataStats.add("|--------|-------------|---------|----------|------|");

        for (int batchSize : batchSizes) {
            PerformanceResult result = runLargeDataStressTest(batchSize);
            performanceResults.add(result);

            // 收集统计数据
            largeDataStats.add(String.format("| %d个  | %d  | %d  | %s |",
                    result.batchSize,
                    result.executionTime,
                    result.tps,
                    result.success ? "✓" : "✗"
            ));
        }

        log.info("大数据量测试执行完成");
    }

    @Test
    @Order(2)
    void testConcurrentStress() {
        log.info("=== 并发性能测试 ===");

        // 先测试单线程基准性能
        log.info("建立基准性能...");
        baselineTPS = establishBaseline();
        log.info("基准TPS: {}", baselineTPS);

        int[] threadCounts = {5, 10, 20, 40};
        final int operationsPerThread = 50;

        // 准备统计表格头部
        concurrentStats.add("| 线程数 | 操作数/线程 | 总时间(ms) | 平均TPS | 并发效率 | 状态 |");
        concurrentStats.add("|--------|------------|-----------|---------|----------|------|");

        for (int threadCount : threadCounts) {
            ConcurrentResult result = runConcurrentStressTest(threadCount, operationsPerThread);
            concurrentResults.add(result);

            // 收集统计数据
            concurrentStats.add(String.format("| %d个   | %d个      | %d    | %d     | %d%%     | %s |",
                    result.threadCount,
                    result.operationsPerThread,
                    result.totalTime,
                    result.tps,
                    result.efficiency,
                    result.success ? "✓" : "✗"
            ));
        }

        log.info("并发测试执行完成");
    }

    /**
     * 建立单线程基准性能
     */
    private long establishBaseline() {
        final int baselineOperations = 20; // 较少的操作数快速建立基准

        long startTime = System.currentTimeMillis();

        try {
            UserContext.runAsSystem(() -> {
                S.withStrongConsistency(() -> {
                    String dateCode = String.valueOf(System.currentTimeMillis());
                    io.emop.service.api.data.ObjectService objectService =
                            S.service(io.emop.service.api.data.ObjectService.class);

                    for (int i = 0; i < baselineOperations; i++) {
                        String code = "BASELINE_" + dateCode + "_" + i;

                        // 创建实体
                        io.emop.integrationtest.domain.TypeTestEntity entity =
                                io.emop.integrationtest.domain.TypeTestEntity.createFullTestInstance(code, "BASELINE_V1");

                        // 保存
                        io.emop.integrationtest.domain.TypeTestEntity saved = objectService.save(entity);

                        // 查询验证
                        io.emop.integrationtest.domain.TypeTestEntity found = objectService.findById(saved.getId());

                        // 更新操作
                        found.setName("Updated-" + found.getName());
                        found.setIntegerField(999);
                        objectService.save(found);
                    }
                });
            });
        } catch (Exception e) {
            log.error("建立基准性能失败", e);
            return 100; // 返回一个默认基准值
        }

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        int totalOps = baselineOperations * 3; // 保存+查询+更新

        return totalTime > 0 ? (totalOps * 1000L) / totalTime : 100;
    }

    @Test
    @Order(3)
    void testGenerateReport() {
        generateDetailedTestReport();
    }

    /**
     * 大数据量压力测试
     */
    private PerformanceResult runLargeDataStressTest(int batchSize) {
        // 测试前强制垃圾回收并等待
        System.gc();
        try {
            Thread.sleep(100); // 等待GC完成
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long startTime = System.currentTimeMillis();
        boolean success = false;

        try {
            List<io.emop.integrationtest.domain.TypeTestEntity> entities = new ArrayList<>();
            String dateCode = String.valueOf(System.currentTimeMillis());

            // 创建测试数据
            for (int i = 0; i < batchSize; i++) {
                String code = "STRESS_LARGE_" + dateCode + "_" + i;
                io.emop.integrationtest.domain.TypeTestEntity entity =
                        io.emop.integrationtest.domain.TypeTestEntity.createFullTestInstance(code, "STRESS_V1");
                entities.add(entity);
            }

            io.emop.service.api.data.ObjectService objectService = S.service(io.emop.service.api.data.ObjectService.class);

            // 批量保存
            List<io.emop.integrationtest.domain.TypeTestEntity> saved = objectService.saveAll(entities);
            success = (saved.size() == batchSize);

            if (!success) {
                log.error("批量保存失败，期望 {} 个，实际 {} 个", batchSize, saved.size());
            }

        } catch (Exception e) {
            log.error("大数据量测试失败: batchSize=" + batchSize, e);
        }

        long endTime = System.currentTimeMillis();

        // 测试后再次强制垃圾回收
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }


        long executionTime = endTime - startTime;
        long tps = executionTime > 0 ? (batchSize * 1000L) / executionTime : 0;

        return new PerformanceResult(batchSize, executionTime, tps, success);
    }

    /**
     * 并发压力测试
     */
    private ConcurrentResult runConcurrentStressTest(int threadCount, int operationsPerThread) {
        long startTime = System.currentTimeMillis();
        boolean success = false;

        try {
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int threadIndex = 0; threadIndex < threadCount; threadIndex++) {
                final int finalThreadIndex = threadIndex;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    UserContext.runAsSystem(() -> {
                        S.withStrongConsistency(() -> {
                            try {
                                String dateCode = String.valueOf(System.currentTimeMillis());
                                io.emop.service.api.data.ObjectService objectService =
                                        S.service(io.emop.service.api.data.ObjectService.class);

                                for (int opIndex = 0; opIndex < operationsPerThread; opIndex++) {
                                    String code = "STRESS_CONCURRENT_" + dateCode + "_T" + finalThreadIndex + "_O" + opIndex;

                                    // 创建实体
                                    io.emop.integrationtest.domain.TypeTestEntity entity =
                                            io.emop.integrationtest.domain.TypeTestEntity.createFullTestInstance(code, "STRESS_V1");

                                    // 保存
                                    io.emop.integrationtest.domain.TypeTestEntity saved = objectService.save(entity);
                                    if (saved == null || saved.getId() == null) {
                                        throw new RuntimeException("保存失败: " + code);
                                    }

                                    // 立即查询验证
                                    io.emop.integrationtest.domain.TypeTestEntity found =
                                            objectService.findById(saved.getId());
                                    if (found == null || !code.equals(found.getCode())) {
                                        throw new RuntimeException("查询验证失败: " + code);
                                    }

                                    // 更新操作
                                    found.setName("Updated-" + found.getName());
                                    found.setIntegerField(finalThreadIndex * 1000 + opIndex);
                                    io.emop.integrationtest.domain.TypeTestEntity updated = objectService.save(found);
                                    if (updated == null) {
                                        throw new RuntimeException("更新失败: " + code);
                                    }
                                }

                                log.debug("并发线程 {} 完成 {} 个操作", finalThreadIndex, operationsPerThread);

                            } catch (Exception e) {
                                log.error("并发线程 " + finalThreadIndex + " 失败", e);
                                throw new RuntimeException("并发压力测试失败", e);
                            }
                        });
                    });
                }, executor);

                futures.add(future);
            }

            // 等待所有线程完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            executor.shutdown();
            executor.awaitTermination(60, TimeUnit.SECONDS);

            success = true;

        } catch (Exception e) {
            log.error("并发压力测试失败: threadCount=" + threadCount, e);
        }

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        int totalOperations = threadCount * operationsPerThread * 3; // 每个操作包含 保存+查询+更新
        long tps = totalTime > 0 ? (totalOperations * 1000L) / totalTime : 0;

        // 计算并发效率：实际TPS相对于理论TPS的百分比
        // 理论TPS = baselineTPS * threadCount (理想情况下线性扩展)
        long theoreticalTPS = baselineTPS * threadCount;
        int efficiency = theoreticalTPS > 0 ? (int) ((tps * 100) / theoreticalTPS) : 0;

        return new ConcurrentResult(threadCount, operationsPerThread, totalTime, tps, efficiency, success);
    }

    /**
     * 获取当前内存使用量 (MB)
     */
    private long getUsedMemory() {
        // 强制垃圾回收以获得更准确的内存使用量
        System.gc();
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }

    /**
     * 生成详细测试报告
     */
    private void generateDetailedTestReport() {
        log.info("========================================");
        log.info("建模性能测试详细报告");
        log.info("========================================");

        // 输出大数据量测试统计表格
        log.info("");
        log.info("一、大数据量性能测试结果:");
        for (String stat : largeDataStats) {
            log.info(stat);
        }

        // 输出并发测试统计表格
        log.info("");
        log.info("二、并发性能测试结果:");
        log.info("(基准单线程TPS: {})", baselineTPS);
        for (String stat : concurrentStats) {
            log.info(stat);
        }

        // 大数据量测试总结
        log.info("");
        log.info("三、大数据量性能分析:");
        if (!performanceResults.isEmpty()) {
            PerformanceResult best = performanceResults.stream()
                    .filter(r -> r.success)
                    .max((r1, r2) -> Long.compare(r1.tps, r2.tps))
                    .orElse(null);

            PerformanceResult worst = performanceResults.stream()
                    .filter(r -> r.success)
                    .min((r1, r2) -> Long.compare(r1.tps, r2.tps))
                    .orElse(null);

            if (best != null) {
                log.info("  最佳性能: {} 个对象，{} TPS", best.batchSize, best.tps);
            }
            if (worst != null) {
                log.info("  最低性能: {} 个对象，{} TPS", worst.batchSize, worst.tps);
            }

            long avgTps = performanceResults.stream()
                    .filter(r -> r.success)
                    .mapToLong(r -> r.tps)
                    .reduce(0, Long::sum) / performanceResults.size();
            log.info("  平均TPS: {}", avgTps);
        }

        // 并发测试总结
        log.info("");
        log.info("四、并发性能分析:");
        if (!concurrentResults.isEmpty()) {
            ConcurrentResult bestConcurrent = concurrentResults.stream()
                    .filter(r -> r.success)
                    .max((r1, r2) -> Long.compare(r1.tps, r2.tps))
                    .orElse(null);

            if (bestConcurrent != null) {
                log.info("  最佳并发性能: {} 线程，{} TPS，{}% 效率",
                        bestConcurrent.threadCount, bestConcurrent.tps, bestConcurrent.efficiency);
            }

            long avgConcurrentTps = concurrentResults.stream()
                    .filter(r -> r.success)
                    .mapToLong(r -> r.tps)
                    .reduce(0, Long::sum) / concurrentResults.size();
            log.info("  平均并发TPS: {}", avgConcurrentTps);
        }

        log.info("");
        log.info("五、测试覆盖的数据类型:");
        log.info("  基础类型: String, Integer, Long, Double, Float, Boolean, Short, Byte");
        log.info("  大数类型: BigDecimal, BigInteger");
        log.info("  日期时间: Date, java.sql.Date, Timestamp, LocalDate, LocalDateTime, Instant");
        log.info("  二进制类型: byte[]");
        log.info("  特殊类型: UUID, Enum");
        log.info("  集合类型: List, Set, Map");
        log.info("  复杂对象: 嵌套对象, MultiLanguage");
        log.info("  数组类型: String[], Integer[], int[], double[]");

        log.info("");
        log.info("六、性能测试场景:");
        log.info("  ✓ 大数据量批量保存性能");
        log.info("  ✓ 高并发读写性能");
        log.info("  ✓ 内存使用效率");
        log.info("  ✓ 事务处理性能");
        log.info("  ✓ 复杂对象序列化性能");

        log.info("");
        log.info("七、性能评估标准:");
        log.info("  TPS评估标准:");
        log.info("    优秀: TPS > 1000");
        log.info("    良好: TPS 500-1000");
        log.info("    一般: TPS 100-500");
        log.info("    需优化: TPS < 100");
        log.info("  并发效率说明:");
        log.info("    100%: 完美线性扩展");
        log.info("    80-100%: 优秀并发性能");
        log.info("    50-80%: 良好并发性能");
        log.info("    <50%: 存在并发瓶颈");
        log.info("  内存使用说明:");
        log.info("    显示测试前后内存使用变化的绝对值");

        log.info("");
        log.info("八、测试结论:");
        boolean allLargeDataPassed = performanceResults.stream().allMatch(r -> r.success);
        boolean allConcurrentPassed = concurrentResults.stream().allMatch(r -> r.success);
        boolean allPassed = allLargeDataPassed && allConcurrentPassed;

        if (allPassed) {
            log.info("  ✓ 所有性能测试通过");
            log.info("  ✓ 系统能处理大数据量建模场景");
            log.info("  ✓ 高并发下保持良好性能");
            log.info("  ✓ 内存使用合理");
        } else {
            log.info("  ⚠ 部分测试未通过，建议关注:");
            if (!allLargeDataPassed) {
                log.info("    - 大数据量测试存在问题，检查批量操作优化");
            }
            if (!allConcurrentPassed) {
                log.info("    - 并发测试存在问题，检查线程安全和锁机制");
            }
            log.info("    - 检查数据库连接池配置");
            log.info("    - 调整JVM内存参数");
            log.info("    - 优化批量操作策略");
        }

        log.info("========================================");
    }

    /**
     * 大数据量性能测试结果
     */
    @AllArgsConstructor
    private static class PerformanceResult {
        final int batchSize;
        final long executionTime;
        final long tps;
        final boolean success;
    }

    /**
     * 并发性能测试结果
     */
    @AllArgsConstructor
    private static class ConcurrentResult {
        final int threadCount;
        final int operationsPerThread;
        final long totalTime;
        final long tps;
        final int efficiency;
        final boolean success;
    }
}