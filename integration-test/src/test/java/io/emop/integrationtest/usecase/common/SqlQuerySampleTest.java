package io.emop.integrationtest.usecase.common;

import io.emop.model.common.ItemRevision;
import io.emop.model.common.UserContext;
import io.emop.model.query.Q;
import io.emop.model.query.tuple.Tuple2;
import io.emop.model.query.tuple.Tuple3;
import io.emop.integrationtest.dto.RevisionCountDTO;
import io.emop.integrationtest.util.TimerUtils;
import io.emop.service.S;
import io.emop.service.api.data.ObjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.emop.model.query.tuple.Tuples.*;
import static io.emop.integrationtest.util.Assertion.assertEquals;
import static io.emop.integrationtest.util.Assertion.assertTrue;

@RequiredArgsConstructor
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SqlQuerySampleTest {

    private static final String dateCode = String.valueOf(System.currentTimeMillis());

    @BeforeAll
    public void setup() {
        UserContext.setCurrentUser(new UserContext(100L, List.of("admin")));
    }

    @Test
    @Order(1)
    public void testPrepareDate() {
        S.withStrongConsistency(this::prepareDate);
    }

    @Test
    @Order(2)
    public void testPojoQueryByPojoSql() {
        S.withStrongConsistency(this::pojoQueryByPojoSql);
    }

    @Test
    @Order(3)
    public void testPojoQueryByPojoCondition() {
        S.withStrongConsistency(this::pojoQueryByPojoCondition);
    }

    @Test
    @Order(4)
    public void testSqlQueryWithList() {
        S.withStrongConsistency(this::sqlQueryWithList);
    }

    @Test
    @Order(5)
    public void testSqlQueryWithDTO() {
        S.withStrongConsistency(this::sqlQueryWithDTO);
    }

    @Test
    @Order(6)
    public void testBatchTupleQuery() {
        S.withStrongConsistency(this::batchTupleQuery);
    }

    @Test
    @Order(7)
    public void testComplexBatchTupleQuery() {
        S.withStrongConsistency(this::complexBatchTupleQuery);
    }

    @Test
    @Order(8)
    public void testTripleTupleQuery() {
        S.withStrongConsistency(this::tripleTupleQuery);
    }

    @Test
    @Order(9)
    public void testEdgeCaseTests() {
        S.withStrongConsistency(this::edgeCaseTests);
    }

    private void prepareDate() {
        int batchSize = 100;
        ObjectService objectService = S.service(ObjectService.class);
        TimerUtils.measureExecutionTime("批量创建 " + batchSize + " ItemRevision对象", () -> {
            List<ItemRevision> data = IntStream.rangeClosed(1, batchSize).mapToObj(idx -> {
                ItemRevision rev = ItemRevision.newModel("Q-" + dateCode + "-" + idx, String.valueOf(idx));
                rev.setName("QuerySample-" + dateCode + "-" + idx);
                rev.set("factoryLocation", switch (idx % 3) {
                    case 1 -> "zhuhai";
                    case 2 -> "beijing";
                    default -> "unknown";
                });
                return rev;
            }).collect(Collectors.toList());
            objectService.saveAll(data);
        });
    }

    /**
     * 使用SQL查询，并将结果填充回POJO
     */
    private void pojoQueryByPojoCondition() {
        TimerUtils.measureExecutionTime("查询 pojoQueryByPojoCondition's ItemRevision对象", () -> {
            List<ItemRevision> revisions = Q.result(ItemRevision.class).where(inExp("code", 2), new String[]{"Q-" + dateCode + "-1", "Q-" + dateCode + "-2"}).query();
            log.info("query result: {}", revisions);
            assertEquals(2, revisions.size());
            assertTrue(revisions.stream().map(r -> r.getCode()).collect(Collectors.toList()).contains("Q-" + dateCode + "-1"));
            assertTrue(revisions.stream().map(r -> r.getCode()).collect(Collectors.toList()).contains("Q-" + dateCode + "-2"));
            assertTrue(revisions.stream().map(r -> r.getRevId()).collect(Collectors.toList()).contains("1"));
            assertTrue(revisions.stream().map(r -> r.getRevId()).collect(Collectors.toList()).contains("2"));
        });
    }

    /**
     * 生成n个逗号隔开的"?"字符串，用于sql的in操作
     * <p>
     * 2 => col in (?,?)
     * 3 => col in (?,?,?)
     */
    String inExp(String column, int size) {
        if (size > 1000) {
            throw new IllegalArgumentException("too many params, please split the items of sql");
        }
        return column + " in (" + String.join(",", Collections.nCopies(size, "?")) + ")";
    }

    /**
     * 使用SQL查询，并将结果填充回POJO
     */
    private void pojoQueryByPojoSql() {
        TimerUtils.measureExecutionTime("查询 pojoQueryByPojosql's ItemRevision对象", () -> {
            //do something else, add join to sql will be fine
            final String sql = "select * from COMMON.Item_Revision where code = ?";
            List<ItemRevision> revisions = Q.result(ItemRevision.class).sql(sql, "Q-" + dateCode + "-1").query();
            log.info("query result: {}", revisions);
            assertEquals(1, revisions.size());
            assertTrue(revisions.stream().map(r -> r.getCode()).collect(Collectors.toList()).contains("Q-" + dateCode + "-1"));
            assertTrue(revisions.stream().map(r -> r.getRevId()).collect(Collectors.toList()).contains("1"));
        });
    }

    /**
     * 使用SQL查询，并将结果填充回List
     */
    private void sqlQueryWithList() {
        TimerUtils.measureExecutionTime("查询 sqlQueryWithList's ItemRevision对象", () -> {
            //do something else, add join to sql will be fine
            final String sql = "select id, code, revId, name from COMMON.Item_Revision where code = ?";
            List<List<?>> revisions = Q.objectType(ItemRevision.class.getName()).sql(sql, "Q-" + dateCode + "-1").queryRaw();
            log.info("query result: {}", revisions);
            assertEquals(1, revisions.size());
            assertEquals("Q-" + dateCode + "-1", revisions.get(0).get(1));
            assertEquals("1", revisions.get(0).get(2));
            assertEquals("QuerySample-" + dateCode + "-1", revisions.get(0).get(3));
        });
    }

    /**
     * 使用SQL查询，并将结果填充回dto
     */
    private void sqlQueryWithDTO() {
        TimerUtils.measureExecutionTime("查询 sqlQueryWithDTO's ItemRevision对象", () -> {
            //do something else, add join to sql will be fine
            final String sql = """
                    SELECT
                        CASE
                            WHEN _properties->>'factoryLocation' = 'zhuhai' THEN 'GuangDong'
                            WHEN _properties->>'factoryLocation' = 'beijing' THEN 'Hebei'
                            ELSE 'Other'
                        END AS province,
                        COUNT(*) AS count
                    FROM
                        COMMON.Item_Revision
                    WHERE
                        code LIKE ?
                    GROUP BY
                        province;
                    """;
            List<RevisionCountDTO> dtos = Q.result(RevisionCountDTO.class).objectType(ItemRevision.class.getName()).sql(sql, "Q-" + dateCode + "-%").query();
            log.info("query result: {}", dtos);
            assertEquals(3, dtos.size());
            assertEquals(new RevisionCountDTO("Other", 33l), dtos.stream().filter(d -> "Other".equals(d.getProvince())).findFirst().get());
            assertEquals(new RevisionCountDTO("Hebei", 33l), dtos.stream().filter(d -> "Hebei".equals(d.getProvince())).findFirst().get());
            assertEquals(new RevisionCountDTO("GuangDong", 34l), dtos.stream().filter(d -> "GuangDong".equals(d.getProvince())).findFirst().get());
        });
    }

    /**
     * 使用 whereTuples 进行批量查询 - 性能优化示例
     * 对比传统逐个查询 vs 批量查询的性能差异
     */
    private void batchTupleQuery() {
        TimerUtils.measureExecutionTime("批量 Tuple 查询 vs 逐个查询性能对比", () -> {

            // 准备测试数据：多个 (code, revId) 组合
            List<Tuple2<String, String>> queryTuples = Arrays.asList(
                    tuple("Q-" + dateCode + "-1", "1"),
                    tuple("Q-" + dateCode + "-2", "2"),
                    tuple("Q-" + dateCode + "-3", "3"),
                    tuple("Q-" + dateCode + "-4", "4"),
                    tuple("Q-" + dateCode + "-5", "5"),
                    tuple("Q-" + dateCode + "-6", "6"),
                    tuple("Q-" + dateCode + "-7", "7"),
                    tuple("Q-" + dateCode + "-8", "8"),
                    tuple("Q-" + dateCode + "-9", "9"),
                    tuple("Q-" + dateCode + "-10", "10")
            );

            log.info("开始性能对比测试，查询 {} 个条件组合", queryTuples.size());

            // 方式1：传统的逐个查询方式
            long startTime = System.currentTimeMillis();
            List<ItemRevision> resultsByIndividualQuery = new ArrayList<>();
            for (Tuple2<String, String> queryTuple : queryTuples) {
                List<ItemRevision> items = Q.result(ItemRevision.class)
                        .where("code = ? AND revId = ?", queryTuple.first(), queryTuple.second())
                        .query();
                resultsByIndividualQuery.addAll(items);
            }
            long individualQueryTime = System.currentTimeMillis() - startTime;

            // 方式2：批量 tuple 查询方式
            startTime = System.currentTimeMillis();
            List<ItemRevision> resultsByBatchQuery = Q.result(ItemRevision.class)
                    .whereTuples("code, revId", queryTuples)
                    .query();
            long batchQueryTime = System.currentTimeMillis() - startTime;

            // 验证结果一致性
            log.info("Individual query results: {}", resultsByIndividualQuery.size());
            log.info("Batch query results: {}", resultsByBatchQuery.size());
            assertEquals(resultsByIndividualQuery.size(), resultsByBatchQuery.size());
            assertEquals(10, resultsByBatchQuery.size());

            // 验证具体数据
            Set<String> expectedCodes = queryTuples.stream()
                    .map(Tuple2::first)
                    .collect(Collectors.toSet());
            Set<String> actualCodes = resultsByBatchQuery.stream()
                    .map(ItemRevision::getCode)
                    .collect(Collectors.toSet());
            assertEquals(expectedCodes, actualCodes);

            // 性能对比
            log.info("=== 性能对比结果 ===");
            log.info("  逐个查询耗时: {}ms ({} 次数据库往返)", individualQueryTime, queryTuples.size());
            log.info("  批量查询耗时: {}ms (1 次数据库往返)", batchQueryTime);
            if (individualQueryTime > 0) {
                double improvement = ((double) (individualQueryTime - batchQueryTime) / individualQueryTime * 100);
                log.info("  性能提升: {}%", improvement);
                log.info("  速度倍数: {}x", (double) individualQueryTime / Math.max(batchQueryTime, 1));
            }

            // 验证查询结果的正确性
            assertTrue("批量查询应该包含所有预期的代码",
                    resultsByBatchQuery.stream()
                            .map(ItemRevision::getCode)
                            .collect(Collectors.toSet())
                            .containsAll(expectedCodes));

            // 验证每个结果的 revId 也是正确的
            for (ItemRevision item : resultsByBatchQuery) {
                boolean foundMatchingTuple = queryTuples.stream()
                        .anyMatch(t -> t.first().equals(item.getCode()) && t.second().equals(item.getRevId()));
                assertTrue("每个结果都应该匹配输入的 tuple", foundMatchingTuple);
            }
        });
    }

    /**
     * 复杂的批量查询场景 - 使用子查询方式
     * 演示如何通过子查询实现复杂条件组合
     */
    private void complexBatchTupleQuery() {
        TimerUtils.measureExecutionTime("复杂批量 Tuple 查询 - 子查询方式", () -> {

            // 准备查询条件：查找特定的 (code, revId) 组合，且 factoryLocation 为 zhuhai
            List<Tuple2<String, String>> codeRevPairs = Arrays.asList(
                    tuple("Q-" + dateCode + "-1", "1"),  // factoryLocation = "zhuhai"
                    tuple("Q-" + dateCode + "-4", "4"),  // factoryLocation = "zhuhai"
                    tuple("Q-" + dateCode + "-7", "7"),  // factoryLocation = "zhuhai"
                    tuple("Q-" + dateCode + "-2", "2"),  // factoryLocation = "beijing" (应该被过滤掉)
                    tuple("Q-" + dateCode + "-5", "5")   // factoryLocation = "beijing" (应该被过滤掉)
            );

            log.info("复杂查询测试：查找 {} 个条件组合中 factoryLocation = 'zhuhai' 的记录", codeRevPairs.size());

            // 第一步：使用 whereTuples 获取符合条件的对象
            List<ItemRevision> candidateResults = Q.result(ItemRevision.class)
                    .whereTuples("code, revId", codeRevPairs)
                    .query();

            log.info("第一步批量查询获得 {} 个候选结果", candidateResults.size());

            // 第二步：在内存中过滤其他条件
            List<ItemRevision> memoryFilteredResults = candidateResults.stream()
                    .filter(item -> "zhuhai".equals(item.get("factoryLocation")))
                    .collect(Collectors.toList());

            log.info("内存过滤后得到 {} 个结果", memoryFilteredResults.size());

            // 备选方案：如果需要数据库级别过滤，可以用ID做二次查询
            List<ItemRevision> dbFilteredResults = new ArrayList<>();
            if (!candidateResults.isEmpty()) {
                List<Long> ids = candidateResults.stream()
                        .map(ItemRevision::getId)
                        .collect(Collectors.toList());

                // 方案1：使用 IN 语句（推荐，兼容性好）
                if (ids.size() <= 1000) { // PostgreSQL IN 语句建议不超过1000个参数
                    String inClause = ids.stream()
                            .map(id -> "?")
                            .collect(Collectors.joining(","));

                    String whereClause = "id IN (" + inClause + ") AND _properties->>'factoryLocation' = ?";
                    List<Object> params = new ArrayList<>(ids);
                    params.add("zhuhai");

                    dbFilteredResults = Q.result(ItemRevision.class)
                            .where(whereClause, params.toArray())
                            .query();
                } else {
                    // 方案2：分批处理大量ID
                    log.info("ID数量过多 ({}), 分批处理", ids.size());
                    List<List<Long>> batches = partitionList(ids, 500);
                    for (List<Long> batch : batches) {
                        String inClause = batch.stream()
                                .map(id -> "?")
                                .collect(Collectors.joining(","));

                        String whereClause = "id IN (" + inClause + ") AND _properties->>'factoryLocation' = ?";
                        List<Object> params = new ArrayList<>(batch);
                        params.add("zhuhai");

                        List<ItemRevision> batchResults = Q.result(ItemRevision.class)
                                .where(whereClause, params.toArray())
                                .query();
                        dbFilteredResults.addAll(batchResults);
                    }
                }

                log.info("数据库级别过滤得到 {} 个结果", dbFilteredResults.size());
            }

            // 验证两种过滤方式的结果一致
            assertEquals(memoryFilteredResults.size(), dbFilteredResults.size());

            List<ItemRevision> results = memoryFilteredResults; // 使用内存过滤的结果进行验证
            log.info("复杂批量查询最终结果: {}", results.size());

            // 验证结果：应该只包含 factoryLocation 为 zhuhai 的项目
            for (ItemRevision item : results) {
                assertEquals("zhuhai", item.get("factoryLocation"));
                assertTrue("应该包含预期的代码",
                        codeRevPairs.stream().anyMatch(t -> t.first().equals(item.getCode())));
                log.debug("验证通过: {} - {} - {}", item.getCode(), item.getRevId(), item.get("factoryLocation"));
            }

            // 根据测试数据的模式，应该有3个结果（1, 4, 7对应 zhuhai）
            assertEquals(3, results.size());
        });
    }

    /**
     * 三元组批量查询示例
     * 演示复杂字段组合的批量查询
     */
    private void tripleTupleQuery() {
        TimerUtils.measureExecutionTime("三元组批量查询", () -> {

            // 假设我们要查询特定的 (code, revId, factoryLocation) 组合
            List<Tuple3<String, String, String>> triples = Arrays.asList(
                    tuple("Q-" + dateCode + "-1", "1", "zhuhai"),
                    tuple("Q-" + dateCode + "-2", "2", "beijing"),
                    tuple("Q-" + dateCode + "-3", "3", "unknown"),
                    tuple("Q-" + dateCode + "-4", "4", "zhuhai"),
                    tuple("Q-" + dateCode + "-5", "5", "beijing")
            );

            log.info("三元组查询测试：查找 {} 个精确匹配的记录", triples.size());

            // 使用复杂的字段表达式进行三元组查询
            // 注意：这里使用了 PostgreSQL 的 JSON 操作符来访问 _properties 中的 factoryLocation
            List<ItemRevision> results = Q.result(ItemRevision.class)
                    .whereTuples("code, revId, _properties->>'factoryLocation'", triples)
                    .asc("code")  // 按 code 排序
                    .query();

            log.info("三元组查询结果: {} 个记录", results.size());
            assertEquals(5, results.size());

            // 验证每个结果都匹配对应的三元组
            for (ItemRevision item : results) {
                String factoryLocation = (String) item.get("factoryLocation");
                boolean found = triples.stream().anyMatch(t ->
                        t.first().equals(item.getCode()) &&
                                t.second().equals(item.getRevId()) &&
                                t.third().equals(factoryLocation));

                assertTrue("结果应该匹配其中一个三元组: " + item.getCode() + ", " +
                        item.getRevId() + ", " + factoryLocation, found);

                log.debug("三元组匹配成功: {} - {} - {}", item.getCode(), item.getRevId(), factoryLocation);
            }

            // 验证结果按 code 排序
            List<String> codes = results.stream().map(ItemRevision::getCode).collect(Collectors.toList());
            List<String> sortedCodes = new ArrayList<>(codes);
            Collections.sort(sortedCodes);
            assertEquals(sortedCodes, codes, "结果应该按 code 排序");

            log.info("三元组查询验证完成，所有 {} 个结果都匹配预期", results.size());
        });
    }

    /**
     * 边界条件和错误处理测试
     */
    private void edgeCaseTests() {
        TimerUtils.measureExecutionTime("边界条件和错误处理测试", () -> {

            // 测试空列表
            try {
                Q.result(ItemRevision.class)
                        .whereTuples("code, revId", Collections.emptyList())
                        .query();
                throw new AssertionError("空列表应该抛出异常");
            } catch (IllegalArgumentException e) {
                log.info("✅ 空列表正确抛出异常: {}", e.getMessage());
            }

            // 测试字段数量不匹配
            try {
                // 创建一个字段数量不匹配的 tuple - 3个字段但期望2个
                Q.result(ItemRevision.class)
                        .whereTuples("code, revId", Arrays.asList(
                                tuple("Q-" + dateCode + "-1", "1", "extra")  // 3个字段，但只期望2个
                        ))
                        .query();
                throw new AssertionError("字段数量不匹配应该抛出异常");
            } catch (IllegalArgumentException e) {
                log.info("✅ 字段数量不匹配正确抛出异常: {}", e.getMessage());
            }

            // 测试大批量查询（性能测试）
            List<Tuple2<String, String>> largeBatch = new ArrayList<>();
            for (int i = 1; i <= 50; i++) {
                largeBatch.add(tuple("Q-" + dateCode + "-" + i, String.valueOf(i)));
            }

            long start = System.currentTimeMillis();
            List<ItemRevision> largeResults = Q.result(ItemRevision.class)
                    .whereTuples("code, revId", largeBatch)
                    .query();
            long duration = System.currentTimeMillis() - start;

            log.info("✅ 大批量查询 ({} 个条件) 耗时: {}ms, 结果: {} 个",
                    largeBatch.size(), duration, largeResults.size());
            assertTrue("大批量查询应该返回预期数量的结果", largeResults.size() <= largeBatch.size());

            log.info("所有边界条件测试通过");
        });
    }

    /**
     * 工具方法：将列表分割成指定大小的批次
     */
    private static <T> List<List<T>> partitionList(List<T> list, int batchSize) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            partitions.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return partitions;
    }
}