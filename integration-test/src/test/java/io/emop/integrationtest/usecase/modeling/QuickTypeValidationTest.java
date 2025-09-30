package io.emop.integrationtest.usecase.modeling;

import io.emop.integrationtest.domain.TypeTestEntity;
import io.emop.service.S;
import io.emop.service.api.data.ObjectService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 快速类型验证工具 - 用于快速验证平台类型系统的基本功能
 * 这个测试专注于验证各种类型是否能正确建模和持久化，而不是详细的边界测试
 */
@Slf4j
public class QuickTypeValidationTest {

    private static final String REV_ID = "QUICK_TEST_V1";

    @BeforeEach
    void setUp() {
        log.info("=== 快速类型验证开始 ===");
    }

    @Test
    void testQuickTypeValidation() {
        try {
            S.withStrongConsistency(() -> {
                validateBasicTypes();
                validateDateTimeTypes();
                validateCollectionTypes();
                validateComplexTypes();
                validateSpecialCases();
            });

            log.info("=== 快速类型验证成功完成 ===");
            log.info("✓ 所有基础类型都能正确建模和持久化");
            log.info("✓ 平台类型系统工作正常");

        } catch (Exception e) {
            log.error("❌ 快速类型验证失败", e);
            throw new RuntimeException("类型验证失败: " + e.getMessage(), e);
        }
    }

    @Test
    void testBasicTypes() {
        S.withStrongConsistency(this::validateBasicTypes);
    }

    @Test
    void testDateTimeTypes() {
        S.withStrongConsistency(this::validateDateTimeTypes);
    }

    @Test
    void testCollectionTypes() {
        S.withStrongConsistency(this::validateCollectionTypes);
    }

    @Test
    void testComplexTypes() {
        S.withStrongConsistency(this::validateComplexTypes);
    }

    @Test
    void testSpecialCases() {
        S.withStrongConsistency(this::validateSpecialCases);
    }

    /**
     * 验证基础数据类型
     */
    private void validateBasicTypes() {
        log.info("--- 验证基础数据类型 ---");

        String code = "BASIC_TYPES_" + System.currentTimeMillis();
        TypeTestEntity entity = new TypeTestEntity(code, REV_ID);
        entity.setName("基础类型验证");

        // 设置各种基础类型
        entity.setStringField("测试字符串");
        entity.setIntegerField(12345);
        entity.setLongField(123456789L);
        entity.setDoubleField(123.456);
        entity.setFloatField(12.34f);
        entity.setBooleanField(true);
        entity.setShortField((short) 123);
        entity.setByteField((byte) 12);
        entity.setBigDecimalField(new BigDecimal("12345.6789"));
        entity.setBigIntegerField(new BigInteger("123456789012345"));
        entity.setUuidField(UUID.randomUUID());
        entity.setStatusField(TypeTestEntity.Status.ACTIVE);
        entity.setPriorityField(TypeTestEntity.Priority.HIGH);
        entity.setBinaryField("测试二进制".getBytes());

        // 保存和验证
        ObjectService objectService = S.service(ObjectService.class);
        TypeTestEntity saved = objectService.save(entity);
        assertNotNull("保存失败", saved);
        assertNotNull("ID为空", saved.getId());

        TypeTestEntity found = objectService.findById(saved.getId());
        assertNotNull("查询失败", found);
        assertEquals("字符串不匹配", "测试字符串", found.getStringField());
        assertEquals("整数不匹配", Integer.valueOf(12345), found.getIntegerField());
        assertEquals("长整数不匹配", Long.valueOf(123456789L), found.getLongField());
        assertEquals("布尔值不匹配", Boolean.TRUE, found.getBooleanField());
        assertEquals("枚举不匹配", TypeTestEntity.Status.ACTIVE, found.getStatusField());
        assertNotNull("UUID为空", found.getUuidField());
        assertNotNull("二进制数据为空", found.getBinaryField());

        log.info("✓ 基础数据类型验证通过");
    }

