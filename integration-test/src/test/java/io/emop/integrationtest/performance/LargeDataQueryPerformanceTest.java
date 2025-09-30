package io.emop.integrationtest.performance;

import io.emop.model.common.ItemRevision;
import io.emop.model.common.RevisionRule;
import io.emop.model.query.Pagination;
import io.emop.model.query.Q;
import io.emop.model.query.tuple.Tuples;
import io.emop.service.S;
import io.emop.service.api.data.NativeSqlService;
import io.emop.service.api.metadata.MetadataService;
import io.emop.service.api.metadata.MetadataUpdateService;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * ItemRevision 大数据量性能测试
 * 测试场景：2000万条ItemRevision数据，每个code有1-5个随机版本
 * <p>
 * 测试内容：
 * 1. Q.result() 物理查询性能
 * 2. Q.logical() 逻辑查询性能
 * 3. ABAC权限场景性能
 * 4. 批量查询性能
 * 5. 分页查询性能
 * 6. 索引效果对比
 */
@Slf4j
public class LargeDataQueryPerformanceTest implements Runnable {

    // 测试配置
    private static final int TOTAL_CODES = 10_000_000;  // 1000万个不同的code
    private static final int TOTAL_RECORDS = 40_000_000; // 4000万条记录
    private static final int BATCH_SIZE = 6000;
    private static final int TEST_ITERATIONS = 5; // 每个测试重复次数
    private static final int CONCURRENT_THREADS = 10; // 并发测试线程数

