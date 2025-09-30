package io.emop.integrationtest.usecase.pipeline;

import io.emop.model.common.ItemRevision;
import io.emop.model.common.ModelObject;
import io.emop.model.common.UserContext;
import io.emop.integrationtest.util.TimerUtils;
import io.emop.service.S;
import io.emop.service.api.data.ObjectService;
import io.emop.service.api.domain.common.CheckoutService;
import io.emop.service.api.domain.common.CheckoutServicePipeline;
import io.emop.service.api.pipeline.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.emop.integrationtest.util.Assertion.*;

/**
 * CheckoutService Pipeline简化测试用例
 * 重点验证功能正确性和性能提升效果
 */
@RequiredArgsConstructor
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CheckoutServicePipelineTest {

    private static final String revId = "50";
    private static final String dateCode = String.valueOf(System.currentTimeMillis());
    private static final int batchSize = 200;

    @BeforeAll
    public void setup() {
        UserContext.setCurrentUser(new UserContext(100L, List.of("admin")));
    }

    @Test
    @Order(1)
    public void testBasicCheckoutFunctionality() {
        S.withStrongConsistency(this::testBasicCheckoutFunctionalityImpl);
    }

    @Test
    @Order(2)
    public void testBasicCheckinFunctionality() {
        S.withStrongConsistency(this::testBasicCheckinFunctionalityImpl);
    }

    @Test
    @Order(3)
    public void testPerformanceComparison() {
        S.withStrongConsistency(this::testPerformanceComparisonImpl);
    }

    /**
     * 测试基本签出功能
     */
    private void testBasicCheckoutFunctionalityImpl() {
        log.info("=== 测试Pipeline签出功能 ===");

        ObjectService objectService = S.service(ObjectService.class);
        List<ItemRevision> testData = prepareTestData(10);
        List<ItemRevision> savedObjects = objectService.saveAll(testData);

        TimerUtils.measureExecutionTime("Pipeline签出功能测试", () -> {
            CheckoutServicePipeline pipeline = CheckoutService.pipeline();

            // 收集签出操作
            List<ResultFuture<ItemRevision>> futures = new ArrayList<>();
            for (ItemRevision obj : savedObjects) {
                futures.add(pipeline.checkout(obj, "测试签出", 60));
            }

            // 执行Pipeline
            pipeline.execute();

            // 验证结果
            for (ResultFuture<ItemRevision> future : futures) {
                try {
                    ItemRevision result = future.get();
                    assertNotNull(result);
                    assertTrue(result.isCheckedOut());
                    assertEquals("测试签出", result.getCheckoutInfo().getComment());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            log.info("✅ Pipeline签出功能测试通过: {}个对象", futures.size());
        });

        // 清理：强制签入
        CheckoutService checkoutService = S.service(CheckoutService.class);
        for (ItemRevision obj : savedObjects) {
            checkoutService.forceCheckin(obj);
        }
        objectService.forceDelete(savedObjects.stream().map(ModelObject::getId).collect(Collectors.toList()));
    }

    /**
     * 测试基本签入功能
     */
    private void testBasicCheckinFunctionalityImpl() {
        log.info("=== 测试Pipeline签入功能 ===");

        ObjectService objectService = S.service(ObjectService.class);
        CheckoutService checkoutService = S.service(CheckoutService.class);

        List<ItemRevision> testData = prepareTestData(10);
        List<ItemRevision> savedObjects = objectService.saveAll(testData);

        // 先签出对象
        List<ItemRevision> checkedOutObjects = checkoutService.checkout(savedObjects, "准备签入测试", 60);

        TimerUtils.measureExecutionTime("Pipeline签入功能测试", () -> {
            CheckoutServicePipeline pipeline = CheckoutService.pipeline();

            // 收集签入操作
            List<ResultFuture<ItemRevision>> futures = new ArrayList<>();
            for (ItemRevision obj : checkedOutObjects) {
                futures.add(pipeline.checkin(obj, "测试签入"));
            }

            // 执行Pipeline
            pipeline.execute();

            // 验证结果
            for (ResultFuture<ItemRevision> future : futures) {
                try {
                    ItemRevision result = future.get();
                    assertNotNull(result);
                    assertFalse(result.isCheckedOut());
                    assertNull(result.getCheckoutInfo());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            log.info("✅ Pipeline签入功能测试通过: {}个对象", futures.size());
        });

        objectService.forceDelete(savedObjects.stream().map(ModelObject::getId).collect(Collectors.toList()));
    }

    /**
     * 性能对比测试：传统方式 vs Pipeline方式
     */
    private void testPerformanceComparisonImpl() {
        log.info("=== 性能对比测试 ===");

        ObjectService objectService = S.service(ObjectService.class);
        CheckoutService checkoutService = S.service(CheckoutService.class);

        // 准备测试数据
        List<ItemRevision> testData = prepareTestData(batchSize);
        List<ItemRevision> savedObjects = objectService.saveAll(testData);
        savedObjects.forEach(o -> {
            assertNotNull(o.getCode());
            assertTrue(o.traits().size() == 1);
        });
        // 测试1：传统逐个调用方式
        long traditionalTime = TimerUtils.measureExecutionTime("传统方式逐个签出/签入 " + batchSize + " 个对象", () -> {
            for (ItemRevision obj : savedObjects) {
                assertTrue(obj.traits().size() == 1);
                checkoutService.checkout(obj, "传统方式测试", 60);
            }

            // 逐个签入
            for (ItemRevision obj : savedObjects) {
                assertTrue(obj.traits().size() == 1);
                checkoutService.checkin(obj, "传统方式签入");
            }
        }) / 1_000_000;

        // 重新准备数据（因为上面已经签入了）
        List<ItemRevision> testData2 = prepareTestData(batchSize, "pipeline-test");
        List<ItemRevision> savedObjects2 = objectService.saveAll(testData2);

        // 测试2：Pipeline批量方式
        long pipelineTime = TimerUtils.measureExecutionTime("Pipeline批量签出/签入 " + batchSize + " 个对象", () -> {
            // Pipeline签出
            CheckoutServicePipeline checkoutPipeline = CheckoutService.pipeline();
            List<ResultFuture<ItemRevision>> checkoutFutures = new ArrayList<>();

            for (ItemRevision obj : savedObjects2) {
                checkoutFutures.add(checkoutPipeline.checkout(obj, "Pipeline测试", 60));
            }
            checkoutPipeline.execute();

            // 获取签出结果
            List<ItemRevision> checkedOutObjects = new ArrayList<>();
            for (ResultFuture<ItemRevision> future : checkoutFutures) {
                try {
                    checkedOutObjects.add(future.get());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            // Pipeline签入
            CheckoutServicePipeline checkinPipeline = CheckoutService.pipeline();
            List<ResultFuture<ItemRevision>> checkinFutures = new ArrayList<>();

            for (ItemRevision obj : checkedOutObjects) {
                checkinFutures.add(checkinPipeline.checkin(obj, "Pipeline签入"));
            }
            checkinPipeline.execute();

            // 验证签入结果
            for (ResultFuture<ItemRevision> future : checkinFutures) {
                try {
                    future.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }) / 1_000_000;

        // 测试3：使用原生批量API方式
        List<ItemRevision> testData3 = prepareTestData(batchSize, "batch-api-test");
        List<ItemRevision> savedObjects3 = objectService.saveAll(testData3);

        long batchApiTime = TimerUtils.measureExecutionTime("原生批量API签出/签入 " + batchSize + " 个对象", () -> {
            // 批量签出
            List<ItemRevision> checkedOut = checkoutService.checkout(savedObjects3, "批量API测试", 60);
            // 批量签入
            checkoutService.checkin(checkedOut, "批量API签入");
        }) / 1_000_000;

        // 性能统计
        double pipelineSpeedup = (double) traditionalTime / pipelineTime;
        double batchApiSpeedup = (double) traditionalTime / batchApiTime;

        log.info("🚀 性能对比结果 ({} 个对象):", batchSize);
        log.info("   传统逐个调用: {}ms", traditionalTime);
        log.info("   Pipeline方式:  {}ms (提升 {}x)", pipelineTime, pipelineSpeedup);
        log.info("   原生批量API:  {}ms (提升 {}x)", batchApiTime, batchApiSpeedup);
        log.info("   Pipeline vs 批量API: {}x", (double) batchApiTime / pipelineTime);

        // 清理测试数据
        List<Long> allIds = new ArrayList<>();
        allIds.addAll(savedObjects.stream().map(ModelObject::getId).collect(Collectors.toList()));
        allIds.addAll(savedObjects2.stream().map(ModelObject::getId).collect(Collectors.toList()));
        allIds.addAll(savedObjects3.stream().map(ModelObject::getId).collect(Collectors.toList()));
        objectService.forceDelete(allIds);
    }

    /**
     * 准备测试数据
     */
    private List<ItemRevision> prepareTestData(int count) {
        return prepareTestData(count, "checkout-pipeline-test");
    }

    private List<ItemRevision> prepareTestData(int count, String prefix) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(idx -> {
                    ItemRevision rev = ItemRevision.newModel(prefix + "-" + dateCode + "-" + idx, revId);
                    rev.setName("Checkout测试对象-" + dateCode + "-" + idx);
                    return rev;
                })
                .collect(Collectors.toList());
    }
}