    /**
     * 验证日期时间类型
     */
    private void validateDateTimeTypes() {
        log.info("--- 验证日期时间类型 ---");

        String code = "DATETIME_TYPES_" + System.currentTimeMillis();
        TypeTestEntity entity = new TypeTestEntity(code, REV_ID);
        entity.setName("日期时间类型验证");

        long currentTime = System.currentTimeMillis();
        entity.setUtilDateField(new Date(currentTime));
        entity.setSqlDateField(new java.sql.Date(currentTime));
        entity.setTimestampField(new Timestamp(currentTime));
        entity.setLocalDateField(LocalDate.now());
        entity.setLocalDateTimeField(LocalDateTime.now());
        entity.setInstantField(Instant.now());

        ObjectService objectService = S.service(ObjectService.class);
        TypeTestEntity saved = objectService.save(entity);
        assertNotNull("保存失败", saved);

        TypeTestEntity found = objectService.findById(saved.getId());
        assertNotNull("查询失败", found);
        assertNotNull("util.Date为空", found.getUtilDateField());
        assertNotNull("sql.Date为空", found.getSqlDateField());
        assertNotNull("Timestamp为空", found.getTimestampField());
        assertNotNull("LocalDate为空", found.getLocalDateField());
        assertNotNull("LocalDateTime为空", found.getLocalDateTimeField());
        assertNotNull("Instant为空", found.getInstantField());

        log.info("✓ 日期时间类型验证通过");
    }

    /**
     * 验证集合类型
     */
    private void validateCollectionTypes() {
        log.info("--- 验证集合类型 ---");

        String code = "COLLECTION_TYPES_" + System.currentTimeMillis();
        TypeTestEntity entity = new TypeTestEntity(code, REV_ID);
        entity.setName("集合类型验证");

        // 设置各种集合
        entity.setStringListField(Arrays.asList("item1", "item2", "item3"));
        entity.setStringSetField(new HashSet<>(Arrays.asList("set1", "set2", "set3")));

        Map<String, String> stringMap = new HashMap<>();
        stringMap.put("key1", "value1");
        stringMap.put("key2", "value2");
        entity.setStringMapField(stringMap);

        entity.setIntegerListField(Arrays.asList(1, 2, 3));
        entity.setLongSetField(new HashSet<>(Arrays.asList(100L, 200L, 300L)));

        Map<String, Double> doubleMap = new HashMap<>();
        doubleMap.put("weight", 12.5);
        doubleMap.put("height", 180.5);
        entity.setDoubleMapField(doubleMap);

        // 数组类型
        entity.setStringArrayField(new String[]{"array1", "array2", "array3"});
        entity.setIntegerArrayField(new Integer[]{10, 20, 30});
        entity.setPrimitiveIntArrayField(new int[]{1, 2, 3});
        entity.setPrimitiveDoubleArrayField(new double[]{1.1, 2.2, 3.3});

        ObjectService objectService = S.service(ObjectService.class);
        TypeTestEntity saved = objectService.save(entity);
        assertNotNull("保存失败", saved);

        TypeTestEntity found = objectService.findById(saved.getId());
        assertNotNull("查询失败", found);
        assertNotNull("字符串列表为空", found.getStringListField());
        assertEquals("列表大小不匹配", 3, found.getStringListField().size());
        assertTrue("列表内容不匹配", found.getStringListField().contains("item1"));

        assertNotNull("字符串集合为空", found.getStringSetField());
        assertEquals("集合大小不匹配", 3, found.getStringSetField().size());

        assertNotNull("字符串映射为空", found.getStringMapField());
        assertEquals("映射大小不匹配", 2, found.getStringMapField().size());
        assertEquals("映射值不匹配", "value1", found.getStringMapField().get("key1"));

        log.info("✓ 集合类型验证通过");
    }

