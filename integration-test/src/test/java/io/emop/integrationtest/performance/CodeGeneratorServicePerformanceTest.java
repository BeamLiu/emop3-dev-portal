package io.emop.integrationtest.performance;

import io.emop.model.common.AbstractModelObject;
import io.emop.model.common.CodeSequence;
import io.emop.model.common.ModelObject;
import io.emop.model.common.UserContext;
import io.emop.model.metadata.TypeDefinition;
import io.emop.model.query.Q;
import io.emop.integrationtest.domain.SampleDataset;
import io.emop.integrationtest.domain.SampleDocument;
import io.emop.service.S;
import io.emop.service.api.data.ObjectService;
import io.emop.service.api.domain.common.CodeGeneratorService;
import io.emop.service.api.metadata.MetadataService;
import io.emop.service.api.metadata.MetadataUpdateService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static io.emop.integrationtest.util.Assertion.assertTrue;

/**
 * CodeGeneratorService 性能测试类
 * 专门用于测试编码生成服务的性能表现
 */
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CodeGeneratorServicePerformanceTest {

    private CodeGeneratorService codeGeneratorService;
    private ObjectService objectService;

    @BeforeAll
    public void setup() {
        UserContext.setCurrentUser(new UserContext(100l, List.of("admin")));
        this.codeGeneratorService = S.service(CodeGeneratorService.class);
        this.objectService = S.service(ObjectService.class);
        S.withStrongConsistency(() -> prepareData());
    }

    private void prepareData() {
        // 清理测试数据
        cleanTestData();

        TypeDefinition modelObjectDefinition = Q.result(TypeDefinition.class)
                .where("name=?", AbstractModelObject.class.getName()).first();
        modelObjectDefinition.modifier()
                .codeGenPattern("${autoIncrease(scope=\"Rule\",start=\"0000000\",step=\"1\",max=\"9999999\")}");
        S.service(MetadataUpdateService.class).createOrUpdateType(modelObjectDefinition);
        S.service(MetadataService.class).reloadTypeDefinitions();

        String datasetCodePattern = "DOC-${attr(name=\"type\")}-${script(content=\"return modelObject.get('name').substring(0,3);\")}-${date(pattern=\"YYMM\")}-${alpha(scope=\"Element[1]\",start=\"AA\",max=\"ZZZZ\")}";
        TypeDefinition docDefinition = Q.result(TypeDefinition.class)
                .where("name=?", SampleDataset.class.getName()).first();
        docDefinition.modifier().codeGenPattern(datasetCodePattern);
        objectService.save(docDefinition);

        log.info("性能测试数据准备完成");
    }

    private void cleanTestData() {
        // 清理测试序列
        List<CodeSequence> testSequences = Q.result(CodeSequence.class)
                .where("pattern LIKE ?", "%test%")
                .query();
        if (!testSequences.isEmpty()) {
            objectService.delete(testSequences.stream().map(CodeSequence::getId).collect(Collectors.toList()));
        }
    }

    @Test
    @Order(1)
    public void testBatchPerformance() {
        log.info("=== 测试批量性能优化 ===");

        int[] testSizes = {10, 50, 100, 500};

        for (int size : testSizes) {
            List<ModelObject> objects = createTestObjects(size);

            // 测试单个生成性能
            long singleStart = System.currentTimeMillis();
            List<String> singleResults = new ArrayList<>();
            for (ModelObject obj : objects) {
                singleResults.add(codeGeneratorService.generateCode(obj));
            }
            long singleDuration = System.currentTimeMillis() - singleStart;

            // 重新创建对象以避免序列号冲突
            objects = createTestObjects(size);

            // 测试批量生成性能
            long batchStart = System.currentTimeMillis();
            List<String> batchResults = codeGeneratorService.generateCodesBatch(objects);
            long batchDuration = System.currentTimeMillis() - batchStart;

            // 计算性能提升
            double improvement = (double) singleDuration / batchDuration;

            log.info("性能测试[{}个对象]: 单个={}ms, 批量={}ms, 提升={}倍",
                    size, singleDuration, batchDuration, improvement);

            // 验证结果数量一致
            assertTrue(singleResults.size() == batchResults.size());
            assertTrue(singleResults.size() == size);
        }

        log.info("批量性能优化测试完成");
    }

    @Test
    @Order(2)
    public void performanceComparison() {
        log.info("=== 详细性能对比分析 ===");

        int[] sizes = {50, 100, 200, 500};

        log.info("| 数据量 | 单个方式(ms) | 批量方式(ms) | 性能提升 | 数据库调用优化 |");
        log.info("|--------|-------------|-------------|----------|---------------|");

        for (int size : sizes) {
            PerformanceResult result = comparePerformance(size);

            log.info("| {}个  | {}  | {}  | {}倍 | {}次 → 2次     |",
                    size,
                    result.singleDuration,
                    result.batchDuration,
                    result.improvement,
                    size * 2); // 每个对象大约2次数据库调用
        }

        log.info("性能对比分析完成");
    }

    /**
     * 性能测试结果数据结构
     */
    private static class PerformanceResult {
        final long singleDuration;
        final long batchDuration;
        final double improvement;

        public PerformanceResult(long singleDuration, long batchDuration) {
            this.singleDuration = singleDuration;
            this.batchDuration = batchDuration;
            this.improvement = batchDuration > 0 ? (double) singleDuration / batchDuration : 0;
        }
    }

    /**
     * 比较单个生成和批量生成的性能
     */
    private PerformanceResult comparePerformance(int size) {
        // 预热JVM
        warmupJVM();

        List<ModelObject> objects1 = createTestObjects(size);
        List<ModelObject> objects2 = createTestObjects(size);

        // 测试单个方式
        long singleStart = System.nanoTime();
        for (ModelObject obj : objects1) {
            codeGeneratorService.generateCode(obj);
        }
        long singleDuration = (System.nanoTime() - singleStart) / 1_000_000; // 转换为毫秒

        // 测试批量方式
        long batchStart = System.nanoTime();
        codeGeneratorService.generateCodesBatch(objects2);
        long batchDuration = (System.nanoTime() - batchStart) / 1_000_000; // 转换为毫秒

        return new PerformanceResult(singleDuration, batchDuration);
    }

    /**
     * JVM预热，确保测试结果准确
     */
    private void warmupJVM() {
        List<ModelObject> warmupObjects = createTestObjects(10);
        for (int i = 0; i < 3; i++) {
            for (ModelObject obj : warmupObjects) {
                codeGeneratorService.generateCode(obj);
            }
            codeGeneratorService.generateCodesBatch(warmupObjects);
        }
    }

    // ========== 辅助方法 ==========

    /**
     * 创建指定数量的测试对象
     */
    private List<ModelObject> createTestObjects(int count) {
        List<ModelObject> objects = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            if (i % 2 == 0) {
                // 创建SampleDocument
                objects.add(new SampleDocument(SampleDocument.class.getName()));
            } else {
                // 创建SampleDataset
                SampleDataset dataset = SampleDataset.newModel();
                dataset.setType("DOC");
                dataset.setName("testData" + String.format("%03d", i));
                objects.add(dataset);
            }
        }

        return objects;
    }
}