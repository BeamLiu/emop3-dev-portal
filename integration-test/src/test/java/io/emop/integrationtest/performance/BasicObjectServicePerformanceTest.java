package io.emop.integrationtest.performance;

import io.emop.model.common.ItemRevision;
import io.emop.model.common.UserContext;
import io.emop.model.query.Q;
import io.emop.integrationtest.util.TimerUtils;
import io.emop.service.S;
import io.emop.service.api.data.ObjectService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * ObjectService基础性能测试
 * 专注于最常用的操作：findById, save, query, findAllById
 */
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BasicObjectServicePerformanceTest {

    private static final String TEST_PREFIX = "PERF-" + System.currentTimeMillis();
    private static final int WARMUP_ROUNDS = 5;
    private static final int TEST_ROUNDS = 10;

    private final ObjectService objectService;
    private final List<Long> testObjectIds = new ArrayList<>();
    private int uniqueCounter = 1; // 确保每个对象都有唯一标识

    public BasicObjectServicePerformanceTest() {
        this.objectService = S.service(ObjectService.class);
    }

    @BeforeAll
    public void setup() {
        UserContext.setCurrentUser(new UserContext(100L, List.of("admin")));
        log.info("=== ObjectService 基础性能测试开始 ===");

        // 准备测试数据
        prepareTestData();
    }

    @Test
    @Order(1)
    public void testFindByIdPerformance() {
        testFindByIdPerformanceImpl();
    }

    @Test
    @Order(2)
    public void testFindAllByIdPerformance() {
        testFindAllByIdPerformanceImpl();
    }

    @Test
    @Order(3)
    public void testSavePerformance() {
        testSavePerformanceImpl();
    }

    @Test
    @Order(4)
    public void testSimpleQueryPerformance() {
        testSimpleQueryPerformanceImpl();
    }

    @Test
    @Order(5)
    public void testUpdatePerformance() {
        testUpdatePerformanceImpl();
    }

    @Test
    @Order(6)
    public void testBusinessKeyPerformance() {
        testBusinessKeyPerformanceImpl();
    }

    @Test
    @Order(7)
    public void testCachePerformance() {
        testCachePerformanceImpl();
    }

    /**
     * 准备测试数据：创建1000个ItemRevision对象
     */
    private void prepareTestData() {
        log.info("准备测试数据...");

        int testDataSize = 1000;
        List<ItemRevision> testObjects = TimerUtils.measureExecutionTime(
                "创建 " + testDataSize + " 个测试对象",
                () -> {
                    return IntStream.rangeClosed(1, testDataSize)
                            .mapToObj(i -> createTestObject(i))
                            .collect(Collectors.toList());
                }
        );

        // 批量保存测试数据
        List<ItemRevision> savedObjects = TimerUtils.measureExecutionTime(
                "批量保存 " + testDataSize + " 个对象",
                () -> objectService.saveAll(testObjects)
        );

        // 收集测试对象ID
        testObjectIds.addAll(savedObjects.stream()
                .map(ItemRevision::getId)
                .collect(Collectors.toList()));

        log.info("测试数据准备完成，共 {} 个对象", testObjectIds.size());
    }

    /**
     * 测试 findById 性能
     */
    private void testFindByIdPerformanceImpl() {
        log.info("\n--- 测试 findById 性能 ---");

        // 预热
        warmup("findById预热", () -> {
            Long randomId = getRandomTestId();
            objectService.findById(randomId);
        });

        // 正式测试
        List<Long> latencies = new ArrayList<>();

        for (int i = 0; i < TEST_ROUNDS; i++) {
            Long randomId = getRandomTestId();

            long startTime = System.nanoTime();
            ItemRevision result = objectService.findById(randomId);
            long endTime = System.nanoTime();

            long latencyNs = endTime - startTime;
            latencies.add(latencyNs);

            if (result == null) {
                log.warn("查询结果为空: {}", randomId);
            }
        }

        printPerformanceStats("findById", latencies);
        double avgLatencyMs = latencies.stream().mapToLong(l -> l).average().orElse(0.0) / 1_000_000.0;
        log.info("平均每次查找时间： {} ms", avgLatencyMs);
    }

    /**
     * 测试 findAllById 批量查询性能
     */
    private void testFindAllByIdPerformanceImpl() {
        log.info("\n--- 测试 findAllById 批量查询性能 ---");

        int[] batchSizes = {10, 50, 100, 500};

        for (int batchSize : batchSizes) {
            log.info("测试批次大小: {}", batchSize);

            // 预热
            warmup("findAllById预热(batch=" + batchSize + ")", () -> {
                List<Long> ids = getRandomTestIds(batchSize);
                objectService.findAllById(ids);
            });

            // 正式测试
            List<Long> latencies = new ArrayList<>();

            for (int i = 0; i < TEST_ROUNDS; i++) {
                List<Long> ids = getRandomTestIds(batchSize);

                long startTime = System.nanoTime();
                List<ItemRevision> results = objectService.findAllById(ids);
                long endTime = System.nanoTime();

                long latencyNs = endTime - startTime;
                latencies.add(latencyNs);

                if (results.size() != batchSize) {
                    log.warn("期待 {} 个结果，实际 {} 个", batchSize, results.size());
                }
            }

            printPerformanceStats("findAllById(batch=" + batchSize + ")", latencies);

            // 计算平均每个对象的查询时间
            double avgLatencyMs = latencies.stream().mapToLong(l -> l).average().orElse(0.0) / 1_000_000.0;
            double avgPerObjectMs = avgLatencyMs / batchSize;
            log.info("  平均每个对象查询时间: {} ms", avgPerObjectMs);
        }
    }

    /**
     * 测试 save 保存性能
     */
    private void testSavePerformanceImpl() {
        log.info("\n--- 测试 save 保存性能 ---");

        // 测试新对象保存
        log.info("测试新对象保存性能:");
        testSaveNewObjects();

        // 测试更新对象保存
        log.info("测试更新对象保存性能:");
        testSaveExistingObjects();
    }

    private void testSaveNewObjects() {
        // 预热 - 使用特殊的预热数据
        warmup("save新对象预热", () -> {
            ItemRevision newObj = createTestObject(getNextUniqueIndex() + 200000); // 确保与测试数据不冲突
            try {
                objectService.save(newObj);
            } catch (Exception e) {
                log.debug("预热过程中的异常（可忽略）: {}", e.getMessage());
            }
        });

        // 正式测试
        List<Long> latencies = new ArrayList<>();

        for (int i = 0; i < TEST_ROUNDS; i++) {
            ItemRevision newObj = createTestObject(getNextUniqueIndex() + 300000); // 使用更大的基数避免冲突

            long startTime = System.nanoTime();
            try {
                ItemRevision saved = objectService.save(newObj);
                long endTime = System.nanoTime();

                long latencyNs = endTime - startTime;
                latencies.add(latencyNs);

                if (saved.getId() == null) {
                    log.warn("保存后对象ID为空");
                }
            } catch (Exception e) {
                log.error("保存新对象时出错: {}", e.getMessage());
                // 重新测量一次，使用不同的数据
                ItemRevision retryObj = createTestObject(getNextUniqueIndex() + 400000);
                long retryStart = System.nanoTime();
                ItemRevision retrySaved = objectService.save(retryObj);
                long retryEnd = System.nanoTime();
                latencies.add(retryEnd - retryStart);
            }
        }

        printPerformanceStats("save(新对象)", latencies);
        double avgLatencyMs = latencies.stream().mapToLong(l -> l).average().orElse(0.0) / 1_000_000.0;
        log.info("  平均每个save(新对象): {} ms", avgLatencyMs);
    }

    private void testSaveExistingObjects() {
        // 预热
        warmup("save更新对象预热", () -> {
            Long randomId = getRandomTestId();
            ItemRevision obj = objectService.findById(randomId);
            if (obj != null) {
                obj.setName("Warmup-Updated-" + System.currentTimeMillis());
                try {
                    objectService.save(obj);
                } catch (Exception e) {
                    log.debug("预热过程中的异常（可忽略）: {}", e.getMessage());
                }
            }
        });

        // 正式测试
        List<Long> latencies = new ArrayList<>();

        for (int i = 0; i < TEST_ROUNDS; i++) {
            Long randomId = getRandomTestId();
            ItemRevision obj = objectService.findById(randomId);

            if (obj != null) {
                obj.setName("Updated-" + System.currentTimeMillis() + "-" + i);

                long startTime = System.nanoTime();
                try {
                    ItemRevision saved = objectService.save(obj);
                    long endTime = System.nanoTime();

                    long latencyNs = endTime - startTime;
                    latencies.add(latencyNs);

                    if (!saved.getName().contains("Updated-")) {
                        log.warn("更新未生效");
                    }
                } catch (Exception e) {
                    log.error("更新对象时出错: {}", e.getMessage());
                    long endTime = System.nanoTime();
                    latencies.add(endTime - startTime); // 即使失败也记录时间
                }
            } else {
                log.warn("测试对象 {} 不存在", randomId);
            }
        }

        printPerformanceStats("save(更新对象)", latencies);
        double avgLatencyMs = latencies.stream().mapToLong(l -> l).average().orElse(0.0) / 1_000_000.0;
        log.info("  平均每个save(更新对象): {} ms", avgLatencyMs);
    }

    /**
     * 测试简单查询性能
     */
    private void testSimpleQueryPerformanceImpl() {
        log.info("\n--- 测试简单查询性能 ---");

        // 预热
        warmup("query预热", () -> {
            Q.result(ItemRevision.class)
                    .where("code like '" + TEST_PREFIX + "%'")
                    .limit(10)
                    .query();
        });

        // 正式测试
        List<Long> latencies = new ArrayList<>();

        for (int i = 0; i < TEST_ROUNDS; i++) {
            long startTime = System.nanoTime();
            List<ItemRevision> results = Q.result(ItemRevision.class)
                    .where("code like '" + TEST_PREFIX + "%'")
                    .limit(50)
                    .query();
            long endTime = System.nanoTime();

            long latencyNs = endTime - startTime;
            latencies.add(latencyNs);

            if (results.isEmpty()) {
                log.warn("查询结果为空");
            }
        }

        printPerformanceStats("简单查询(LIKE + LIMIT)", latencies);
        double avgLatencyMs = latencies.stream().mapToLong(l -> l).average().orElse(0.0) / 1_000_000.0;
        log.info("  平均每个简单查询(LIKE + LIMIT): {} ms", avgLatencyMs);
    }

    /**
     * 测试缓存效果
     */
    private void testCachePerformanceImpl() {
        log.info("\n--- 测试缓存效果 ---");

        Long testId = getRandomTestId();

        // 清理可能的缓存（这里假设有清理缓存的方法，实际需要根据你的缓存实现）
        // cacheService.evict(testId);

        // 测试缓存未命中（首次查询）
        List<Long> cacheMissLatencies = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            // 每次使用不同的ID，确保缓存未命中
            Long randomId = getRandomTestId();

            long startTime = System.nanoTime();
            objectService.findById(randomId);
            long endTime = System.nanoTime();

            cacheMissLatencies.add(endTime - startTime);
        }

        // 测试缓存命中（重复查询同一对象）
        List<Long> cacheHitLatencies = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            long startTime = System.nanoTime();
            objectService.findById(testId);
            long endTime = System.nanoTime();

            cacheHitLatencies.add(endTime - startTime);
        }

        printPerformanceStats("缓存未命中", cacheMissLatencies);
        printPerformanceStats("缓存命中", cacheHitLatencies);

        // 计算缓存加速比
        double avgCacheMissMs = cacheMissLatencies.stream().mapToLong(l -> l).average().orElse(0.0) / 1_000_000.0;
        double avgCacheHitMs = cacheHitLatencies.stream().mapToLong(l -> l).average().orElse(0.0) / 1_000_000.0;

        if (avgCacheHitMs > 0) {
            double speedupRatio = avgCacheMissMs / avgCacheHitMs;
            log.info("缓存加速比: {}x", speedupRatio);
        }
    }

    /**
     * 测试update和fastUpdate性能对比
     */
    private void testUpdatePerformanceImpl() {
        log.info("\n--- 测试 update 和 fastUpdate 性能对比 ---");

        // 1. 测试带版本控制的update性能
        testUpdateWithVersionControl();

        // 2. 测试轻量级fastUpdate性能
        testFastUpdate();
    }

    private void testUpdateWithVersionControl() {
        log.info("测试 update(带版本控制) 性能:");

        // 预热
        warmup("update预热", () -> {
            Long randomId = getRandomTestId();
            ItemRevision obj = objectService.findById(randomId);
            if (obj != null) {
                try {
                    Map<String, Object> updateData = Map.of(
                            "name", "UpdateTest-" + System.currentTimeMillis(),
                            "priority", 1
                    );
                    objectService.update(obj.getId(), obj.get_version(), updateData);
                } catch (Exception e) {
                    log.debug("预热过程中的异常（可忽略）: {}", e.getMessage());
                }
            }
        });

        // 先准备所有要更新的对象（不计入更新时间）
        List<Long> randomIds = getRandomTestIds(TEST_ROUNDS);
        List<ItemRevision> objectsToUpdate = objectService.findAllById(randomIds);

        // 正式测试 - 只计算update时间
        List<Long> latencies = new ArrayList<>();
        long totalStartTime = System.nanoTime();

        for (int i = 0; i < objectsToUpdate.size(); i++) {
            ItemRevision obj = objectsToUpdate.get(i);
            Map<String, Object> updateData = Map.of(
                    "name", "UpdateTest-" + System.currentTimeMillis() + "-" + i,
                    "priority", i % 3,
                    "updateCounter", i
            );

            long startTime = System.nanoTime();
            try {
                ItemRevision updated = (ItemRevision) objectService.update(obj.getId(), obj.get_version(), updateData);
                long endTime = System.nanoTime();

                long latencyNs = endTime - startTime;
                latencies.add(latencyNs);

                if (updated == null) {
                    log.warn("update返回结果为空");
                }
            } catch (Exception e) {
                log.error("update操作时出错: {}", e.getMessage());
                long endTime = System.nanoTime();
                latencies.add(endTime - startTime);
            }
        }
        long totalEndTime = System.nanoTime();

        printPerformanceStats("update(带版本控制)", latencies);

        double totalTimeMs = (totalEndTime - totalStartTime) / 1_000_000.0;
        log.info("  总更新时间: {} ms", String.format("%.3f", totalTimeMs));
        log.info("  单个更新耗时: {} ms", String.format("%.1f", (totalTimeMs / objectsToUpdate.size())));
    }

    private void testFastUpdate() {
        log.info("测试 fastUpdate(轻量级) 性能:");

        // 预热
        warmup("fastUpdate预热", () -> {
            Long randomId = getRandomTestId();
            try {
                Map<String, Object> updateData = Map.of(
                        "name", "FastUpdateTest-" + System.currentTimeMillis(),
                        "priority", 2
                );
                objectService.fastUpdate(randomId, updateData);
            } catch (Exception e) {
                log.debug("预热过程中的异常（可忽略）: {}", e.getMessage());
            }
        });

        // 获取测试对象ID（不计入更新时间）
        List<Long> randomIds = getRandomTestIds(TEST_ROUNDS);

        // 正式测试 - 只计算fastUpdate时间
        List<Long> latencies = new ArrayList<>();
        long totalStartTime = System.nanoTime();

        for (int i = 0; i < randomIds.size(); i++) {
            Long id = randomIds.get(i);
            Map<String, Object> updateData = Map.of(
                    "name", "FastUpdateTest-" + System.currentTimeMillis() + "-" + i,
                    "priority", i % 3,
                    "fastUpdateCounter", i
            );

            long startTime = System.nanoTime();
            try {
                int affectedRows = objectService.fastUpdate(id, updateData);
                long endTime = System.nanoTime();

                long latencyNs = endTime - startTime;
                latencies.add(latencyNs);

                if (affectedRows == 0) {
                    log.warn("fastUpdate未影响任何行: {}", id);
                }
            } catch (Exception e) {
                log.error("fastUpdate操作时出错: {}", e.getMessage());
                long endTime = System.nanoTime();
                latencies.add(endTime - startTime);
            }
        }
        long totalEndTime = System.nanoTime();

        printPerformanceStats("fastUpdate(轻量级)", latencies);

        double totalTimeMs = (totalEndTime - totalStartTime) / 1_000_000.0;
        log.info("  总更新时间: {} ms", String.format("%.3f", totalTimeMs));
        log.info("  单个更新耗时: {} ms", String.format("%.1f", (totalTimeMs / randomIds.size())));
    }

    /**
     * 测试businessKey相关API性能
     */
    private void testBusinessKeyPerformanceImpl() {
        log.info("\n--- 测试 businessKey 相关API性能 ---");

        // 1. 测试upsertByBusinessKey - 新建场景
        testUpsertByBusinessKeyCreate();

        // 2. 测试upsertByBusinessKey - 更新场景
        testUpsertByBusinessKeyUpdate();
    }

    private void testUpsertByBusinessKeyCreate() {
        log.info("测试 upsertByBusinessKey(新建) 性能:");

        // 预热
        warmup("upsertByBusinessKey新建预热", () -> {
            ItemRevision newObj = createTestObject(getNextUniqueIndex() + 500000);
            try {
                objectService.upsertByBusinessKey(newObj);
            } catch (Exception e) {
                log.debug("预热过程中的异常（可忽略）: {}", e.getMessage());
            }
        });

        // 先创建所有测试对象（不计入upsert时间）
        List<ItemRevision> testObjects = new ArrayList<>();
        for (int i = 0; i < TEST_ROUNDS; i++) {
            testObjects.add(createTestObject(getNextUniqueIndex() + 600000 + i));
        }

        // 正式测试 - 只计算upsert时间
        List<Long> latencies = new ArrayList<>();
        long totalStartTime = System.nanoTime();

        for (ItemRevision obj : testObjects) {
            long startTime = System.nanoTime();
            try {
                ItemRevision result = objectService.upsertByBusinessKey(obj);
                long endTime = System.nanoTime();

                long latencyNs = endTime - startTime;
                latencies.add(latencyNs);

                if (result == null || result.getId() == null) {
                    log.warn("upsertByBusinessKey返回结果异常");
                }
            } catch (Exception e) {
                log.error("upsertByBusinessKey新建时出错: {}", e.getMessage());
                long endTime = System.nanoTime();
                latencies.add(endTime - startTime);
            }
        }
        long totalEndTime = System.nanoTime();

        printPerformanceStats("upsertByBusinessKey(新建)", latencies);

        double totalTimeMs = (totalEndTime - totalStartTime) / 1_000_000.0;
        log.info("  总upsert时间: {} ms", String.format("%.3f", totalTimeMs));
        log.info("  单个更新耗时: {} ms", String.format("%.1f", (totalTimeMs / testObjects.size())));
    }

    private void testUpsertByBusinessKeyUpdate() {
        log.info("测试 upsertByBusinessKey(更新) 性能:");

        // 预热
        warmup("upsertByBusinessKey更新预热", () -> {
            Long randomId = getRandomTestId();
            ItemRevision obj = objectService.findById(randomId);
            if (obj != null) {
                obj.setName("UpsertUpdateTest-" + System.currentTimeMillis());
                try {
                    objectService.upsertByBusinessKey(obj);
                } catch (Exception e) {
                    log.debug("预热过程中的异常（可忽略）: {}", e.getMessage());
                }
            }
        });

        // 先获取并准备所有要更新的对象（不计入upsert时间）
        List<Long> randomIds = getRandomTestIds(Math.min(TEST_ROUNDS, 50)); // 限制数量避免过多
        List<ItemRevision> objectsToUpdate = objectService.findAllById(randomIds);

        // 修改对象属性（不计入upsert时间）
        for (int i = 0; i < objectsToUpdate.size(); i++) {
            ItemRevision obj = objectsToUpdate.get(i);
            obj.setName("UpsertUpdateTest-" + System.currentTimeMillis() + "-" + i);
            obj.set("upsertUpdateCounter", i);
        }

        // 正式测试 - 只计算upsert时间
        List<Long> latencies = new ArrayList<>();
        long totalStartTime = System.nanoTime();

        for (ItemRevision obj : objectsToUpdate) {
            long startTime = System.nanoTime();
            try {
                ItemRevision result = objectService.upsertByBusinessKey(obj);
                long endTime = System.nanoTime();

                long latencyNs = endTime - startTime;
                latencies.add(latencyNs);

                if (result == null) {
                    log.warn("upsertByBusinessKey返回结果为空");
                }
            } catch (Exception e) {
                log.error("upsertByBusinessKey更新时出错: {}", e.getMessage());
                long endTime = System.nanoTime();
                latencies.add(endTime - startTime);
            }
        }
        long totalEndTime = System.nanoTime();

        printPerformanceStats("upsertByBusinessKey(更新)", latencies);

        double totalTimeMs = (totalEndTime - totalStartTime) / 1_000_000.0;
        log.info("  总upsert时间: {} ms", String.format("%.3f", totalTimeMs));
        log.info("  单个更新耗时: {} ms", String.format("%.1f", (totalTimeMs / objectsToUpdate.size())));
    }

    // ========== 辅助方法 ==========

    private ItemRevision createTestObject(int index) {
        // 确保每个对象都有唯一的code和revId组合
        String uniqueCode = TEST_PREFIX + "-" + index;
        String uniqueRevId = String.valueOf((index % 10) + 1); // 1-10 而不是 0-9

        ItemRevision revision = ItemRevision.newModel(uniqueCode, uniqueRevId);
        revision.setName("PerfTest-Object-" + index);
        revision.set("category", "performance-test");
        revision.set("priority", index % 3);
        return revision;
    }

    private synchronized int getNextUniqueIndex() {
        return uniqueCounter++;
    }

    private Long getRandomTestId() {
        if (testObjectIds.isEmpty()) {
            throw new IllegalStateException("测试数据未准备，无可用的测试对象ID");
        }
        int randomIndex = new Random().nextInt(testObjectIds.size());
        return testObjectIds.get(randomIndex);
    }

    private List<Long> getRandomTestIds(int count) {
        if (testObjectIds.size() < count) {
            log.warn("测试数据不足: 需要{}个, 实际{}个", count, testObjectIds.size());
            // 如果数据不足，返回所有可用的ID
            return new ArrayList<>(testObjectIds);
        }

        List<Long> shuffled = new ArrayList<>(testObjectIds);
        Collections.shuffle(shuffled);
        return new ArrayList<>(shuffled.subList(0, count));
    }

    private void warmup(String operationName, Runnable operation) {
        log.debug("执行 {} 预热...", operationName);
        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            operation.run();
        }
    }

    private void printPerformanceStats(String operation, List<Long> latenciesNs) {
        if (latenciesNs.isEmpty()) {
            log.warn("{}: 无测试数据", operation);
            return;
        }

        // 转换为毫秒
        List<Double> latenciesMs = latenciesNs.stream()
                .mapToDouble(ns -> ns / 1_000_000.0)
                .boxed()
                .sorted()
                .collect(Collectors.toList());

        double avg = latenciesMs.stream().mapToDouble(d -> d).average().orElse(0.0);
        double min = latenciesMs.get(0);
        double max = latenciesMs.get(latenciesMs.size() - 1);
        double p50 = latenciesMs.get((int) (latenciesMs.size() * 0.5));
        double p95 = latenciesMs.get((int) (latenciesMs.size() * 0.95));
        double p99 = latenciesMs.get((int) (latenciesMs.size() * 0.99));

        log.info("{} 性能统计 ({}次测试):", operation, latenciesMs.size());
        log.info("  平均: {} ms", String.format("%.3f", avg));
        log.info("  最小: {} ms", String.format("%.3f", min));
        log.info("  最大: {} ms", String.format("%.3f", max));
        log.info("  P50:  {} ms", String.format("%.3f", p50));
        log.info("  P95:  {} ms", String.format("%.3f", p95));
        log.info("  P99:  {} ms", String.format("%.3f", p99));

        // 性能评估
        if (avg < 10) {
            log.info("  评估: ✅ 优秀 (< 10ms)");
        } else if (avg < 50) {
            log.info("  评估: ✅ 良好 (< 50ms)");
        } else if (avg < 100) {
            log.info("  评估: ⚠️  一般 (< 100ms)");
        } else {
            log.info("  评估: ❌ 需要优化 (>= 100ms)");
        }
    }
}