    /**
     * 验证复杂类型
     */
    private void validateComplexTypes() {
        log.info("--- 验证复杂类型 ---");

        String code = "COMPLEX_TYPES_" + System.currentTimeMillis();
        TypeTestEntity entity = new TypeTestEntity(code, REV_ID);
        entity.setName("复杂类型验证");

        // 多语言对象
        io.emop.model.common.MultiLanguage multiLang = new io.emop.model.common.MultiLanguage();
        multiLang.get_properties().put("zh_CN", "中文名称");
        multiLang.get_properties().put("en_US", "English Name");
        entity.setMultiLangField(multiLang);

        // 嵌套对象
        TypeTestEntity.NestedObject nested = new TypeTestEntity.NestedObject("测试嵌套", 42, Arrays.asList("tag1", "tag2"));
        entity.setNestedObjectField(nested);

        // 嵌套对象列表
        List<TypeTestEntity.NestedObject> nestedList = Arrays.asList(
                new TypeTestEntity.NestedObject("nested1", 1, Arrays.asList("a", "b")),
                new TypeTestEntity.NestedObject("nested2", 2, Arrays.asList("c", "d"))
        );
        entity.setNestedObjectListField(nestedList);

        // 嵌套对象映射
        Map<String, TypeTestEntity.NestedObject> nestedMap = new HashMap<>();
        nestedMap.put("first", new TypeTestEntity.NestedObject("first", 10, Arrays.asList("x")));
        nestedMap.put("second", new TypeTestEntity.NestedObject("second", 20, Arrays.asList("y")));
        entity.setNestedObjectMapField(nestedMap);

        ObjectService objectService = S.service(ObjectService.class);
        TypeTestEntity saved = objectService.save(entity);
        assertNotNull("保存失败", saved);

        TypeTestEntity found = objectService.findById(saved.getId());
        assertNotNull("查询失败", found);
        assertNotNull("多语言对象为空", found.getMultiLangField());
        assertEquals("多语言内容不匹配", "中文名称", found.getMultiLangField().get("zh_CN"));

        assertNotNull("嵌套对象为空", found.getNestedObjectField());
        assertEquals("嵌套对象名称不匹配", "测试嵌套", found.getNestedObjectField().getName());
        assertEquals("嵌套对象值不匹配", Integer.valueOf(42), found.getNestedObjectField().getValue());

        assertNotNull("嵌套对象列表为空", found.getNestedObjectListField());
        assertEquals("嵌套对象列表大小不匹配", 2, found.getNestedObjectListField().size());

        assertNotNull("嵌套对象映射为空", found.getNestedObjectMapField());
        assertEquals("嵌套对象映射大小不匹配", 2, found.getNestedObjectMapField().size());

        log.info("✓ 复杂类型验证通过");
    }

    /**
     * 验证特殊情况
     */
    private void validateSpecialCases() {
        log.info("--- 验证特殊情况 ---");

        // NULL值测试
        String nullCode = "NULL_TEST_" + System.currentTimeMillis();
        TypeTestEntity nullEntity = new TypeTestEntity(nullCode, REV_ID);
        nullEntity.setName("NULL测试");
        nullEntity.setStringField("基础字符串");
        nullEntity.setIntegerField(100);
        // 其他字段保持NULL

        ObjectService objectService = S.service(ObjectService.class);
        TypeTestEntity nullSaved = objectService.save(nullEntity);
        assertNotNull("NULL测试保存失败", nullSaved);

        TypeTestEntity nullFound = objectService.findById(nullSaved.getId());
        assertNotNull("NULL测试查询失败", nullFound);
        assertEquals("基础字符串不匹配", "基础字符串", nullFound.getStringField());
        assertEquals("基础整数不匹配", Integer.valueOf(100), nullFound.getIntegerField());

        // 空集合测试
        String emptyCode = "EMPTY_TEST_" + System.currentTimeMillis();
        TypeTestEntity emptyEntity = new TypeTestEntity(emptyCode, REV_ID);
        emptyEntity.setName("空集合测试");
        emptyEntity.setStringField("空集合测试");
        emptyEntity.setStringListField(new ArrayList<>());
        emptyEntity.setStringSetField(new HashSet<>());
        emptyEntity.setStringMapField(new HashMap<>());

        TypeTestEntity emptySaved = objectService.save(emptyEntity);
        assertNotNull("空集合测试保存失败", emptySaved);

        TypeTestEntity emptyFound = objectService.findById(emptySaved.getId());
        assertNotNull("空集合测试查询失败", emptyFound);
        assertNotNull("空列表为null", emptyFound.getStringListField());
        assertTrue("列表不为空", emptyFound.getStringListField().isEmpty());

        // Unicode字符测试
        String unicodeCode = "UNICODE_TEST_" + System.currentTimeMillis();
        TypeTestEntity unicodeEntity = new TypeTestEntity(unicodeCode, REV_ID);
        unicodeEntity.setName("Unicode测试 🌍");
        unicodeEntity.setStringField("包含Unicode字符: 🚀💻🎉 中文 العربية Русский");

        TypeTestEntity unicodeSaved = objectService.save(unicodeEntity);
        assertNotNull("Unicode测试保存失败", unicodeSaved);

        TypeTestEntity unicodeFound = objectService.findById(unicodeSaved.getId());
        assertNotNull("Unicode测试查询失败", unicodeFound);
        assertTrue("Unicode字符丢失", unicodeFound.getStringField().contains("🚀"));
        assertTrue("中文字符丢失", unicodeFound.getStringField().contains("中文"));

        log.info("✓ 特殊情况验证通过");
    }

    // 简单的断言方法
    private void assertNotNull(String message, Object obj) {
        if (obj == null) {
            throw new AssertionError(message);
        }
    }

    private void assertEquals(String message, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message + " - 期望: " + expected + ", 实际: " + actual);
        }
    }

    private void assertTrue(String message, boolean condition) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}