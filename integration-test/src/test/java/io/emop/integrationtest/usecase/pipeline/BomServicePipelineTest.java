package io.emop.integrationtest.usecase.pipeline;

import io.emop.integrationtest.util.TimerUtils;
import io.emop.model.bom.BomLine;
import io.emop.model.common.ItemRevision;
import io.emop.model.common.ModelObject;
import io.emop.model.common.UserContext;
import io.emop.service.S;
import io.emop.service.api.data.ObjectService;
import io.emop.service.api.domain.bom.BomService;
import io.emop.service.api.domain.bom.BomServicePipeline;
import io.emop.service.api.pipeline.ResultFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.emop.integrationtest.util.Assertion.*;

/**
 * BomService Pipeline测试用例
 * 验证BOM单行增删改查的Pipeline功能和性能
 */
@RequiredArgsConstructor
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BomServicePipelineTest {

    private static final String revId = "A";
    private static final String dateCode = String.valueOf(System.currentTimeMillis());
    private static final int batchSize = 100;

    @BeforeAll
    public void setup() {
        UserContext.setCurrentUser(new UserContext(100L, List.of("admin")));
    }

    @Test
    @Order(1)
    public void testBasicAddBomLine() {
        S.withStrongConsistency(this::testBasicAddBomLineImpl);
    }

    @Test
    @Order(2)
    public void testBasicUpdateBomLine() {
        S.withStrongConsistency(this::testBasicUpdateBomLineImpl);
    }

    @Test
    @Order(3)
    public void testBasicDeleteBomLine() {
        S.withStrongConsistency(this::testBasicDeleteBomLineImpl);
    }

    @Test
    @Order(4)
    public void testPerformanceComparison() {
        S.withStrongConsistency(this::testPerformanceComparisonImpl);
    }

    /**
     * 测试基本的添加BOM行功能
     */
    private void testBasicAddBomLineImpl() {
        log.info("=== 测试Pipeline添加BOM行功能 ===");

        ObjectService objectService = S.service(ObjectService.class);
        
        // 准备测试数据：1个父件和10个子件
        ItemRevision parent = createTestItem("parent");
        List<ItemRevision> children = prepareTestData(10, "child");
        
        parent = objectService.save(parent);
        children = objectService.saveAll(children);

        final ItemRevision savedParent = parent;
        final List<ItemRevision> savedChildren = children;

        TimerUtils.measureExecutionTime("Pipeline添加BOM行测试", () -> {
            BomServicePipeline pipeline = BomService.pipeline();

            // 收集添加操作
            List<ResultFuture<BomLine>> futures = new ArrayList<>();
            for (int i = 0; i < savedChildren.size(); i++) {
                Map<String, Object> props = new HashMap<>();
                props.put("quantity", (float) (i + 1));
                props.put("unit", "个");
                props.put("description", "测试子件-" + i);
                
                futures.add(pipeline.addBomLine(savedParent, savedChildren.get(i), props));
            }

            // 执行Pipeline
            pipeline.execute();

            // 验证结果
            for (int i = 0; i < futures.size(); i++) {
                try {
                    BomLine result = futures.get(i).get();
                    assertNotNull(result);
                    assertNotNull(result.getId());
                    assertEquals((float) (i + 1), result.getQuantity());
                    assertEquals("个", result.getUnit());
                    assertEquals("测试子件-" + i, result.getDescription());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            log.info("✅ Pipeline添加BOM行测试通过: {}个BOM行", futures.size());
        });

        // 清理测试数据
        cleanupTestData(savedParent, savedChildren);
    }

    /**
     * 测试更新BOM行功能
     */
    private void testBasicUpdateBomLineImpl() {
        log.info("=== 测试Pipeline更新BOM行功能 ===");

        ObjectService objectService = S.service(ObjectService.class);
        BomService bomService = S.service(BomService.class);
        
        // 准备测试数据
        ItemRevision parent = createTestItem("update-parent");
        List<ItemRevision> children = prepareTestData(10, "update-child");
        
        parent = objectService.save(parent);
        children = objectService.saveAll(children);

        // 先添加BOM行
        List<BomLine> bomLines = new ArrayList<>();
        for (ItemRevision child : children) {
            Map<String, Object> props = new HashMap<>();
            props.put("quantity", 1.0f);
            props.put("unit", "个");
            BomLine bomLine = bomService.addBomLine(parent, child, props);
            bomLines.add(bomLine);
        }

        final ItemRevision savedParent = parent;
        final List<ItemRevision> savedChildren = children;

        TimerUtils.measureExecutionTime("Pipeline更新BOM行测试", () -> {
            BomServicePipeline pipeline = BomService.pipeline();

            // 收集更新操作
            List<ResultFuture<BomLine>> futures = new ArrayList<>();
            for (int i = 0; i < savedChildren.size(); i++) {
                Map<String, Object> updates = new HashMap<>();
                updates.put("quantity", (float) (i + 10));
                updates.put("description", "更新后的描述-" + i);
                
                futures.add(pipeline.updateBomLine(savedParent, savedChildren.get(i), updates));
            }

            // 执行Pipeline
            pipeline.execute();

            // 验证结果
            for (int i = 0; i < futures.size(); i++) {
                try {
                    BomLine result = futures.get(i).get();
                    assertNotNull(result);
                    assertEquals((float) (i + 10), result.getQuantity());
                    assertEquals("更新后的描述-" + i, result.getDescription());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            log.info("✅ Pipeline更新BOM行测试通过: {}个BOM行", futures.size());
        });

        // 清理测试数据
        cleanupTestData(savedParent, savedChildren);
    }

    /**
     * 测试删除BOM行功能
     */
    private void testBasicDeleteBomLineImpl() {
        log.info("=== 测试Pipeline删除BOM行功能 ===");

        ObjectService objectService = S.service(ObjectService.class);
        BomService bomService = S.service(BomService.class);
        
        // 准备测试数据
        ItemRevision parent = createTestItem("delete-parent");
        List<ItemRevision> children = prepareTestData(10, "delete-child");
        
        parent = objectService.save(parent);
        children = objectService.saveAll(children);

        // 先添加BOM行
        for (ItemRevision child : children) {
            Map<String, Object> props = new HashMap<>();
            props.put("quantity", 1.0f);
            bomService.addBomLine(parent, child, props);
        }

        final ItemRevision savedParent = parent;
        final List<ItemRevision> savedChildren = children;

        TimerUtils.measureExecutionTime("Pipeline删除BOM行测试", () -> {
            BomServicePipeline pipeline = BomService.pipeline();

            // 收集删除操作
            List<ResultFuture<Boolean>> futures = new ArrayList<>();
            for (ItemRevision child : savedChildren) {
                futures.add(pipeline.deleteBomLine(savedParent, child, true));
            }

            // 执行Pipeline
            pipeline.execute();

            // 验证结果
            for (ResultFuture<Boolean> future : futures) {
                try {
                    Boolean result = future.get();
                    assertTrue(result);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            log.info("✅ Pipeline删除BOM行测试通过: {}个BOM行", futures.size());
        });

        // 清理测试数据
        cleanupTestData(savedParent, savedChildren);
    }

    /**
     * 性能对比测试：传统方式 vs Pipeline方式
     */
    private void testPerformanceComparisonImpl() {
        log.info("=== 性能对比测试 ===");

        ObjectService objectService = S.service(ObjectService.class);
        BomService bomService = S.service(BomService.class);

        // 准备测试数据
        ItemRevision parent1 = createTestItem("perf-parent-1");
        List<ItemRevision> children1 = prepareTestData(batchSize, "perf-child-1");
        parent1 = objectService.save(parent1);
        children1 = objectService.saveAll(children1);

        // 测试1：传统逐个调用方式
        final ItemRevision finalParent1 = parent1;
        final List<ItemRevision> finalChildren1 = children1;
        long traditionalTime = TimerUtils.measureExecutionTime("传统方式逐个添加 " + batchSize + " 个BOM行", () -> {
            for (ItemRevision child : finalChildren1) {
                Map<String, Object> props = new HashMap<>();
                props.put("quantity", 1.0f);
                props.put("unit", "个");
                bomService.addBomLine(finalParent1, child, props);
            }
        }) / 1_000_000;

        // 准备第二组测试数据
        ItemRevision parent2 = createTestItem("perf-parent-2");
        List<ItemRevision> children2 = prepareTestData(batchSize, "perf-child-2");
        parent2 = objectService.save(parent2);
        children2 = objectService.saveAll(children2);

        // 测试2：Pipeline批量方式
        final ItemRevision finalParent2 = parent2;
        final List<ItemRevision> finalChildren2 = children2;
        long pipelineTime = TimerUtils.measureExecutionTime("Pipeline批量添加 " + batchSize + " 个BOM行", () -> {
            BomServicePipeline pipeline = BomService.pipeline();
            List<ResultFuture<BomLine>> futures = new ArrayList<>();

            for (ItemRevision child : finalChildren2) {
                Map<String, Object> props = new HashMap<>();
                props.put("quantity", 1.0f);
                props.put("unit", "个");
                futures.add(pipeline.addBomLine(finalParent2, child, props));
            }

            pipeline.execute();

            // 验证结果
            for (ResultFuture<BomLine> future : futures) {
                try {
                    assertNotNull(future.get());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }) / 1_000_000;

        // 性能统计
        double speedup = (double) traditionalTime / pipelineTime;

        log.info("🚀 性能对比结果 ({} 个BOM行):", batchSize);
        log.info("   传统逐个调用: {}ms", traditionalTime);
        log.info("   Pipeline方式:  {}ms (提升 {:.2f}x)", pipelineTime, speedup);

        // 清理测试数据
        cleanupTestData(finalParent1, finalChildren1);
        cleanupTestData(finalParent2, finalChildren2);
    }

    /**
     * 创建测试Item
     */
    private ItemRevision createTestItem(String prefix) {
        ItemRevision rev = ItemRevision.newModel(prefix + "-" + dateCode + "-" + UUID.randomUUID(), revId);
        rev.setName("BOM测试对象-" + prefix);
        return rev;
    }

    /**
     * 准备测试数据
     */
    private List<ItemRevision> prepareTestData(int count, String prefix) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(idx -> {
                    ItemRevision rev = ItemRevision.newModel(
                            prefix + "-" + dateCode + "-" + idx + "-" + UUID.randomUUID().toString().substring(0, 8),
                            revId
                    );
                    rev.setName("BOM测试子件-" + prefix + "-" + idx);
                    return rev;
                })
                .collect(Collectors.toList());
    }

    /**
     * 清理测试数据
     */
    private void cleanupTestData(ItemRevision parent, List<ItemRevision> children) {
        ObjectService objectService = S.service(ObjectService.class);
        List<Long> allIds = new ArrayList<>();
        allIds.add(parent.getId());
        allIds.addAll(children.stream().map(ModelObject::getId).collect(Collectors.toList()));
        
        // 删除所有相关的BomLine
        try {
            objectService.forceDelete(allIds);
        } catch (Exception e) {
            log.warn("清理测试数据时出错: {}", e.getMessage());
        }
    }
}
