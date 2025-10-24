package io.emop.integrationtest.usecase.modeling;

import io.emop.model.common.UserContext;
import io.emop.integrationtest.domain.TypeTestEntity;
import io.emop.service.S;
import io.emop.service.api.data.ObjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.emop.integrationtest.util.Assertion.*;

/**
 * 边界情况和特殊场景的类型测试
 */
@RequiredArgsConstructor
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EdgeCaseTypeTest {

    private static final String REV_ID = "EDGE_TEST_V1";
    private final String dateCode = String.valueOf(System.currentTimeMillis());

    @BeforeAll
    public void setup() {
        UserContext.setCurrentUser(new UserContext(100L, List.of("admin")));
    }

    @Test
    @Order(1)
    public void testExtremeValues() {
        S.withStrongConsistency(this::testExtremeValuesImpl);
    }

    @Test
    @Order(2)
    public void testSpecialCharactersAndUnicode() {
        S.withStrongConsistency(this::testSpecialCharactersAndUnicodeImpl);
    }

    @Test
    @Order(3)
    public void testDateTimeBoundaries() {
        S.withStrongConsistency(this::testDateTimeBoundariesImpl);
    }

    @Test
    @Order(4)
    public void testLargeCollections() {
        S.withStrongConsistency(this::testLargeCollectionsImpl);
    }

    @Test
    @Order(5)
    public void testDeepNestedObjects() {
        S.withStrongConsistency(this::testDeepNestedObjectsImpl);
    }

    @Test
    @Order(6)
    public void testConcurrentTypeOperations() {
        S.withStrongConsistency(this::testConcurrentTypeOperationsImpl);
    }

    @Test
    @Order(7)
    public void testSerializationEdgeCases() {
        S.withStrongConsistency(this::testSerializationEdgeCasesImpl);
    }

    @Test
    @Order(8)
    public void testTypeConversionLimits() {
        S.withStrongConsistency(this::testTypeConversionLimitsImpl);
    }

    /**
     * 测试极值数据
     */
    private void testExtremeValuesImpl() {
        log.info("--- 测试极值数据 ---");

        try {
            String code = "EXTREME_VALUES_" + dateCode;
            TypeTestEntity entity = new TypeTestEntity(code, REV_ID);
            entity.setName("极值测试");

            // 测试数值极值
            entity.setIntegerField(Integer.MAX_VALUE);
            entity.setLongField(Long.MAX_VALUE);
            entity.setDoubleField(Double.MAX_VALUE);
            entity.setFloatField(Float.MAX_VALUE);
            entity.setShortField(Short.MAX_VALUE);
            entity.setByteField(Byte.MAX_VALUE);

            // 测试负极值
            TypeTestEntity negativeEntity = new TypeTestEntity("NEGATIVE_" + code, REV_ID);
            negativeEntity.setName("负极值测试");
            negativeEntity.setIntegerField(Integer.MIN_VALUE);
            negativeEntity.setLongField(Long.MIN_VALUE);
            negativeEntity.setDoubleField(Double.MIN_VALUE);
            negativeEntity.setFloatField(Float.MIN_VALUE);
            negativeEntity.setShortField(Short.MIN_VALUE);
            negativeEntity.setByteField(Byte.MIN_VALUE);

            // 测试特殊浮点值
            TypeTestEntity specialFloatEntity = new TypeTestEntity("SPECIAL_FLOAT_" + code, REV_ID);
            specialFloatEntity.setName("特殊浮点测试");
            specialFloatEntity.setDoubleField(Double.POSITIVE_INFINITY);
            specialFloatEntity.setFloatField(Float.NEGATIVE_INFINITY);

            // 测试超大BigDecimal和BigInteger
            entity.setBigDecimalField(new BigDecimal("123456789012345678.90123"));
            entity.setBigIntegerField(new BigInteger("123456789012345"));

            ObjectService objectService = S.service(ObjectService.class);
            // 保存并验证
            TypeTestEntity saved = objectService.save(entity);
            assertNotNull(saved);

            TypeTestEntity negativeSaved = objectService.save(negativeEntity);
            assertNotNull(negativeSaved);

            // 验证极值保存正确
            TypeTestEntity found = objectService.findById(saved.getId());
            assertEquals(Integer.MAX_VALUE, found.getIntegerField().intValue());
            assertEquals(Long.MAX_VALUE, found.getLongField().longValue());

            TypeTestEntity negativeFound = objectService.findById(negativeSaved.getId());
            assertEquals(Integer.MIN_VALUE, negativeFound.getIntegerField().intValue());
            assertEquals(Long.MIN_VALUE, negativeFound.getLongField().longValue());

            log.info("极值数据测试完成");

        } catch (Exception e) {
            log.error("极值测试失败", e);
            throw new RuntimeException("极值测试失败", e);
        }
    }

    /**
     * 测试特殊字符和Unicode
     */
    private void testSpecialCharactersAndUnicodeImpl() {
        log.info("--- 测试特殊字符和Unicode ---");

        try {
            String code = "UNICODE_" + dateCode;
            TypeTestEntity entity = new TypeTestEntity(code, REV_ID);
            entity.setName("Unicode测试");

            // 测试各种特殊字符
            String specialChars = "特殊字符测试: " +
                    "🌍🚀💻🎉😀😢🤔💡🔥⭐ " + // Emoji
                    "中文 العربية Русский 日本語 한국어 " + // 多语言
                    "数学符号: ∑∏∫√∞±≤≥≠ " + // 数学符号
                    "货币: $€£¥₹₽ " + // 货币符号
                    "引号: \"'`''«»‹› " + // 引号
                    "换行\n制表\t回车\r " + // 控制字符
                    "特殊符号: @#$%^&*()_+-={}[]|\\:;\"'<>?,./ " + // 特殊符号
                    "零宽字符:\u200B\u200C\u200D\uFEFF"; // 零宽字符

            entity.setStringField(specialChars);

            // 测试包含特殊字符的集合
            List<String> unicodeList = Arrays.asList(
                    "🌍Hello World",
                    "中文测试",
                    "العربية",
                    "Русский текст",
                    "控制字符\n\t\r",
                    "JSON特殊字符: {}[]\"'\\/"
            );
            entity.setStringListField(unicodeList);

            // 测试包含Unicode的Map
            Map<String, String> unicodeMap = new HashMap<>();
            unicodeMap.put("🔑key", "🔓value");
            unicodeMap.put("中文键", "中文值");
            unicodeMap.put("control\nkey", "control\nvalue");
            entity.setStringMapField(unicodeMap);

            ObjectService objectService = S.service(ObjectService.class);
            TypeTestEntity saved = objectService.save(entity);
            assertNotNull(saved);

            // 验证Unicode保存正确
            TypeTestEntity found = objectService.findById(saved.getId());
            assertTrue(found.getStringField().contains("🌍"));
            assertTrue(found.getStringField().contains("中文"));
            assertTrue(found.getStringField().contains("العربية"));
            assertTrue(found.getStringListField().contains("🌍Hello World"));
            assertEquals("🔓value", found.getStringMapField().get("🔑key"));
            assertEquals("中文值", found.getStringMapField().get("中文键"));

            log.info("特殊字符和Unicode测试完成");

        } catch (Exception e) {
            log.error("Unicode测试失败", e);
            throw new RuntimeException("Unicode测试失败", e);
        }
    }

    /**
     * 测试日期时间边界值
     */
    private void testDateTimeBoundariesImpl() {
        log.info("--- 测试日期时间边界值 ---");

        try {
            String code = "DATETIME_BOUNDARY_" + dateCode;
            TypeTestEntity entity = new TypeTestEntity(code, REV_ID);
            entity.setName("日期时间边界测试");

            // 测试Unix时间戳边界
            entity.setSqlDateField(new java.sql.Date(0));
            entity.setTimestampField(new Timestamp(0));

            // 测试LocalDate边界
            entity.setLocalDateField(LocalDate.of(1900, 1, 1)); // 早期日期
            entity.setLocalDateTimeField(LocalDateTime.of(2099, 12, 31, 23, 59, 59)); // 未来日期
            entity.setInstantField(Instant.EPOCH);

            // 测试极端未来日期
            TypeTestEntity futureEntity = new TypeTestEntity("FUTURE_" + code, REV_ID);
            futureEntity.setName("未来日期测试");
            futureEntity.setLocalDateField(LocalDate.of(9999, 12, 31));
            futureEntity.setLocalDateTimeField(LocalDateTime.of(9999, 12, 31, 23, 59, 59));

            ObjectService objectService = S.service(ObjectService.class);

            TypeTestEntity saved = objectService.save(entity);
            TypeTestEntity futureSaved = objectService.save(futureEntity);

            assertNotNull(saved);
            assertNotNull(futureSaved);

            // 验证日期时间保存正确
            TypeTestEntity found = objectService.findById(saved.getId());
            assertEquals(LocalDate.of(1900, 1, 1), found.getLocalDateField());
            assertEquals(Instant.EPOCH, found.getInstantField());

            TypeTestEntity futureFound = objectService.findById(futureSaved.getId());
            assertEquals(LocalDate.of(9999, 12, 31), futureFound.getLocalDateField());

            log.info("日期时间边界值测试完成");

        } catch (Exception e) {
            log.error("日期时间边界测试失败", e);
            throw new RuntimeException("日期时间边界测试失败", e);
        }
    }

    /**
     * 测试大集合数据
     */
    private void testLargeCollectionsImpl() {
        log.info("--- 测试大集合数据 ---");

        try {
            String code = "LARGE_COLLECTION_" + dateCode;
            TypeTestEntity entity = new TypeTestEntity(code, REV_ID);
            entity.setName("大集合测试");

            // 创建大List（1000个元素）
            List<String> largeList = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                largeList.add("Large list item " + i + " with some content to make it longer");
            }
            entity.setStringListField(largeList);

            // 创建大Set（500个元素）
            Set<String> largeSet = new HashSet<>();
            for (int i = 0; i < 500; i++) {
                largeSet.add("Large set item " + i);
            }
            entity.setStringSetField(largeSet);

            // 创建大Map（300个键值对）
            Map<String, String> largeMap = new HashMap<>();
            for (int i = 0; i < 300; i++) {
                largeMap.put("key" + i, "This is a longer value for key " + i + " with more content");
            }
            entity.setStringMapField(largeMap);

            // 创建大的嵌套对象列表
            List<TypeTestEntity.NestedObject> largeNestedList = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                List<String> tags = Arrays.asList("tag" + i + "_1", "tag" + i + "_2", "tag" + i + "_3");
                largeNestedList.add(new TypeTestEntity.NestedObject("nested" + i, i, tags));
            }
            entity.setNestedObjectListField(largeNestedList);

            ObjectService objectService = S.service(ObjectService.class);
            TypeTestEntity saved = objectService.save(entity);
            assertNotNull(saved);

            // 验证大集合保存正确
            TypeTestEntity found = objectService.findById(saved.getId());
            assertEquals(1000, found.getStringListField().size());
            assertEquals(500, found.getStringSetField().size());
            assertEquals(300, found.getStringMapField().size());
            assertEquals(100, found.getNestedObjectListField().size());

            // 验证内容正确性
            assertTrue(found.getStringListField().contains("Large list item 999 with some content to make it longer"));
            assertTrue(found.getStringMapField().containsKey("key299"));
            assertEquals("nested99", found.getNestedObjectListField().get(99).getName());

            log.info("大集合数据测试完成");

        } catch (Exception e) {
            log.error("大集合测试失败", e);
            throw new RuntimeException("大集合测试失败", e);
        }
    }

    /**
     * 测试深层嵌套对象
     */
    private void testDeepNestedObjectsImpl() {
        log.info("--- 测试深层嵌套对象 ---");

        try {
            String code = "DEEP_NESTED_" + dateCode;
            TypeTestEntity entity = new TypeTestEntity(code, REV_ID);
            entity.setName("深层嵌套测试");

            // 创建深层嵌套的Map结构
            Map<String, Object> deepMap = new HashMap<>();
            Map<String, Object> level1 = new HashMap<>();
            Map<String, Object> level2 = new HashMap<>();
            Map<String, Object> level3 = new HashMap<>();

            level3.put("deepest", "这是最深层的数据");
            level3.put("number", 42);
            level3.put("list", Arrays.asList("deep1", "deep2", "deep3"));

            level2.put("level3", level3);
            level2.put("level2_data", "第二层数据");

            level1.put("level2", level2);
            level1.put("level1_data", "第一层数据");

            deepMap.put("level1", level1);
            deepMap.put("root_data", "根数据");

            // 虽然实体没有直接的深层嵌套字段，我们可以通过_properties来测试
            // 或者创建包含深层嵌套的NestedObject
            TypeTestEntity.NestedObject deepNested = new TypeTestEntity.NestedObject();
            deepNested.setName("深层嵌套对象");
            deepNested.setValue(100);

            // 创建多层嵌套的tags
            List<String> deepTags = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                StringBuilder tagBuilder = new StringBuilder();
                for (int j = 0; j <= i; j++) {
                    tagBuilder.append("level").append(j).append(".");
                }
                tagBuilder.append("tag").append(i);
                deepTags.add(tagBuilder.toString());
            }
            deepNested.setTags(deepTags);

            entity.setNestedObjectField(deepNested);

            ObjectService objectService = S.service(ObjectService.class);
            TypeTestEntity saved = objectService.save(entity);
            assertNotNull(saved);

            // 验证深层嵌套保存正确
            TypeTestEntity found = objectService.findById(saved.getId());
            assertNotNull(found.getNestedObjectField());
            assertEquals("深层嵌套对象", found.getNestedObjectField().getName());
            assertEquals(10, found.getNestedObjectField().getTags().size());
            assertTrue(found.getNestedObjectField().getTags().contains("level0.level1.level2.level3.level4.level5.level6.level7.level8.level9.tag9"));

            log.info("深层嵌套对象测试完成");

        } catch (Exception e) {
            log.error("深层嵌套测试失败", e);
            throw new RuntimeException("深层嵌套测试失败", e);
        }
    }

    /**
     * 测试并发类型操作
     */
    private void testConcurrentTypeOperationsImpl() {
        log.info("--- 测试并发类型操作 ---");

        try {
            ExecutorService executor = Executors.newFixedThreadPool(5);
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int i = 0; i < 10; i++) {
                final int threadIndex = i;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    UserContext.runAsSystem(() -> {
                        try {
                            String code = "CONCURRENT_" + dateCode + "_" + threadIndex;
                            TypeTestEntity entity = TypeTestEntity.createFullTestInstance(code, REV_ID);
                            entity.setIntegerField(threadIndex * 1000);

                            ObjectService objectService = S.service(ObjectService.class);
                            TypeTestEntity saved = objectService.save(entity);
                            assertNotNull(saved);

                            // 立即查询验证
                            TypeTestEntity found = objectService.findById(saved.getId());
                            assertNotNull(found);
                            assertEquals(threadIndex * 1000, found.getIntegerField().intValue());

                            log.debug("并发线程 {} 完成", threadIndex);

                        } catch (Exception e) {
                            log.error("并发线程 " + threadIndex + " 失败", e);
                            throw new RuntimeException("并发测试失败", e);
                        }
                    });
                }, executor);

                futures.add(future);
            }

            // 等待所有线程完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);

            log.info("并发类型操作测试完成");

        } catch (Exception e) {
            log.error("并发测试失败", e);
            throw new RuntimeException("并发测试失败", e);
        }
    }

    /**
     * 测试序列化边界情况
     */
    private void testSerializationEdgeCasesImpl() {
        log.info("--- 测试序列化边界情况 ---");

        try {
            String code = "SERIALIZATION_" + dateCode;
            TypeTestEntity entity = new TypeTestEntity(code, REV_ID);
            entity.setName("序列化边界测试");

            // 测试包含特殊JSON字符的字符串
            entity.setStringField("JSON特殊字符: {\"key\": \"value\", \"array\": [1, 2, 3], \"escaped\": \"quote\\\"inside\"}");

            // 测试包含循环引用可能性的结构（通过字符串模拟）
            List<String> selfReferenceList = new ArrayList<>();
            selfReferenceList.add("self");
            selfReferenceList.add("reference");
            selfReferenceList.add(selfReferenceList.toString()); // 这会创建一个字符串表示，避免真正的循环引用
            entity.setStringListField(selfReferenceList);

            // 测试非常长的字符串
            StringBuilder longString = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                longString.append("这是一个非常长的字符串，用于测试序列化性能和正确性。编号：").append(i).append(" ");
            }

            TypeTestEntity.NestedObject longStringObject = new TypeTestEntity.NestedObject();
            longStringObject.setName(longString.substring(0, Math.min(1000, longString.length()))); // 限制长度避免过大
            longStringObject.setValue(99999);
            longStringObject.setTags(Arrays.asList("long", "string", "test"));
            entity.setNestedObjectField(longStringObject);

            ObjectService objectService = S.service(ObjectService.class);
            TypeTestEntity saved = objectService.save(entity);
            assertNotNull(saved);

            // 验证序列化边界情况
            TypeTestEntity found = objectService.findById(saved.getId());
            assertTrue(found.getStringField().contains("JSON特殊字符"));
            assertTrue(found.getStringField().contains("quote\\\"inside"));
            assertEquals(3, found.getStringListField().size());
            assertTrue(found.getNestedObjectField().getName().contains("这是一个非常长的字符串"));

            log.info("序列化边界情况测试完成");

        } catch (Exception e) {
            log.error("序列化边界测试失败", e);
            throw new RuntimeException("序列化边界测试失败", e);
        }
    }

    /**
     * 测试类型转换限制
     */
    private void testTypeConversionLimitsImpl() {
        log.info("--- 测试类型转换限制 ---");

        try {
            String code = "TYPE_CONVERSION_" + dateCode;
            TypeTestEntity entity = new TypeTestEntity(code, REV_ID);
            entity.setName("类型转换限制测试");

            // 测试数值精度限制
            entity.setDoubleField(1.7976931348623157E308); // 接近Double.MAX_VALUE
            entity.setFloatField(3.4028235E38f); // 接近Float.MAX_VALUE

            // 测试BigDecimal精度
            entity.setBigDecimalField(new BigDecimal("1.23456789012345678901234567890123456789"));

            // 测试时间戳精度
            entity.setTimestampField(new Timestamp(System.currentTimeMillis()));
            entity.setInstantField(Instant.now());

            ObjectService objectService = S.service(ObjectService.class);
            TypeTestEntity saved = objectService.save(entity);
            assertNotNull(saved);

            // 验证精度保持
            TypeTestEntity found = objectService.findById(saved.getId());
            assertNotNull(found.getDoubleField());
            assertNotNull(found.getBigDecimalField());

            // 验证时间戳精度（毫秒级）
            long originalMillis = entity.getTimestampField().getTime();
            long foundMillis = found.getTimestampField().getTime();
            assertEquals(originalMillis, foundMillis);

            log.info("类型转换限制测试完成");

        } catch (Exception e) {
            log.error("类型转换限制测试失败", e);
            throw new RuntimeException("类型转换限制测试失败", e);
        }
    }
}