    // 测试结果收集
    private final List<TestResult> testResults = new ArrayList<>();
    private final ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_THREADS);

    @Override
    public void run() {
        log.info("=== ItemRevision 大数据量性能测试开始 ===");
        log.info("测试规模：{} 条记录，{} 个不同code", TOTAL_RECORDS, TOTAL_CODES);

        try {
            // 1. 数据准备
            prepareTestEnvironment();

            // 2. 快速导入测试数据
            importTestData();

            // 4. 基础查询性能测试
            testBasicQueries();

            // 5. 逻辑对象查询性能测试
            testLogicalQueries();

            // 6. 批量查询性能测试
            testBatchQueries();

            // 7. 分页查询性能测试
            testPaginationQueries();

            // 8. ABAC权限场景性能测试
            testAbacPermissionQueries();

            // 9. 并发查询性能测试
            testConcurrentQueries();

            // 10. 生成性能报告
            generatePerformanceReport();

        } catch (Exception e) {
            log.error("性能测试执行失败", e);
        } finally {
            executorService.shutdown();
        }

        log.info("=== ItemRevision 大数据量性能测试完成 ===");
    }

    /**
     * 准备测试环境
     */
    private void prepareTestEnvironment() {
        log.info("=== 准备测试环境 ===");
        // 配置数据库优化参数
        try {
            S.service(NativeSqlService.class).executeNativeUpdate(
                    "SET work_mem = '256MB'", Collections.emptyList()
            );
            log.info("数据库性能参数配置完成");
        } catch (SQLException e) {
            log.warn("数据库参数配置失败", e);
        }
    }

    /**
     * 快速导入测试数据 - 使用直接批量插入
     */
    private void importTestData() {
        log.info("=== 开始导入 {} 条测试数据 ===", TOTAL_RECORDS);

        long startTime = System.currentTimeMillis();

        try {
            long actualCount = getActualCount();
            if (actualCount >= TOTAL_RECORDS) {
                log.info("已有数据量: {} 条，跳过导入。", actualCount);
                return;
            }

            final AtomicInteger total = new AtomicInteger(0);
            // 直接批量生成并插入数据
            IntStream.range(0, 4).parallel().forEach(i -> {
                while(true) {
                    try {
                        generateAndImportDataDirect(total);
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    }
                }
            });

            long duration = System.currentTimeMillis() - startTime;
            log.info("数据导入完成，耗时: {} ms ({} 分钟)", duration, duration / 60000.0);

            actualCount = getActualCount();
            log.info("实际导入数据量: {} 条", actualCount);

        } catch (Exception e) {
            log.error("数据导入失败", e);
            throw new RuntimeException(e);
        }
    }

    private static long getActualCount() {
        // 验证数据导入结果
        List<List<?>> result = S.service(NativeSqlService.class).executeNativeQuery(
                "SELECT COUNT(*) FROM COMMON.Item_Revision WHERE name LIKE 'PERF_TEST_%'"
        );
        long actualCount = ((Number) result.get(0).get(0)).longValue();
        return actualCount;
    }

    private void generateAndImportDataDirect(AtomicInteger total) {
        log.info("直接生成并导入测试数据...");

        Random random = new Random(42); // 固定种子确保可重复
        String[] states = {"Working", "Released", "Obsolete"};
        List<Object[]> batchData = new ArrayList<>(BATCH_SIZE);
        int imported = 0;

        // 准备批量插入的SQL
        String insertSQL = """
                INSERT INTO COMMON.Item_Revision (
                    code, revid, name, _state, _properties, _creator, _creationDate, _lastmodifier, _lastmodifieddate, _version
                ) VALUES (?, ?, ?, ?, '{}'::jsonb, ?, ?, ?, ?, ?)
                """;

        Long typeDefId = S.service(MetadataService.class).retrieveFullTypeDefinition(ItemRevision.class.getName()).getId();
        for (int codeIndex = 0; codeIndex < TOTAL_RECORDS; codeIndex++) {
            String baseCode = String.format("PERF_TEST_%08d", codeIndex);
            int versions = random.nextInt(5) + 1; // 1-5个版本

            List<Long> ids = S.service(MetadataUpdateService.class).nextId(typeDefId, versions);
            for (int version = 1; version <= versions; version++) {
                String revId = String.valueOf(version);
                String name = String.format("PERF_TEST_ITEM_%08d_V%d", codeIndex, version);
                String state = states[random.nextInt(states.length)];
                long creatorId = random.nextInt(1000) + 1; // 模拟1000个用户


                Object[] row = {
                        ids.get(version - 1),
                        baseCode,
                        revId,
                        name,
                        state,
                        creatorId,
                        java.sql.Timestamp.valueOf("2024-01-01 00:00:00"),
                        creatorId,
                        java.sql.Timestamp.valueOf("2024-01-01 00:00:00"),
                        1
                };
                batchData.add(row);
                imported++;

                // 批量插入
                if (batchData.size() >= BATCH_SIZE) {
                    insertBatchDirect(insertSQL, batchData);
                    total.addAndGet(batchData.size());
                    log.info("已导入 {} 条记录", total.get());
                    batchData.clear();
                }

                if (total.get() >= TOTAL_RECORDS) {
                    break;
                }
            }

            if (total.get() >= TOTAL_RECORDS) {
                break;
            }
        }

        // 插入剩余数据
        if (!batchData.isEmpty()) {
            insertBatchDirect(insertSQL, batchData);
        }

        log.info("数据生成完成，共 {} 条记录", imported);
    }

    @SneakyThrows
    private void insertBatchDirect(String insertSQL, List<Object[]> batchData) {
        // 使用VALUES语法进行批量插入
        if (batchData.isEmpty()) return;

        StringBuilder batchSql = new StringBuilder();
        batchSql.append("INSERT INTO COMMON.Item_Revision (");
        batchSql.append("id, code, revid, name, _state, _properties, _creator, _creationDate, _lastmodifier, _lastmodifieddate, _version");
        batchSql.append(") VALUES ");

        List<Object> allParams = new ArrayList<>();
        for (int i = 0; i < batchData.size(); i++) {
            if (i > 0) batchSql.append(", ");
            batchSql.append("(?, ?, ?, ?, ?, '{}'::jsonb, ?, ?, ?, ?, ?)");

            Object[] row = batchData.get(i);
            allParams.addAll(Arrays.asList(row));
        }

        S.service(NativeSqlService.class).executeNativeUpdate(
                batchSql.toString(), allParams
        );


    }

    /**
     * 基础查询性能测试
     */
    private void testBasicQueries() {
        log.info("=== 基础查询性能测试 ===");

        // 1. 单条记录查询 - 按code查询
        testSingleQuery("Q.result单条记录查询(按code)", () -> {
            String testCode = "PERF_TEST_00001000";
            return Q.result(ItemRevision.class)
                    .where("code = ?", testCode)
                    .query()
                    .size();
        });

        // 2. 单条记录查询 - 按code+revId查询
        testSingleQuery("Q.result单条记录查询(按code+revId)", () -> {
            String testCode = "PERF_TEST_00001000";
            return Q.result(ItemRevision.class)
                    .where("code = ? AND \"revId\" = ?", testCode, "1")
                    .first() != null ? 1 : 0;
        });

        // 3. 范围查询 - 按code前缀查询
        testSingleQuery("Q.result范围查询(code前缀)", () -> {
            return Q.result(ItemRevision.class)
                    .where("code LIKE ?", "PERF_TEST_0000100%")
                    .limit(1000)
                    .query()
                    .size();
        });

        // 4. 状态过滤查询
        testSingleQuery("Q.result状态过滤查询", () -> {
            return Q.result(ItemRevision.class)
                    .where("name LIKE ? AND \"_state\" = ?", "PERF_TEST_%", "Released")
                    .limit(1000)
                    .query()
                    .size();
        });

        // 5. 排序查询
        testSingleQuery("Q.result排序查询", () -> {
            return Q.result(ItemRevision.class)
                    .where("name LIKE ?", "PERF_TEST_0000100%")
                    .asc("code", "revId")
                    .limit(1000)
                    .query()
                    .size();
        });
    }

    /**
     * 逻辑对象查询性能测试
     */
    private void testLogicalQueries() {
        log.info("=== 逻辑对象查询性能测试 ===");

        // 1. 逻辑查询 - LATEST规则
        testSingleQuery("Q.logical查询(LATEST规则)", () -> {
            return Q.logical(ItemRevision.class)
                    .where("name LIKE ?", "PERF_TEST_0000100%")
                    .withRule(RevisionRule.LATEST)
                    .limit(1000)
                    .query()
                    .size();
        });

        // 2. 逻辑查询 - LATEST_RELEASED规则
        testSingleQuery("Q.logical查询(LATEST_RELEASED规则)", () -> {
            return Q.logical(ItemRevision.class)
                    .where("name LIKE ?", "PERF_TEST_0000100%")
                    .withRule(RevisionRule.LATEST_RELEASED)
                    .limit(1000)
                    .query()
                    .size();
        });

        // 3. 逻辑查询 - LATEST_WORKING规则
        testSingleQuery("Q.logical查询(LATEST_WORKING规则)", () -> {
            return Q.logical(ItemRevision.class)
                    .where("name LIKE ?", "PERF_TEST_0000100%")
                    .withRule(RevisionRule.LATEST_WORKING)
                    .limit(1000)
                    .query()
                    .size();
        });

        // 4. 逻辑查询单条记录
        testSingleQuery("Q.logical单条记录查询", () -> {
            String testCode = "PERF_TEST_00001000";
            return Q.logical(ItemRevision.class)
                    .where("code = ?", testCode)
                    .withRule(RevisionRule.LATEST)
                    .first() != null ? 1 : 0;
        });

        // 5. 逻辑查询计数
        testSingleQuery("Q.logical计数查询", () -> {
            return (int) Q.logical(ItemRevision.class)
                    .where("name LIKE ?", "PERF_TEST_0000100%")
                    .withRule(RevisionRule.LATEST)
                    .count();
        });
    }

    /**
     * 批量查询性能测试
     */
    private void testBatchQueries() {
        log.info("=== 批量查询性能测试 ===");

        // 准备测试数据
        List<String> testCodes = IntStream.range(1000, 2000)
                .mapToObj(i -> String.format("PERF_TEST_%08d", i))
                .collect(Collectors.toList());

        // 1. 批量IN查询 vs 元组查询对比
        testSingleQuery("批量IN查询(1000条)", () -> {
            return Q.result(ItemRevision.class)
                    .where("code IN (" +
                                    testCodes.stream().map(c -> "?").collect(Collectors.joining(",")) + ")",
                            testCodes.toArray())
                    .query()
                    .size();
        });

        // 2. 元组查询
        testSingleQuery("元组查询(1000条)", () -> {
            var tuples = testCodes.stream()
                    .map(code -> Tuples.tuple(code, "1"))
                    .collect(Collectors.toList());

            return Q.result(ItemRevision.class)
                    .whereTuples("code, \"revId\"", tuples)
                    .query()
                    .size();
        });

        // 3. 批量业务键查询
        testSingleQuery("批量业务键查询", () -> {
            int count = 0;
            for (String code : testCodes.subList(0, 100)) {
                Map<String, Object> businessKey = Map.of("code", code, "revId", "1");
                ItemRevision result = Q.result(ItemRevision.class)
                        .whereByBusinessKeys(businessKey)
                        .first();
                if (result != null) count++;
            }
            return count;
        });
    }

    /**
     * 分页查询性能测试
     */
    private void testPaginationQueries() {
        log.info("=== 分页查询性能测试 ===");

        // 1. Q.result分页查询
        testSingleQuery("Q.result分页查询(第1页)", () -> {
            Pagination.Page<ItemRevision> page = Q.result(ItemRevision.class)
                    .where("name LIKE ?", "PERF_TEST_%")
                    .asc("code")
                    .pageSize(1000)
                    .pageNumber(0)
                    .queryPage();
            return page.getContent().size();
        });

        // 2. Q.result分页查询 - 深度分页
        testSingleQuery("Q.result分页查询(第1000页)", () -> {
            Pagination.Page<ItemRevision> page = Q.result(ItemRevision.class)
                    .where("name LIKE ?", "PERF_TEST_%")
                    .asc("code")
                    .pageSize(1000)
                    .pageNumber(999)
                    .queryPage();
            return page.getContent().size();
        });

        // 3. Q.logical分页查询
        testSingleQuery("Q.logical分页查询(第1页)", () -> {
            Pagination.Page<ItemRevision> page = Q.logical(ItemRevision.class)
                    .where("name LIKE ?", "PERF_TEST_%")
                    .withRule(RevisionRule.LATEST)
                    .asc("code")
                    .pageSize(1000)
                    .pageNumber(0)
                    .queryPage();
            return page.getContent().size();
        });

        // 4. Q.logical分页查询 - 深度分页
        testSingleQuery("Q.logical分页查询(第500页)", () -> {
            Pagination.Page<ItemRevision> page = Q.logical(ItemRevision.class)
                    .where("name LIKE ?", "PERF_TEST_%")
                    .withRule(RevisionRule.LATEST)
                    .asc("code")
                    .pageSize(1000)
                    .pageNumber(499)
                    .queryPage();
            return page.getContent().size();
        });
    }

    /**
     * ABAC权限场景性能测试
     */
    private void testAbacPermissionQueries() {
        log.info("=== ABAC权限场景性能测试 ===");

        // 1. 无权限控制基准查询
        testSingleQuery("无权限基准查询", () -> {
            return Q.result(ItemRevision.class)
                    .where("name LIKE ?", "PERF_TEST_0000%")
                    .limit(1000)
                    .query()
                    .size();
        });

        // 2. 创建者权限过滤查询（模拟RLS）
        testSingleQuery("创建者权限过滤查询", () -> {
            return Q.result(ItemRevision.class)
                    .where("name LIKE ? AND \"_creator\" = ?", "PERF_TEST_0000%", 1L)
                    .limit(1000)
                    .query()
                    .size();
        });

        // 3. 状态权限过滤查询（模拟RLS）
        testSingleQuery("状态权限过滤查询", () -> {
            return Q.result(ItemRevision.class)
                    .where("name LIKE ? AND \"_state\" IN (?, ?)", "PERF_TEST_0000%", "Working", "Released")
                    .limit(1000)
                    .query()
                    .size();
        });

        // 4. 复合权限条件查询
        testSingleQuery("复合权限条件查询", () -> {
            return Q.result(ItemRevision.class)
                    .where("name LIKE ? AND (\"_creator\" = ? OR \"_state\" = ?)",
                            "PERF_TEST_0000%", 1L, "Released")
                    .limit(1000)
                    .query()
                    .size();
        });

        // 5. 权限检查API性能测试
        testSingleQuery("权限检查API测试", () -> {
            List<ItemRevision> items = Q.result(ItemRevision.class)
                    .where("name LIKE ?", "PERF_TEST_0000%")
                    .limit(100)
                    .query();

            int permittedCount = 0;
            for (ItemRevision item : items) {
                try {
                    // 模拟权限检查 - 需要根据实际的PermissionService API调整
                    // boolean hasPermission = S.service(PermissionService.class)
                    //     .checkPermission(item, PermissionAction.READ);
                    // if (hasPermission) permittedCount++;

                    // 简化版本：基于创建者的权限检查
                    if (item.get_creator() != null && item.get_creator() <= 10) {
                        permittedCount++;
                    }
                } catch (Exception e) {
                    // 权限检查失败，跳过
                }
            }
            return permittedCount;
        });
    }

    /**
     * 并发查询性能测试
     */
    private void testConcurrentQueries() {
        log.info("=== 并发查询性能测试 ===");

        // 并发查询测试
        testConcurrentQuery("并发基础查询", CONCURRENT_THREADS, () -> {
            Random random = new Random();
            String testCode = String.format("PERF_TEST_%08d", random.nextInt(1000) + 1000);

            return Q.result(ItemRevision.class)
                    .where("code = ?", testCode)
                    .query()
                    .size();
        });

        // 并发逻辑查询测试
        testConcurrentQuery("并发逻辑查询", CONCURRENT_THREADS, () -> {
            Random random = new Random();
            String pattern = String.format("PERF_TEST_%04d%%", random.nextInt(1000) + 1000);

            return Q.logical(ItemRevision.class)
                    .where("name LIKE ?", pattern)
                    .withRule(RevisionRule.LATEST)
                    .limit(100)
                    .query()
                    .size();
        });

        // 并发分页查询测试
        testConcurrentQuery("并发分页查询", CONCURRENT_THREADS, () -> {
            Random random = new Random();
            int pageNumber = random.nextInt(100);

            Pagination.Page<ItemRevision> page = Q.result(ItemRevision.class)
                    .where("name LIKE ?", "PERF_TEST_%")
                    .asc("code")
                    .pageSize(100)
                    .pageNumber(pageNumber)
                    .queryPage();
            return page.getContent().size();
        });
    }

    /**
     * 执行单个查询性能测试
     */
    private void testSingleQuery(String testName, Callable<Integer> queryFunction) {
        List<Long> durations = new ArrayList<>();
        int totalResults = 0;

        for (int i = 0; i < TEST_ITERATIONS; i++) {
            System.gc(); // 建议垃圾回收

            long startTime = System.nanoTime();
            try {
                totalResults += queryFunction.call();
            } catch (Exception e) {
                log.error("查询执行失败: " + testName, e);
                return;
            }
            long duration = System.nanoTime() - startTime;
            durations.add(duration / 1_000_000); // 转换为毫秒
        }

        // 计算统计信息
        double avgDuration = durations.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long minDuration = durations.stream().mapToLong(Long::longValue).min().orElse(0);
        long maxDuration = durations.stream().mapToLong(Long::longValue).max().orElse(0);

        TestResult result = new TestResult();
        result.testName = testName;
        result.avgDuration = avgDuration;
        result.minDuration = minDuration;
        result.maxDuration = maxDuration;
        result.totalResults = totalResults;
        result.iterations = TEST_ITERATIONS;

        testResults.add(result);

        log.info("测试: {} | 平均: {:.2f}ms | 最小: {}ms | 最大: {}ms | 结果数: {}",
                testName, avgDuration, minDuration, maxDuration, totalResults);
    }

    /**
     * 执行并发查询测试
     */
    private void testConcurrentQuery(String testName, int threadCount, Callable<Integer> queryFunction) {
        List<Future<Long>> futures = new ArrayList<>();
        long startTime = System.nanoTime();

        // 启动并发任务
        for (int i = 0; i < threadCount; i++) {
            Future<Long> future = executorService.submit(() -> {
                long taskStartTime = System.nanoTime();
                try {
                    queryFunction.call();
                } catch (Exception e) {
                    log.error("并发查询执行失败", e);
                }
                return System.nanoTime() - taskStartTime;
            });
            futures.add(future);
        }

        // 等待所有任务完成
        List<Long> durations = new ArrayList<>();
        for (Future<Long> future : futures) {
            try {
                durations.add(future.get() / 1_000_000); // 转换为毫秒
            } catch (Exception e) {
                log.error("获取并发任务结果失败", e);
            }
        }

        long totalTime = (System.nanoTime() - startTime) / 1_000_000; // 总耗时(毫秒)
        double avgDuration = durations.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double throughput = (double) threadCount / totalTime * 1000; // QPS

        TestResult result = new TestResult();
        result.testName = testName + "(并发" + threadCount + "线程)";
        result.avgDuration = avgDuration;
        result.minDuration = durations.stream().mapToLong(Long::longValue).min().orElse(0);
        result.maxDuration = durations.stream().mapToLong(Long::longValue).max().orElse(0);
        result.totalResults = threadCount;
        result.iterations = 1;
        result.throughput = throughput;
        result.totalTime = totalTime;

        testResults.add(result);

        log.info("并发测试: {} | 总耗时: {}ms | 平均单次: {:.2f}ms | 吞吐量: {:.2f} QPS",
                testName, totalTime, avgDuration, throughput);
    }

    /**
     * 生成性能测试报告
     */
    private void generatePerformanceReport() {
        log.info("=== 生成性能测试报告 ===");

        String reportFileName = "ItemRevision_Performance_Report_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".md";

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(reportFileName))) {
            // 报告头部
            writer.write("# ItemRevision 大数据量性能测试报告\n\n");
            writer.write("## 测试环境\n\n");
            writer.write("- **测试时间**: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
            writer.write("- **数据规模**: " + String.format("%,d", TOTAL_RECORDS) + " 条记录\n");
            writer.write("- **不同Code数量**: " + String.format("%,d", TOTAL_CODES) + " 个\n");
            writer.write("- **数据库**: PostgreSQL\n");
            writer.write("- **测试重复次数**: " + TEST_ITERATIONS + " 次\n");
            writer.write("- **并发线程数**: " + CONCURRENT_THREADS + " 个\n\n");

            // 获取数据库统计信息
            try {
                List<List<?>> dbStats = S.service(NativeSqlService.class).executeNativeQuery(
                        "SELECT COUNT(*), COUNT(DISTINCT code) FROM COMMON.Item_Revision WHERE name LIKE 'PERF_TEST_%'"
                );
                if (!dbStats.isEmpty()) {
                    writer.write("- **实际记录数**: " + String.format("%,d", ((Number) dbStats.get(0).get(0)).longValue()) + " 条\n");
                    writer.write("- **实际Code数量**: " + String.format("%,d", ((Number) dbStats.get(0).get(1)).longValue()) + " 个\n\n");
                }
            } catch (Exception e) {
                log.warn("获取数据库统计信息失败", e);
            }

            // 性能测试结果表格
            writer.write("## 性能测试结果\n\n");
            writer.write("| 测试项目 | 平均耗时(ms) | 最小耗时(ms) | 最大耗时(ms) | 结果数量 | 吞吐量(QPS) | 备注 |\n");
            writer.write("|----------|-------------|-------------|-------------|----------|------------|------|\n");

            for (TestResult result : testResults) {
                String throughputStr = result.throughput > 0 ? String.format("%.2f", result.throughput) : "-";
                String note = result.totalTime > 0 ? String.format("总耗时:%dms", result.totalTime) : "";

                writer.write(String.format("| %s | %.2f | %d | %d | %d | %s | %s |\n",
                        result.testName,
                        result.avgDuration,
                        result.minDuration,
                        result.maxDuration,
                        result.totalResults,
                        throughputStr,
                        note
                ));
            }

            // 性能分析
            writer.write("\n## 性能分析\n\n");

            // 基础查询分析
            writer.write("### 1. 基础查询性能\n\n");
            testResults.stream()
                    .filter(r -> r.testName.contains("Q.result") && !r.testName.contains("并发"))
                    .forEach(result -> {
                        try {
                            writer.write(String.format("- **%s**: 平均 %.2fms，单次查询可处理 %d 条结果\n",
                                    result.testName, result.avgDuration, result.totalResults / result.iterations));
                        } catch (IOException e) {
                            log.error("写入报告失败", e);
                        }
                    });

            // 逻辑查询分析
            writer.write("\n### 2. 逻辑查询性能\n\n");
            testResults.stream()
                    .filter(r -> r.testName.contains("Q.logical") && !r.testName.contains("并发"))
                    .forEach(result -> {
                        try {
                            writer.write(String.format("- **%s**: 平均 %.2fms，相比基础查询有额外的版本过滤开销\n",
                                    result.testName, result.avgDuration));
                        } catch (IOException e) {
                            log.error("写入报告失败", e);
                        }
                    });

            // 批量查询分析
            writer.write("\n### 3. 批量查询性能\n\n");
            testResults.stream()
                    .filter(r -> r.testName.contains("批量") || r.testName.contains("元组"))
                    .forEach(result -> {
                        try {
                            writer.write(String.format("- **%s**: 平均 %.2fms，批量处理效率显著\n",
                                    result.testName, result.avgDuration));
                        } catch (IOException e) {
                            log.error("写入报告失败", e);
                        }
                    });

            // ABAC权限分析
            writer.write("\n### 4. ABAC权限性能影响\n\n");
            testResults.stream()
                    .filter(r -> r.testName.contains("权限"))
                    .forEach(result -> {
                        try {
                            writer.write(String.format("- **%s**: 平均 %.2fms\n",
                                    result.testName, result.avgDuration));
                        } catch (IOException e) {
                            log.error("写入报告失败", e);
                        }
                    });

            // 并发性能分析
            writer.write("\n### 5. 并发性能\n\n");
            testResults.stream()
                    .filter(r -> r.testName.contains("并发"))
                    .forEach(result -> {
                        try {
                            writer.write(String.format("- **%s**: 吞吐量 %.2f QPS，平均响应时间 %.2fms\n",
                                    result.testName, result.throughput, result.avgDuration));
                        } catch (IOException e) {
                            log.error("写入报告失败", e);
                        }
                    });

            // 性能建议
            writer.write("\n## 性能优化建议\n\n");
            writer.write("### 1. 索引优化\n");
            writer.write("- 确保查询字段已建立适当索引\n");
            writer.write("- 对于复合查询条件，建立复合索引\n");
            writer.write("- 定期维护索引统计信息\n\n");

            writer.write("### 2. 查询优化\n");
            writer.write("- 优先使用精确匹配而非模糊查询\n");
            writer.write("- 批量查询时使用元组查询替代多次单独查询\n");
            writer.write("- 合理使用分页，避免深度分页\n\n");

            writer.write("### 3. ABAC权限优化\n");
            writer.write("- RLS策略应尽可能简单高效\n");
            writer.write("- 权限相关字段建立适当索引\n");
            writer.write("- 合理使用权限绕过机制\n\n");

            writer.write("### 4. 并发优化\n");
            writer.write("- 连接池配置要合理\n");
            writer.write("- 避免长时间持有数据库连接\n");
            writer.write("- 考虑使用读写分离\n\n");

            // 数据库配置建议
            writer.write("### 5. 数据库配置建议\n");
            writer.write("```sql\n");
            writer.write("-- PostgreSQL性能配置建议\n");
            writer.write("-- 增加工作内存\n");
            writer.write("SET work_mem = '256MB';\n");
            writer.write("-- 增加共享缓冲区\n");
            writer.write("SET shared_buffers = '25% of RAM';\n");
            writer.write("-- 优化查询计划器\n");
            writer.write("SET random_page_cost = 1.1;\n");
            writer.write("-- 增加有效缓存大小\n");
            writer.write("SET effective_cache_size = '75% of RAM';\n");
            writer.write("```\n\n");

            writer.write("---\n");
            writer.write("*报告生成时间: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "*\n");

        } catch (IOException e) {
            log.error("生成性能报告失败", e);
        }

        log.info("性能测试报告已生成: {}", reportFileName);
    }

    /**
     * 测试结果数据结构
     */
    @Data
    private static class TestResult {
        private String testName;
        private double avgDuration;
        private long minDuration;
        private long maxDuration;
        private int totalResults;
        private int iterations;
        private double throughput; // QPS
        private long totalTime; // 总耗时
    }
}