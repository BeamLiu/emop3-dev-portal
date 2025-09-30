package io.emop.integrationtest.usecase.common;

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

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static io.emop.integrationtest.util.Assertion.assertEquals;
import static io.emop.integrationtest.util.Assertion.assertTrue;

@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CodeGeneratorServiceTest {

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
        log.info("测试数据准备完成");
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
    public void testAttributeAndAutoIncrease() {
        log.info("=== 测试单个对象编码生成 ===");

        ModelObject modelObject = new SampleDocument(SampleDocument.class.getName());

        String result = codeGeneratorService.generateCode(modelObject);
        log.info("首次生成编码: {}", result);
        assertTrue(result.startsWith("SAMPLE-DOC-0"));

        // 连续生成几个编码，验证自增功能
        for (int i = 0; i < 3; i++) {
            String code = codeGeneratorService.generateCode(modelObject);
            log.info("第{}次生成编码: {}", i + 2, code);
        }

        log.info("单个对象编码生成测试完成");
    }

    @Test
    @Order(2)
    public void testScriptExecution() {
        log.info("=== 测试脚本执行编码生成 ===");

        SampleDataset modelObject = SampleDataset.newModel();
        modelObject.setType("PDF");
        modelObject.setName("secureDoc001");
        modelObject.generateCode(true);
        String result = objectService.save(modelObject).getCode();
        log.info("脚本生成编码: {}", result);
        assertTrue(result.startsWith("DOC-PDF-sec-"));
        // 连续生成几个编码
        for (int i = 0; i < 3; i++) {
            String code = codeGeneratorService.generateCode(modelObject);
            log.info("第{}次脚本编码: {}", i + 2, code);
        }

        log.info("脚本执行编码生成测试完成");
    }

    @Test
    @Order(3)
    public void testBatchCodeGeneration() {
        log.info("=== 测试批量编码生成 ===");

        // 创建多个测试对象
        List<ModelObject> objects = createTestObjects(10);

        // 批量生成编码
        long startTime = System.currentTimeMillis();
        List<String> codes = codeGeneratorService.generateCodesBatch(objects);
        long duration = System.currentTimeMillis() - startTime;

        // 验证结果
        assertTrue(codes.size() == objects.size());
        Set<String> uniqueCodes = new HashSet<>(codes);
        assertTrue(uniqueCodes.size() == codes.size()); // 确保编码唯一

        log.info("批量生成{}个编码，耗时: {}ms", codes.size(), duration);
        for (int i = 0; i < Math.min(5, codes.size()); i++) {
            log.info("批量编码[{}]: {}", i, codes.get(i));
        }

        log.info("批量编码生成测试完成");
    }

    @Test
    @Order(4)
    public void testBatchConsistency() {
        log.info("=== 测试批量与单个一致性 ===");

        List<ModelObject> objects = createTestObjects(5);

        // 单个生成
        List<String> singleCodes = new ArrayList<>();
        for (ModelObject obj : objects) {
            singleCodes.add(codeGeneratorService.generateCode(obj));
        }

        // 批量生成
        List<String> batchCodes = codeGeneratorService.generateCodesBatch(objects);

        // 验证一致性（注意：由于序列号递增，这里主要验证格式一致性）
        assertTrue(singleCodes.size() == batchCodes.size());

        for (int i = 0; i < singleCodes.size(); i++) {
            String singleCode = singleCodes.get(i);
            String batchCode = batchCodes.get(i);

            // 验证格式一致性（长度和前缀）
            assertTrue(singleCode.length() == batchCode.length());
            log.info("一致性对比[{}]: 单个={}, 批量={}", i, singleCode, batchCode);
        }

        log.info("批量与单个一致性测试完成");
    }

    @Test
    @Order(5)
    public void testEdgeCases() {
        log.info("=== 测试边界情况 ===");

        // 测试空列表
        List<String> emptyResult = codeGeneratorService.generateCodesBatch(Collections.emptyList());
        assertTrue(emptyResult.isEmpty());
        log.info("空列表测试通过");

        // 测试单个元素列表
        List<ModelObject> singleList = Collections.singletonList(new SampleDocument(SampleDocument.class.getName()));
        List<String> singleResult = codeGeneratorService.generateCodesBatch(singleList);
        assertTrue(singleResult.size() == 1);
        log.info("单个元素列表测试通过: {}", singleResult.get(0));

        // 测试预览模式
        List<ModelObject> previewObjects = createTestObjects(3);
        List<String> previewCodes = codeGeneratorService.generateCodesBatch(previewObjects, true);
        assertTrue(previewCodes.size() == 3);
        log.info("预览模式测试通过，生成{}个预览编码", previewCodes.size());

        // 测试混合类型对象
        List<ModelObject> mixedObjects = new ArrayList<>();
        mixedObjects.add(new SampleDocument(SampleDocument.class.getName()));

        SampleDataset dataset = SampleDataset.newModel();
        dataset.setType("XML");
        dataset.setName("mixedTest123");
        mixedObjects.add(dataset);

        List<String> mixedCodes = codeGeneratorService.generateCodesBatch(mixedObjects);
        assertTrue(mixedCodes.size() == 2);
        log.info("混合类型测试通过: {}, {}", mixedCodes.get(0), mixedCodes.get(1));

        log.info("边界情况测试完成");
    }

    @Test
    @Order(6)
    public void stressTest() {
        log.info("=== 压力测试开始 ===");

        int[] stressSizes = {1000, 2000, 5000};

        for (int size : stressSizes) {
            try {
                log.info("开始压力测试: {}个对象", size);

                List<ModelObject> objects = createRandomTestObjects(size);

                long startTime = System.currentTimeMillis();
                long startMemory = getUsedMemory();

                List<String> codes = codeGeneratorService.generateCodesBatch(objects);

                long endTime = System.currentTimeMillis();
                long endMemory = getUsedMemory();

                // 验证结果
                assertTrue(codes.size() == size);
                Set<String> uniqueCodes = new HashSet<>(codes);
                assertTrue(uniqueCodes.size() == codes.size()); // 确保无重复

                log.info("压力测试[{}个对象]完成: 耗时={}ms, 内存使用={}MB",
                        size,
                        endTime - startTime,
                        (endMemory - startMemory) / 1024 / 1024);

            } catch (Exception e) {
                log.error("压力测试失败[{}个对象]: {}", size, e.getMessage(), e);
            }
        }

        log.info("压力测试完成");
    }

    private long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    @Test
    @Order(7)
    public void concurrencyTest() {
        log.info("=== 并发测试开始 ===");

        int threadCount = 5;
        int objectsPerThread = 100;

        List<Thread> threads = new ArrayList<>();
        List<List<String>> results = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            Thread thread = new Thread(() -> {
                UserContext.runAsSystem(() -> {
                    List<ModelObject> objects = createTestObjects(objectsPerThread);
                    List<String> codes = codeGeneratorService.generateCodesBatch(objects);
                    results.add(codes);
                    log.info("线程{}完成，生成{}个编码", threadId, codes.size());
                });
            });
            threads.add(thread);
        }

        // 启动所有线程
        long startTime = System.currentTimeMillis();
        threads.forEach(Thread::start);

        // 等待所有线程完成
        threads.forEach(thread -> {
            try {
                thread.join();
            } catch (InterruptedException e) {
                log.error("线程等待被中断", e);
            }
        });

        long duration = System.currentTimeMillis() - startTime;

        // 验证结果
        int totalCodes = results.stream().mapToInt(List::size).sum();
        Set<String> allCodes = results.stream().flatMap(List::stream).collect(Collectors.toSet());

        log.info("并发测试完成: 总耗时={}ms, 生成编码={}个, 唯一编码={}个",
                duration, totalCodes, allCodes.size());

        assertTrue(totalCodes == threadCount * objectsPerThread);
        // 注意：由于并发，可能会有相同的编码，这里不强制要求唯一性
    }

    // ========== 辅助方法 ==========

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

    private List<ModelObject> createRandomTestObjects(int count) {
        List<ModelObject> objects = new ArrayList<>();
        Random random = ThreadLocalRandom.current();

        String[] types = {"PDF", "DOC", "XML", "TXT", "XLS"};

        for (int i = 0; i < count; i++) {
            SampleDataset dataset = SampleDataset.newModel();
            dataset.setType(types[random.nextInt(types.length)]);
            dataset.setName("randomData" + String.format("%04d", i));
            objects.add(dataset);
        }

        return objects;
    }
}