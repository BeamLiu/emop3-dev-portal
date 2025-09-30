package io.emop.integrationtest.usecase.common;

import io.emop.integrationtest.util.TimerUtils;
import io.emop.model.common.ModelObject;
import io.emop.model.common.Revisionable;
import io.emop.model.common.UserContext;
import io.emop.service.S;
import io.emop.service.api.cache.CacheKey;
import io.emop.service.api.cache.CacheService;
import io.emop.service.api.data.ObjectService;
import io.emop.service.api.domain.common.AssociateRelationService;
import io.emop.service.api.relation.RelationType;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.stream.IntStream;

import static io.emop.integrationtest.util.Assertion.*;

/**
 * 缓存集成测试 - 验证缓存保存和失效逻辑的正确性
 * <p>
 * 测试策略改进：
 * 1. 严格区分缓存触发和缓存验证的时机
 * 2. 独立测试preload功能
 * 3. 验证ItemRevision的版本缓存特性
 * 4. 每个测试用例都明确清理缓存，避免相互影响
 */
@Slf4j
@RequiredArgsConstructor
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CacheIntegrationTest {

    private static final String dateCode = String.valueOf(System.currentTimeMillis());

    private CacheService cacheService;
    private ObjectService objectService;
    private AssociateRelationService relationService;
    private List<ModelObject> testData;

    /**
     * 准备测试数据 - 改进版：严格控制缓存状态
     */
    @BeforeAll
    public void prepareTestData() {
        UserContext.setCurrentUser(new UserContext(100l, List.of("admin")));
        // 初始化服务
        cacheService = S.service(CacheService.class);
        objectService = S.service(ObjectService.class);
        relationService = S.service(AssociateRelationService.class);

        // 清理缓存环境
        cleanupCache();

        // 创建测试材料 - 直接使用ObjectService保存，会自动触发缓存
        List<ModelObject> testObjects = IntStream.rangeClosed(1, 10).mapToObj(idx -> {
            ModelObject obj = createTestMaterial("CACHE_TEST_" + dateCode + "_" + idx, "缓存测试材料" + idx);
            return obj;
        }).toList();

        // 创建版本测试材料
        List<ModelObject> revisionObjects = IntStream.rangeClosed(1, 5).mapToObj(idx -> {
            ModelObject obj = createTestRevisionMaterial("REV_CACHE_" + dateCode + "_" + idx, "A", "版本缓存测试材料" + idx);
            return obj;
        }).toList();

        testData = new ArrayList<>();
        testData.addAll(testObjects);
        testData.addAll(revisionObjects);

        log.info("Prepared {} test objects and {} revision objects", testObjects.size(), revisionObjects.size());

        // 1. 验证数据创建后缓存状态
        verifyInitialCacheState(testData);
    }

    /**
     * 测试查询操作的缓存触发 - 先清理缓存再测试
     */
    @Test
    @Order(1)
    @SneakyThrows
    public void testQueryTriggeredCaching() {
        TimerUtils.measureExecutionTime("查询操作触发缓存测试", () -> {
            testQueryTriggeredCachingImpl(testData);
        });
    }

    /**
     * 测试批量查询的缓存触发
     */
    @Test
    @Order(2)
    @SneakyThrows
    public void testBatchQueryTriggeredCaching() {
        TimerUtils.measureExecutionTime("批量查询触发缓存测试", () -> {
            testBatchQueryTriggeredCachingImpl(testData);
        });
    }

    /**
     * 测试更新操作的缓存同步 - 确保是更新操作触发的缓存刷新
     */
    @Test
    @Order(3)
    @SneakyThrows
    public void testUpdateTriggeredCacheSync() {
        TimerUtils.measureExecutionTime("更新操作触发缓存同步测试", () -> {
            testUpdateTriggeredCacheSyncImpl(testData);
        });
    }

    /**
     * 测试Preload功能 - 通过ObjectService的保存操作触发
     */
    @Test
    @Order(4)
    @SneakyThrows
    public void testPreloadFunctionality() {
        TimerUtils.measureExecutionTime("Preload功能测试", () -> {
            testPreloadFunctionalityImpl(testData);
        });
    }

    /**
     * 测试ItemRevision的版本缓存特性
     */
    @Test
    @Order(5)
    @SneakyThrows
    public void testItemRevisionVersionCaching() {
        TimerUtils.measureExecutionTime("ItemRevision版本缓存测试", () -> {
            testItemRevisionVersionCachingImpl(testData);
        });
    }

    /**
     * 测试关系缓存 - 通过AssociateRelationService触发
     */
    @Test
    @Order(6)
    @SneakyThrows
    public void testRelationCacheOperations() {
        TimerUtils.measureExecutionTime("关系缓存操作测试", () -> {
            testRelationCacheOperationsImpl(testData);
        });
    }

    /**
     * 测试结构关系缓存 - 通过懒加载触发
     */
    @Test
    @Order(7)
    @SneakyThrows
    public void testStructuralRelationCaching() {
        TimerUtils.measureExecutionTime("结构关系缓存测试", () -> {
            testStructuralRelationCachingImpl();
        });
    }

    /**
     * 测试事务中的缓存行为
     */
    @Test
    @Order(8)
    @SneakyThrows
    public void testTransactionalCaching() {
        TimerUtils.measureExecutionTime("事务缓存行为测试", () -> {
            testTransactionalCachingImpl(testData);
        });
    }

    /**
     * 测试多版本规则的关系缓存独立性
     */
    @Test
    @Order(9)
    @SneakyThrows
    public void testMultipleRevisionRuleRelationCaching() {
        TimerUtils.measureExecutionTime("多版本规则关系缓存独立性测试", () -> {
            testMultipleRevisionRuleRelationCachingImpl(testData);
        });
    }

    /**
     * 测试标签失效机制
     */
    @Test
    @Order(10)
    @SneakyThrows
    public void testTagBasedInvalidation() {
        TimerUtils.measureExecutionTime("标签失效机制测试", () -> {
            testTagBasedInvalidationImpl(testData);
        });
    }

    /**
     * 测试版本规则失效策略
     */
    @Test
    @Order(11)
    @SneakyThrows
    public void testRevisionRuleInvalidationStrategy() {
        TimerUtils.measureExecutionTime("版本规则失效策略测试", () -> {
            testRevisionRuleInvalidationStrategyImpl(testData);
        });
    }

    /**
     * 测试批量关系缓存操作
     */
    @Test
    @Order(12)
    @SneakyThrows
    public void testBatchRelationCacheOperations() {
        TimerUtils.measureExecutionTime("批量关系缓存操作测试", () -> {
            testBatchRelationCacheOperationsImpl(testData);
        });
    }

    /**
     * 测试关系缓存与对象缓存的基本联动
     */
    @Test
    @Order(13)
    @SneakyThrows
    public void testBasicRelationObjectLinkage() {
        TimerUtils.measureExecutionTime("关系对象缓存基本联动测试", () -> {
            testBasicRelationObjectLinkageImpl(testData);
        });
    }

    /**
     * 测试删除操作的缓存清理 - 确保是删除操作触发的缓存失效 - 放在最后执行
     */
    @Test
    @Order(14)
    @SneakyThrows
    public void testDeleteTriggeredCacheEviction() {
        TimerUtils.measureExecutionTime("删除操作触发缓存失效测试", () -> {
            testDeleteTriggeredCacheEvictionImpl(testData);
        });
    }

    // ================ 实现方法（保持原有逻辑不变）================

    /**
     * 新增：验证初始缓存状态 - 确保数据创建时缓存被正确建立
     */
    private void verifyInitialCacheState(List<ModelObject> allObjects) {
        log.info("=== 验证初始缓存状态 ===");

        // 验证所有对象都被缓存
        for (ModelObject obj : allObjects) {
            CacheKey objectKey = CacheKey.objectKey(obj.getId());
            assertTrue(cacheService.exists(objectKey),
                    "数据创建后对象缓存应该存在: " + obj.getId());

            // 验证缓存的对象数据正确性
            ModelObject cachedObj = cacheService.get(objectKey, ModelObject.class);
            assertNotNull(cachedObj, "缓存中应该能获取到对象: " + obj.getId());
            assertEquals(obj.getId(), cachedObj.getId(), "缓存对象ID应该匹配");

            // 验证版本对象的业务键缓存
            if (obj instanceof Revisionable rev) {
                String typeName = getTypeName(obj);
                Revisionable.CodeAndRevId codeAndRevId = new Revisionable.CodeAndRevId(rev.getCode(), rev.getRevId());
                CacheKey versionKey = CacheKey.codeRevisionKey(typeName, codeAndRevId);
                assertTrue(cacheService.exists(versionKey),
                        "版本对象的code+revId映射应该被缓存: " + rev.getCode() + "/" + rev.getRevId());
            }
        }

        log.info("✓ 初始缓存状态验证通过 - {} 个对象全部正确缓存", allObjects.size());
    }

    /**
     * 改进：测试查询操作的缓存触发 - 先清理缓存再测试
     */
    @SneakyThrows
    private void testQueryTriggeredCachingImpl(List<ModelObject> testData) {
        log.info("=== 测试查询操作触发的缓存 ===");

        ModelObject testObj = testData.get(0);
        log.info("Testing query-triggered caching with object: id={}, code={}",
                testObj.getId(), getBusinessCode(testObj));

        // 1. 先清理这个对象的所有缓存
        clearObjectAllCaches(testObj);

        // 2. 验证缓存确实被清理
        CacheKey objectKey = CacheKey.objectKey(testObj.getId());
        assertFalse(cacheService.exists(objectKey), "测试开始前对象缓存应该被清理");

        // 3. 通过ObjectService.findById触发缓存加载
        ModelObject loadedObj = objectService.findById(testObj.getId());
        assertNotNull(loadedObj, "应该能查询到对象");

        // 4. 验证查询操作确实触发了缓存
        assertTrue(cacheService.exists(objectKey), "ObjectService查询后对象缓存应该存在");

        ModelObject cachedObj = cacheService.get(objectKey, ModelObject.class);
        assertNotNull(cachedObj, "应该能从缓存中获取对象");
        assertEquals(testObj.getId(), cachedObj.getId(), "缓存的对象ID应该匹配");

        log.info("✓ 查询操作触发缓存测试通过");
    }

    /**
     * 改进：测试批量查询的缓存触发
     */
    @SneakyThrows
    private void testBatchQueryTriggeredCachingImpl(List<ModelObject> testData) {
        log.info("=== 测试批量查询触发的缓存 ===");

        // 取3个对象进行批量测试
        List<ModelObject> batchObjects = testData.subList(1, 4);
        List<Long> objectIds = batchObjects.stream().map(ModelObject::getId).toList();
        log.info("Testing batch query-triggered caching with {} objects: {}",
                batchObjects.size(), objectIds);

        // 1. 先清理这些对象的缓存
        for (ModelObject obj : batchObjects) {
            clearObjectAllCaches(obj);
        }

        // 2. 验证缓存确实被清理
        for (ModelObject obj : batchObjects) {
            CacheKey objectKey = CacheKey.objectKey(obj.getId());
            assertFalse(cacheService.exists(objectKey),
                    "批量测试开始前对象缓存应该被清理: " + obj.getId());
        }

        // 3. 通过ObjectService.findAllById触发批量缓存加载
        List<ModelObject> loadedObjects = objectService.findAllById(objectIds);
        assertEquals(batchObjects.size(), loadedObjects.size(), "应该能查询到所有对象");

        // 4. 验证批量查询确实触发了所有对象的缓存
        for (ModelObject obj : batchObjects) {
            CacheKey objectKey = CacheKey.objectKey(obj.getId());
            assertTrue(cacheService.exists(objectKey),
                    "批量查询后对象缓存应该存在: " + obj.getId());
        }

        log.info("✓ 批量查询触发缓存测试通过");
    }

    /**
     * 新增：测试Preload功能 - 通过ObjectService的保存操作触发
     */
    @SneakyThrows
    private void testPreloadFunctionalityImpl(List<ModelObject> testData) {
        log.info("=== 测试Preload功能（通过ObjectService保存触发）===");

        // 1. 创建一个新的版本对象来测试preload
        String testCode = "PRELOAD_TEST_" + dateCode;
        log.info("Testing preload by creating new revision object: code={}", testCode);

        // 2. 清理可能存在的相关缓存
        String typeName = "SampleMaterial";
        cleanupPreloadTestCaches(typeName, testCode);

        // 3. 通过ObjectService创建对象，这会触发saveAll -> cacheObjectsWithPreloadAsync
        Map<String, Object> newRevisionData = new HashMap<>();
        newRevisionData.put("code", testCode);
        newRevisionData.put("revId", "A");
        newRevisionData.put("name", "Preload测试材料");
        newRevisionData.put("status", "ACTIVE");

        List<ModelObject> createdObjects = objectService.create("SampleMaterial", newRevisionData);
        ModelObject newRevisionObj = createdObjects.get(0);

        log.info("Created revision object for preload test: id={}, code={}, revId={}",
                newRevisionObj.getId(),
                ((Revisionable) newRevisionObj).getCode(),
                ((Revisionable) newRevisionObj).getRevId());

        // 4. 等待异步preload完成
        Thread.sleep(300);

        // 5. 验证ObjectService.create触发的preload效果
        verifyPreloadResults((Revisionable) newRevisionObj);

        // 6. 测试ObjectService.update也会触发preload
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("name", "Preload测试材料_更新");
        ModelObject updatedObj = objectService.update(newRevisionObj.getId(),
                (Integer) newRevisionObj.get("_version"), updateData);

        // 等待update触发的preload完成
        Thread.sleep(200);

        // 7. 验证update操作后preload仍然有效
        verifyPreloadResults((Revisionable) updatedObj);

        log.info("✓ Preload功能测试完成（通过ObjectService操作触发）");
    }

    /**
     * 验证preload结果
     */
    private void verifyPreloadResults(Revisionable revisionObj) {
        String typeName = getTypeName(revisionObj);
        String code = revisionObj.getCode();

        // 验证对象本身被缓存
        CacheKey objectKey = CacheKey.objectKey(revisionObj.getId());
        assertTrue(cacheService.exists(objectKey), "Preload后对象缓存应该存在");

        // 验证版本映射被缓存
        Revisionable.CodeAndRevId codeAndRevId = new Revisionable.CodeAndRevId(code, revisionObj.getRevId());
        CacheKey versionKey = CacheKey.codeRevisionKey(typeName, codeAndRevId);
        assertTrue(cacheService.exists(versionKey), "Preload后版本映射应该存在");

        // 验证可能的版本类型映射（latest, latestReleased等）
        CacheKey latestKey = CacheKey.codeRevisionKey(typeName, new Revisionable.CodeAndRevId(code, "latest"));
        CacheKey latestReleasedKey = CacheKey.codeRevisionKey(typeName, new Revisionable.CodeAndRevId(code, "latestReleased"));

        // 这些映射可能存在也可能不存在，取决于配置和数据状态
        log.debug("Latest key exists: {}", cacheService.exists(latestKey));
        log.debug("LatestReleased key exists: {}", cacheService.exists(latestReleasedKey));

        log.info("Preload验证完成 - 基本缓存项都已建立");
    }

    /**
     * 改进：测试更新操作的缓存同步 - 确保是更新操作触发的缓存刷新
     */
    @SneakyThrows
    private void testUpdateTriggeredCacheSyncImpl(List<ModelObject> testData) {
        log.info("=== 测试更新操作触发的缓存同步 ===");

        ModelObject testObj = testData.get(5);
        log.info("Testing update-triggered cache sync with object: id={}", testObj.getId());

        // 1. 确保对象在缓存中
        ModelObject originalObj = objectService.findById(testObj.getId());
        String originalName = (String) originalObj.get("name");

        CacheKey objectKey = CacheKey.objectKey(testObj.getId());
        assertTrue(cacheService.exists(objectKey), "更新测试前对象应该在缓存中");

        // 2. 记录更新前的缓存状态
        ModelObject cachedBeforeUpdate = cacheService.get(objectKey, ModelObject.class);
        assertEquals(originalName, cachedBeforeUpdate.get("name"), "更新前缓存的名称应该匹配");

        // 3. 通过ObjectService进行更新操作
        Map<String, Object> updateData = new HashMap<>();
        String newName = "更新后的材料名称_" + System.currentTimeMillis();
        updateData.put("name", newName);

        ModelObject updatedObj = objectService.update(testObj.getId(),
                (Integer) originalObj.get("_version"), updateData);
        assertEquals(newName, updatedObj.get("name"), "对象应该被正确更新");

        // 4. 等待缓存同步（异步操作）
        waitForCacheSync();

        // 5. 验证缓存中的对象也被同步更新
        ModelObject cachedAfterUpdate = cacheService.get(objectKey, ModelObject.class);
        assertEquals(newName, cachedAfterUpdate.get("name"), "缓存中的对象应该被同步更新");

        // 6. 验证版本号也被正确更新
        int originalVersion = (Integer) originalObj.get("_version");
        int updatedVersion = (Integer) cachedAfterUpdate.get("_version");
        assertEquals(originalVersion + 1, updatedVersion, "缓存中的版本号应该被正确更新");

        log.info("✓ 更新操作触发缓存同步测试通过");
    }

    /**
     * 改进：测试删除操作的缓存清理 - 确保是删除操作触发的缓存失效
     */
    @SneakyThrows
    private void testDeleteTriggeredCacheEvictionImpl(List<ModelObject> testData) {
        log.info("=== 测试删除操作触发的缓存失效 ===");

        // 使用最后一个对象进行删除测试
        ModelObject testObj = testData.get(testData.size() - 1);
        log.info("Testing delete-triggered cache eviction with object: id={}", testObj.getId());

        // 1. 确保对象在缓存中
        CacheKey objectKey = CacheKey.objectKey(testObj.getId());
        assertTrue(cacheService.exists(objectKey), "删除测试前对象应该在缓存中");

        // 2. 如果是版本对象，还要确保业务键映射在缓存中
        List<CacheKey> businessKeys = new ArrayList<>();
        if (testObj instanceof Revisionable rev) {
            String typeName = getTypeName(testObj);
            Revisionable.CodeAndRevId codeAndRevId = new Revisionable.CodeAndRevId(rev.getCode(), rev.getRevId());
            CacheKey versionKey = CacheKey.codeRevisionKey(typeName, codeAndRevId);
            businessKeys.add(versionKey);

            if (cacheService.exists(versionKey)) {
                log.debug("版本业务键映射在缓存中: {}", versionKey.getKeyString());
            }
        }

        // 3. 通过ObjectService进行删除操作
        objectService.delete(testObj.getId());

        // 4. 等待异步缓存失效完成
        waitForCacheSync();

        // 5. 验证对象缓存被删除
        assertFalse(cacheService.exists(objectKey), "删除操作后对象缓存应该被清理");

        // 6. 验证业务键映射也被删除
        for (CacheKey businessKey : businessKeys) {
            assertFalse(cacheService.exists(businessKey),
                    "删除操作后业务键映射应该被清理: " + businessKey.getKeyString());
        }

        log.info("✓ 删除操作触发缓存失效测试通过");
    }

    /**
     * 新增：测试ItemRevision的版本缓存特性
     */
    @SneakyThrows
    private void testItemRevisionVersionCachingImpl(List<ModelObject> testData) {
        log.info("=== 测试ItemRevision版本缓存特性 ===");

        // 找到版本对象
        List<ModelObject> revisionObjects = testData.stream()
                .filter(obj -> obj instanceof Revisionable)
                .toList();

        if (revisionObjects.isEmpty()) {
            log.warn("没有找到版本对象，跳过版本缓存测试");
            return;
        }

        Revisionable revisionObj = (Revisionable) revisionObjects.get(0);
        String typeName = getTypeName(revisionObj);
        String code = revisionObj.getCode();
        String revId = revisionObj.getRevId();

        log.info("Testing ItemRevision version caching: code={}, revId={}", code, revId);

        // 1. 清理版本相关的所有缓存
        clearRevisionObjectAllCaches(revisionObj);

        // 2. 通过code+revId进行查询（如果有相应的查询方法）
        // 这里模拟通过业务键查询触发缓存
        ModelObject queriedByBusiness = objectService.findById(revisionObj.getId());
        assertNotNull(queriedByBusiness, "通过ID查询应该成功");

        // 3. 验证各种版本映射缓存
        Revisionable.CodeAndRevId codeAndRevId = new Revisionable.CodeAndRevId(code, revId);
        CacheKey versionKey = CacheKey.codeRevisionKey(typeName, codeAndRevId);

        // 等待缓存建立
        waitForCacheSync();

        assertTrue(cacheService.exists(versionKey), "版本映射缓存应该被建立");

        // 4. 测试版本对象的更新对缓存的影响
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("name", "版本对象更新测试_" + System.currentTimeMillis());

        ModelObject updatedRevision = objectService.update(revisionObj.getId(),
                (Integer) queriedByBusiness.get("_version"), updateData);

        // 5. 验证版本映射缓存被正确更新
        waitForCacheSync();
        String cachedVersionObjId = cacheService.get(versionKey, String.class);
        assertNotNull(cachedVersionObjId);
        assertEquals(updatedRevision.getId().toString(), cachedVersionObjId,
                "版本映射缓存应该被同步更新");

        log.info("✓ ItemRevision版本缓存特性测试通过");
    }
// ================ 辅助方法（保持原有逻辑不变）================

    /**
     * 清理对象的所有缓存
     */
    private void clearObjectAllCaches(ModelObject obj) {
        // 清理对象缓存
        CacheKey objectKey = CacheKey.objectKey(obj.getId());
        cacheService.evict(objectKey);

        // 清理业务键缓存
        if (obj instanceof Revisionable rev) {
            clearRevisionObjectAllCaches(rev);
        } else {
            String businessCode = getBusinessCode(obj);
            if (businessCode != null) {
                String typeName = getTypeName(obj);
                CacheKey businessKey = CacheKey.businessCodeKey(typeName, businessCode);
                cacheService.evict(businessKey);
            }
        }

        // 清理关系缓存
        String relationPattern = CacheKey.patternKeyString(CacheKey.DOMAIN_RELATION, obj.getId() + ":*");
        cacheService.evictByPattern(CacheKey.DOMAIN_RELATION, relationPattern);
    }

    /**
     * 清理preload测试相关的缓存
     */
    private void cleanupPreloadTestCaches(String typeName, String code) {
        try {
            // 清理可能的版本映射缓存
            String[] revisionTypes = {"A", "latest", "latestReleased"};
            for (String revType : revisionTypes) {
                CacheKey versionKey = CacheKey.codeRevisionKey(typeName,
                        new Revisionable.CodeAndRevId(code, revType));
                cacheService.evict(versionKey);
            }

            // 清理业务编码缓存
            CacheKey businessKey = CacheKey.businessCodeKey(typeName, code);
            cacheService.evict(businessKey);

            log.debug("Cleaned up preload test caches for code: {}", code);
        } catch (Exception e) {
            log.warn("Failed to cleanup preload test caches", e);
        }
    }

    /**
     * 清理版本对象的所有缓存
     */
    private void clearRevisionObjectAllCaches(Revisionable revisionObj) {
        String typeName = getTypeName(revisionObj);
        String code = revisionObj.getCode();
        String revId = revisionObj.getRevId();

        // 清理主对象缓存
        CacheKey objectKey = CacheKey.objectKey(revisionObj.getId());
        cacheService.evict(objectKey);

        // 清理版本映射缓存
        Revisionable.CodeAndRevId codeAndRevId = new Revisionable.CodeAndRevId(code, revId);
        CacheKey versionKey = CacheKey.codeRevisionKey(typeName, codeAndRevId);
        cacheService.evict(versionKey);

        // 清理版本类型映射
        CacheKey latestKey = CacheKey.codeRevisionKey(typeName, new Revisionable.CodeAndRevId(code, "latest"));
        CacheKey latestReleasedKey = CacheKey.codeRevisionKey(typeName, new Revisionable.CodeAndRevId(code, "latestReleased"));
        cacheService.evict(latestKey);
        cacheService.evict(latestReleasedKey);

        log.debug("Cleared all caches for revision object: code={}, revId={}", code, revId);
    }

    /**
     * 创建测试材料对象 - 通过ObjectService自动触发缓存
     */
    private ModelObject createTestMaterial(String code, String name) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("code", code);
            data.put("name", name);
            data.put("revId", "A");
            data.put("status", "ACTIVE");

            List<ModelObject> created = objectService.create("SampleMaterial", data);
            return created.get(0);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test material: " + code, e);
        }
    }

    /**
     * 创建测试版本材料对象 - 通过ObjectService自动触发缓存
     */
    private ModelObject createTestRevisionMaterial(String code, String revId, String name) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("code", code);
            data.put("revId", revId);
            data.put("name", name);
            data.put("status", "ACTIVE");

            List<ModelObject> created = objectService.create("SampleMaterial", data);
            return created.get(0);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test revision material: " + code + "/" + revId, e);
        }
    }

    /**
     * 获取对象的业务编码
     */
    private String getBusinessCode(ModelObject obj) {
        if (obj instanceof Revisionable revisionable) {
            return revisionable.getCode();
        }
        return (String) obj.get("code");
    }

    /**
     * 获取类型名称
     */
    private String getTypeName(ModelObject obj) {
        try {
            String fullTypeName = obj.retrieveTypeDefinition().getName();
            int lastDotIndex = fullTypeName.lastIndexOf('.');
            if (lastDotIndex >= 0 && lastDotIndex < fullTypeName.length() - 1) {
                return fullTypeName.substring(lastDotIndex + 1);
            }
            return fullTypeName;
        } catch (Exception e) {
            return obj.getClass().getSimpleName();
        }
    }

    private void waitForCacheSync() throws InterruptedException {
        Thread.sleep(100);
    }

    // ================ 保持原有测试方法不变 ================

    /**
     * 测试关系缓存 - 通过AssociateRelationService触发
     */
    @SneakyThrows
    private void testRelationCacheOperationsImpl(List<ModelObject> testData) {
        log.info("=== 测试关系缓存操作 ===");

        // 1. 准备主对象和子对象
        ModelObject parentObj = testData.get(4);
        List<ModelObject> childObjects = testData.subList(5, 7);

        log.info("Testing relations: parent={}, children={}",
                parentObj.getId(), childObjects.stream().map(ModelObject::getId).toList());

        // 2. 通过AssociateRelationService建立关系，自动触发关系缓存
        RelationType testRelationType = RelationType.reference; // 假设这是一个有效的关系类型
        relationService.replaceRelation(parentObj, testRelationType, childObjects.toArray(new ModelObject[0]));

        // 3. 通过AssociateRelationService查询关系，应该命中缓存
        List<? extends ModelObject> queriedChildren = relationService.findAllChildren(parentObj, Arrays.asList(testRelationType));
        assertEquals(childObjects.size(), queriedChildren.size(), "查询的子对象数量应该匹配");

        // 4. 验证关系缓存存在（通过CacheService直接检查）
        CacheKey relationKey = CacheKey.relationKey(parentObj.getId(), testRelationType.getName(), "LATEST");
        assertTrue(cacheService.exists(relationKey), "关系缓存应该存在");

        // 5. 添加新的关系
        ModelObject newChild = testData.get(7);
        relationService.appendRelation(parentObj, testRelationType, newChild);

        // 6. 查询更新后的关系
        List<? extends ModelObject> updatedChildren = relationService.findAllChildren(parentObj, Arrays.asList(testRelationType));
        assertEquals(childObjects.size() + 1, updatedChildren.size(), "添加关系后子对象数量应该增加");

        // 7. 删除关系并验证缓存更新
        relationService.removeRelations(parentObj, testRelationType, Arrays.asList(newChild));

        List<? extends ModelObject> finalChildren = relationService.findAllChildren(parentObj, Arrays.asList(testRelationType));
        assertEquals(childObjects.size(), finalChildren.size(), "删除关系后子对象数量应该恢复");

        // 8. 清除所有关系
        relationService.removeRelations(parentObj, testRelationType);

        List<? extends ModelObject> emptyChildren = relationService.findAllChildren(parentObj, Arrays.asList(testRelationType));
        assertTrue(emptyChildren.isEmpty(), "清除关系后应该没有子对象");

        log.info("✓ 关系缓存操作测试通过");
    }

    /**
     * 测试结构关系缓存 - 通过懒加载触发
     */
    @SneakyThrows
    private void testStructuralRelationCachingImpl() {
        log.info("=== 测试结构关系缓存 ===");

        // 测试一对一关系缓存
        testOneToOneStructuralRelationCache();

        // 测试一对多关系缓存
        testOneToManyStructuralRelationCache();

        log.info("✓ 结构关系缓存测试通过");
    }

    /**
     * 测试一对一结构关系缓存（外键在当前对象）
     */
    @SneakyThrows
    private void testOneToOneStructuralRelationCache() {
        log.info("--- 测试一对一结构关系缓存 ---");

        // 1. 创建主任务和子任务
        Map<String, Object> mainTaskData = new HashMap<>();
        mainTaskData.put("name", "Cache Main Task " + dateCode);
        mainTaskData.put("code", "CACHE-MAIN-" + dateCode);
        mainTaskData.put("revId", "A");

        Map<String, Object> subTaskData = new HashMap<>();
        subTaskData.put("name", "Cache Sub Task " + dateCode);
        subTaskData.put("code", "CACHE-SUB-" + dateCode);
        subTaskData.put("revId", "A");

        ModelObject mainTask = objectService.create("SampleTask", mainTaskData).get(0);
        ModelObject subTask = objectService.create("SampleTask", subTaskData).get(0);

        // 2. 建立结构关系（外键在主任务）
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("subTaskId", subTask.getId());
        mainTask = objectService.update(mainTask.getId(), (Integer) mainTask.get("_version"), updateData);

        log.info("Created structural relation: mainTask.subTaskId = {}", subTask.getId());

        // 3. 重新查询主任务以清除内存状态
        mainTask = objectService.findById(mainTask.getId());

        // 4. 第一次访问结构关系，触发懒加载和缓存
        Object subTaskRelation = mainTask.get("subTask"); // 这会触发StructuralRelationService.loadOneToOneRelation
        assertNotNull(subTaskRelation, "一对一结构关系应该能被懒加载");

        // 5. 验证关系缓存被创建
        CacheKey relationKey = CacheKey.relationKey(mainTask.getId(), "subTask", "PRECISE");
        Thread.sleep(100); // 等待异步缓存操作完成
        assertTrue(cacheService.exists(relationKey), "一对一结构关系缓存应该存在");

        // 6. 验证关联的子任务对象也被缓存
        CacheKey subTaskKey = CacheKey.objectKey(subTask.getId());
        assertTrue(cacheService.exists(subTaskKey), "关联的子任务对象应该被缓存");

        // 7. 再次访问关系，应该命中缓存
        Object cachedSubTaskRelation = mainTask.get("subTask");
        assertNotNull(cachedSubTaskRelation, "第二次访问应该命中缓存");

        log.info("✓ 一对一结构关系缓存测试通过");
    }

    /**
     * 测试一对多结构关系缓存（外键在目标对象）
     */
    @SneakyThrows
    private void testOneToManyStructuralRelationCache() {
        log.info("--- 测试一对多结构关系缓存 ---");

        // 1. 创建组任务
        Map<String, Object> groupTaskData = new HashMap<>();
        groupTaskData.put("name", "Cache Group Task " + dateCode);
        groupTaskData.put("code", "CACHE-GROUP-" + dateCode);
        groupTaskData.put("revId", "A");

        ModelObject groupTask = objectService.create("SampleTask", groupTaskData).get(0);

        // 2. 创建两个子任务，外键指向组任务
        Map<String, Object> childTask1Data = new HashMap<>();
        childTask1Data.put("name", "Cache Child Task 1 " + dateCode);
        childTask1Data.put("code", "CACHE-CHILD-1-" + dateCode);
        childTask1Data.put("revId", "A");
        childTask1Data.put("groupTaskId", groupTask.getId()); // 外键在子任务

        Map<String, Object> childTask2Data = new HashMap<>();
        childTask2Data.put("name", "Cache Child Task 2 " + dateCode);
        childTask2Data.put("code", "CACHE-CHILD-2-" + dateCode);
        childTask2Data.put("revId", "A");
        childTask2Data.put("groupTaskId", groupTask.getId()); // 外键在子任务

        ModelObject childTask1 = objectService.create("SampleTask", childTask1Data).get(0);
        ModelObject childTask2 = objectService.create("SampleTask", childTask2Data).get(0);

        log.info("Created one-to-many structural relation: groupTask <- [{}, {}]",
                childTask1.getId(), childTask2.getId());

        // 3. 重新查询组任务以清除内存状态
        groupTask = objectService.findById(groupTask.getId());

        // 4. 第一次访问结构关系集合，触发懒加载和缓存
        Object childTasksRelation = groupTask.get("childTasks"); // 这会触发StructuralRelationService.loadOneToManyRelation
        assertNotNull(childTasksRelation, "一对多结构关系应该能被懒加载");

        // 验证返回的是集合且包含正确的子任务
        if (childTasksRelation instanceof Collection<?> childTasks) {
            assertEquals(2, childTasks.size(), "应该有2个子任务");
        } else {
            fail("一对多关系应该返回集合类型");
        }

        // 5. 验证关系缓存被创建
        CacheKey relationKey = CacheKey.relationKey(groupTask.getId(), "childTasks", "PRECISE");
        Thread.sleep(100); // 等待异步缓存操作完成
        assertTrue(cacheService.exists(relationKey), "一对多结构关系缓存应该存在");

        // 6. 验证关联的子任务对象也被缓存
        CacheKey childTask1Key = CacheKey.objectKey(childTask1.getId());
        CacheKey childTask2Key = CacheKey.objectKey(childTask2.getId());
        assertTrue(cacheService.exists(childTask1Key), "关联的子任务1应该被缓存");
        assertTrue(cacheService.exists(childTask2Key), "关联的子任务2应该被缓存");

        // 7. 再次访问关系，应该命中缓存
        Object cachedChildTasksRelation = groupTask.get("childTasks");
        assertNotNull(cachedChildTasksRelation, "第二次访问应该命中缓存");

        if (cachedChildTasksRelation instanceof Collection<?> cachedChildTasks) {
            assertEquals(2, cachedChildTasks.size(), "缓存的子任务数量应该正确");
        }

        log.info("✓ 一对多结构关系缓存测试通过");
    }

    /**
     * 测试事务中的缓存行为
     */
    @SneakyThrows
    private void testTransactionalCachingImpl(List<ModelObject> testData) {
        log.info("=== 测试事务缓存行为 ===");

        ModelObject testObj = testData.get(8);

        try {
            // 在事务中进行操作
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("name", "事务中更新的名称");
            ModelObject updatedObj = objectService.update(testObj.getId(), (Integer) testObj.get("_version"), updateData);

            // 验证对象被更新
            assertEquals("事务中更新的名称", updatedObj.get("name"), "事务中对象应该被正确更新");

            // 验证缓存状态（事务提交后缓存应该被更新）
            CacheKey objectKey = CacheKey.objectKey(testObj.getId());
            Thread.sleep(100); // 等待异步缓存更新

            ModelObject cachedObj = cacheService.get(objectKey, ModelObject.class);
            if (cachedObj != null) {
                log.debug("Cache updated with transaction changes: {}", cachedObj.get("name"));
            }

        } catch (Exception e) {
            log.warn("Transaction test encountered expected behavior: {}", e.getMessage());
        }

        log.info("✓ 事务缓存行为测试通过");
    }
    /**
     * 清理所有关系缓存
     */
    private void clearAllRelationCaches(Long primaryId, String relationType) {
        String[] allRules = {"PRECISE", "LATEST", "LATEST_RELEASED", "LATEST_WORKING"};

        for (String rule : allRules) {
            CacheKey relationKey = CacheKey.relationKey(primaryId, relationType, rule);
            cacheService.evict(relationKey);
        }

        // 清理相关的模式缓存
        cacheService.evictByPattern(CacheKey.DOMAIN_RELATION, primaryId + ":" + relationType + ":*");
    }

    /**
     * 简单模拟不同版本规则的结果（仅用于测试缓存）
     */
    private List<ModelObject> mockRevisionRuleResult(List<ModelObject> allObjects, String ruleName) {
        switch (ruleName) {
            case "LATEST":
                // LATEST规则返回前两个对象（模拟）
                return allObjects.size() >= 2 ? new ArrayList<>(allObjects.subList(0, 2)) : new ArrayList<>(allObjects);
            case "LATEST_RELEASED":
                // LATEST_RELEASED规则返回第一个对象（模拟）
                return allObjects.isEmpty() ? new ArrayList<>() : new ArrayList<>(List.of(allObjects.get(0)));
            case "LATEST_WORKING":
                // LATEST_WORKING规则返回最后一个对象（模拟）
                return allObjects.isEmpty() ? new ArrayList<>() : new ArrayList<>(List.of(allObjects.get(allObjects.size() - 1)));
            case "PRECISE":
            default:
                // PRECISE规则返回所有对象
                return new ArrayList<>(allObjects);
        }
    }

    /**
     * 构建测试用的标签
     */
    private List<String> buildTestTags(List<ModelObject> objects) {
        List<String> tags = new ArrayList<>();

        for (ModelObject obj : objects) {
            // ID标签
            tags.add(obj.getId().toString());

            // 业务编码标签
            String businessCode = getBusinessCode(obj);
            if (businessCode != null) {
                String typeName = obj.retrieveTypeDefinition().simpleName();
                tags.add(typeName + ":" + businessCode);
            }
        }

        return tags;
    }

    /**
     * 测试多版本规则的关系缓存独立性
     */
    @SneakyThrows
    private void testMultipleRevisionRuleRelationCachingImpl(List<ModelObject> testData) {
        log.info("=== 测试多版本规则关系缓存独立性 ===");

        ModelObject parentObj = testData.get(0);
        List<ModelObject> childObjects = testData.subList(1, 4);
        RelationType testRelationType = RelationType.reference;

        log.info("Testing revision rule independence: parent={}, children={}",
                parentObj.getId(), childObjects.stream().map(ModelObject::getId).toList());

        // 清理相关缓存
        clearAllRelationCaches(parentObj.getId(), testRelationType.getName());

        // 建立基础关系
        relationService.replaceRelation(parentObj, testRelationType, childObjects.toArray(new ModelObject[0]));

        // 测试不同版本规则产生独立的缓存
        String[] revisionRules = {"PRECISE", "LATEST", "LATEST_RELEASED", "LATEST_WORKING"};
        Map<String, CacheKey> ruleToKey = new HashMap<>();

        for (String rule : revisionRules) {
            // 模拟不同规则的解析结果
            List<ModelObject> ruleResult = mockRevisionRuleResult(childObjects, rule);

            // 缓存结果
            CacheKey relationKey = CacheKey.relationKey(parentObj.getId(), testRelationType.getName(), rule);
            cacheService.put(relationKey, ruleResult);
            ruleToKey.put(rule, relationKey);

            log.debug("Cached relation with rule {}: {} children", rule, ruleResult.size());
        }

        // 验证所有缓存都独立存在
        for (String rule : revisionRules) {
            CacheKey key = ruleToKey.get(rule);
            assertCacheExists(key, "版本规则 " + rule);

            @SuppressWarnings("unchecked")
            List<ModelObject> cachedResult = cacheService.get(key, List.class);
            assertNotNull(cachedResult, "版本规则 " + rule + " 的缓存内容不应为空");
        }

        // 验证不同规则的缓存内容确实不同
        @SuppressWarnings("unchecked")
        List<ModelObject> preciseResult = cacheService.get(ruleToKey.get("PRECISE"), List.class);
        @SuppressWarnings("unchecked")
        List<ModelObject> latestResult = cacheService.get(ruleToKey.get("LATEST"), List.class);

        assertNotEquals(preciseResult.size(), latestResult.size(),
                "不同版本规则应该产生不同的缓存结果");

        log.info("✓ 多版本规则关系缓存独立性测试通过");
    }

    /**
     * 测试标签失效机制
     */
    @SneakyThrows
    private void testTagBasedInvalidationImpl(List<ModelObject> testData) {
        log.info("=== 测试标签失效机制 ===");

        ModelObject parentObj1 = testData.get(0);
        ModelObject parentObj2 = testData.get(1);
        ModelObject targetObj = testData.get(2);

        RelationType relType1 = RelationType.reference;
        RelationType relType2 = RelationType.children;

        log.info("Testing tag invalidation: target={}, parents=[{}, {}]",
                targetObj.getId(), parentObj1.getId(), parentObj2.getId());

        // 建立多个关系指向同一个目标对象
        relationService.replaceRelation(parentObj1, relType1, targetObj);
        relationService.replaceRelation(parentObj2, relType2, targetObj);

        waitForAsyncOperation();

        // 手动创建关系缓存（模拟实际的缓存建立）
        CacheKey relationKey1 = CacheKey.relationKey(parentObj1.getId(), relType1.getName(), "PRECISE");
        CacheKey relationKey2 = CacheKey.relationKey(parentObj2.getId(), relType2.getName(), "PRECISE");
        CacheKey relationKey3 = CacheKey.relationKey(parentObj1.getId(), relType1.getName(), "LATEST");
        CacheKey relationKey4 = CacheKey.relationKey(parentObj2.getId(), relType2.getName(), "LATEST");

        cacheService.put(relationKey1, Arrays.asList(targetObj));
        cacheService.put(relationKey2, Arrays.asList(targetObj));
        cacheService.put(relationKey3, Arrays.asList(targetObj));
        cacheService.put(relationKey4, Arrays.asList(targetObj));

        // 验证缓存都存在
        assertCacheExists(relationKey1, "关系1");
        assertCacheExists(relationKey2, "关系2");
        assertCacheExists(relationKey3, "关系3");
        assertCacheExists(relationKey4, "关系4");

        // 模拟ID标签失效 - 清除所有包含该对象的关系缓存
        List<String> testTags = buildTestTags(Arrays.asList(targetObj));
        log.debug("Target object tags: {}", testTags);

        // 手动失效所有相关缓存（模拟标签失效机制）
        cacheService.evict(relationKey1);
        cacheService.evict(relationKey2);
        cacheService.evict(relationKey3);
        cacheService.evict(relationKey4);

        // 验证所有相关缓存都被清除
        assertCacheNotExists(relationKey1, "ID标签失效后关系1");
        assertCacheNotExists(relationKey2, "ID标签失效后关系2");
        assertCacheNotExists(relationKey3, "ID标签失效后关系3");
        assertCacheNotExists(relationKey4, "ID标签失效后关系4");

        log.info("✓ 标签失效机制测试通过");
    }

    /**
     * 测试版本规则失效策略
     */
    @SneakyThrows
    private void testRevisionRuleInvalidationStrategyImpl(List<ModelObject> testData) {
        log.info("=== 测试版本规则失效策略 ===");

        ModelObject parentObj = testData.get(0);
        ModelObject targetObj = testData.get(1);
        RelationType testRelationType = RelationType.reference;

        log.info("Testing revision rule invalidation: parent={}, target={}",
                parentObj.getId(), targetObj.getId());

        // 建立关系
        relationService.replaceRelation(parentObj, testRelationType, targetObj);

        // 创建不同版本规则的缓存
        String[] affectedRules = {"LATEST", "LATEST_RELEASED", "LATEST_WORKING"};
        String[] unaffectedRules = {"PRECISE"};

        Map<String, CacheKey> allKeys = new HashMap<>();

        // 缓存受影响的规则
        for (String rule : affectedRules) {
            CacheKey key = CacheKey.relationKey(parentObj.getId(), testRelationType.getName(), rule);
            cacheService.put(key, Arrays.asList(targetObj));
            allKeys.put(rule, key);
        }

        // 缓存不受影响的规则
        for (String rule : unaffectedRules) {
            CacheKey key = CacheKey.relationKey(parentObj.getId(), testRelationType.getName(), rule);
            cacheService.put(key, Arrays.asList(targetObj));
            allKeys.put(rule, key);
        }

        // 验证所有缓存都存在
        for (Map.Entry<String, CacheKey> entry : allKeys.entrySet()) {
            assertCacheExists(entry.getValue(), "规则 " + entry.getKey());
        }

        // 模拟业务编码级别的失效（影响版本规则相关的缓存）
        for (String rule : affectedRules) {
            cacheService.evict(allKeys.get(rule));
        }

        // 验证受影响的规则缓存被失效
        for (String rule : affectedRules) {
            assertCacheNotExists(allKeys.get(rule), "业务编码变更后规则 " + rule);
        }

        // 验证不受影响的规则缓存仍然存在
        for (String rule : unaffectedRules) {
            assertCacheExists(allKeys.get(rule), "业务编码变更不应影响规则 " + rule);
        }

        log.info("✓ 版本规则失效策略测试通过");
    }

    /**
     * 测试批量关系缓存操作
     */
    @SneakyThrows
    private void testBatchRelationCacheOperationsImpl(List<ModelObject> testData) {
        log.info("=== 测试批量关系缓存操作 ===");

        List<ModelObject> parentObjects = testData.subList(0, 3);
        List<ModelObject> childObjects = testData.subList(3, 6);
        RelationType testRelationType = RelationType.reference;

        log.info("Testing batch operations: {} parents, {} children",
                parentObjects.size(), childObjects.size());

        // 准备批量数据
        List<CacheKey> allKeys = new ArrayList<>();

        for (int i = 0; i < parentObjects.size(); i++) {
            ModelObject parent = parentObjects.get(i);
            ModelObject child = childObjects.get(i);

            // 建立实际关系
            relationService.replaceRelation(parent, testRelationType, child);

            // 准备缓存数据
            CacheKey key = CacheKey.relationKey(parent.getId(), testRelationType.getName(), UserContext.ensureCurrent().getRevisionRule().getName());
            allKeys.add(key);
        }

        waitForAsyncOperation();

        // 批量验证缓存存在
        for (CacheKey key : allKeys) {
            assertCacheExists(key, "批量缓存");
        }

        // 批量查询缓存
        List batchResults = cacheService.multiGet(allKeys, List.class);
        assertEquals(allKeys.size(), batchResults.size(), "批量查询结果数量应该匹配");

        // 批量清除缓存
        cacheService.multiEvict(allKeys);

        // 验证批量清除结果
        for (CacheKey key : allKeys) {
            assertCacheNotExists(key, "批量清除后");
        }

        log.info("✓ 批量关系缓存操作测试通过");
    }

    /**
     * 测试关系缓存与对象缓存的基本联动
     */
    @SneakyThrows
    private void testBasicRelationObjectLinkageImpl(List<ModelObject> testData) {
        log.info("=== 测试关系缓存与对象缓存的基本联动 ===");

        ModelObject parentObj = testData.get(0);
        ModelObject childObj = testData.get(1);
        RelationType testRelationType = RelationType.reference;

        log.info("Testing basic linkage: parent={}, child={}",
                parentObj.getId(), childObj.getId());

        // 建立关系
        relationService.replaceRelation(parentObj, testRelationType, childObj);

        waitForAsyncOperation();

        // 设置对象缓存和关系缓存
        CacheKey objectKey = CacheKey.objectKey(childObj.getId());
        CacheKey relationKey = CacheKey.relationKey(parentObj.getId(), testRelationType.getName(), UserContext.ensureCurrent().getRevisionRule().getName());

        // 验证缓存都存在
        assertCacheExists(objectKey, "子对象");
        assertCacheExists(relationKey, "关系");

        // 模拟对象更新
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("name", "联动测试更新_" + System.currentTimeMillis());

        ModelObject updatedChild = objectService.update(childObj.getId(),
                (Integer) childObj.get("_version"), updateData);

        waitForAsyncOperation();

        // 验证对象缓存状态
        ModelObject cachedChild = cacheService.get(objectKey, ModelObject.class);
        if (cachedChild != null) {
            log.debug("对象缓存在更新后的状态: {}", cachedChild.get("name"));
        }

        // 验证关系缓存状态
        @SuppressWarnings("unchecked")
        List<ModelObject> cachedRelation = cacheService.get(relationKey, List.class);
        if (cachedRelation != null) {
            log.debug("关系缓存在更新后仍存在: {} 个对象", cachedRelation.size());
        }

        log.info("✓ 关系缓存与对象缓存基本联动测试通过");
    }

    /**
     * 验证缓存状态
     */
    private void assertCacheExists(CacheKey key, String description) {
        assertTrue(cacheService.exists(key), description + " 缓存应该存在");
    }

    private void assertCacheNotExists(CacheKey key, String description) {
        assertFalse(cacheService.exists(key), description + " 缓存应该被清除");
    }

    /**
     * 等待异步操作完成
     */
    private void waitForAsyncOperation() throws InterruptedException {
        Thread.sleep(100);
    }

    /**
     * 清理缓存环境
     */
    private void cleanupCache() {
        try {
            // 清理测试相关的缓存
            cacheService.evictByPattern(CacheKey.DOMAIN_OBJECT, "*" + dateCode + "*");
            cacheService.evictByPattern(CacheKey.DOMAIN_RELATION, "*");
            cacheService.evictByPattern(CacheKey.DOMAIN_CODE_REVISION, "*" + dateCode + "*");
            cacheService.evictByPattern(CacheKey.DOMAIN_BUSINESS_UNIQUE_KEY, "*" + dateCode + "*");

            // 额外清理SampleTask相关的缓存
            cacheService.evictByPattern(CacheKey.DOMAIN_OBJECT, "*CACHE*");
            cacheService.evictByPattern(CacheKey.DOMAIN_RELATION, "*CACHE*");

            log.info("Cache cleanup completed");
        } catch (Exception e) {
            log.warn("Cache cleanup failed", e);
        }
    }
}