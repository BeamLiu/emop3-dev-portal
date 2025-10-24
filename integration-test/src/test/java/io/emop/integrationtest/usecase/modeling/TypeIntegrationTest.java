package io.emop.integrationtest.usecase.modeling;

import io.emop.model.common.KeyValueObject;
import io.emop.model.common.UserContext;
import io.emop.integrationtest.domain.TypeTestEntity;
import io.emop.integrationtest.util.TimerUtils;
import io.emop.service.S;
import io.emop.service.api.data.ObjectService;
import io.emop.service.api.domain.common.RevisionService;
import io.emop.model.common.Revisionable;
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

import static io.emop.integrationtest.util.Assertion.*;

/**
 * 类型集成测试 - 测试各种数据类型的完整CRUD操作
 */
@RequiredArgsConstructor
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TypeIntegrationTest {

    private static final String REV_ID = "TYPE_TEST_V1";
    private final String dateCode = String.valueOf(System.currentTimeMillis());

    @BeforeAll
    public void setup() {
        UserContext.setCurrentUser(new UserContext(100L, List.of("admin")));
    }

    @Test
    @Order(1)
    public void testFullTypeCRUD() {
        S.withStrongConsistency(this::testFullTypeCRUDImpl);
    }

    @Test
    @Order(2)
    public void testNullValueHandling() {
        S.withStrongConsistency(this::testNullValueHandlingImpl);
    }

    @Test
    @Order(3)
    public void testEmptyCollectionHandling() {
        S.withStrongConsistency(this::testEmptyCollectionHandlingImpl);
    }

    @Test
    @Order(4)
    public void testTypeConversionEdgeCases() {
        S.withStrongConsistency(this::testTypeConversionEdgeCasesImpl);
    }

    @Test
    @Order(5)
    public void testBatchOperations() {
        S.withStrongConsistency(this::testBatchOperationsImpl);
    }

    @Test
    @Order(6)
    public void testComplexQueries() {
        S.withStrongConsistency(this::testComplexQueriesImpl);
    }

    @Test
    @Order(7)
    public void testDataTypeCompatibility() {
        S.withStrongConsistency(this::testDataTypeCompatibilityImpl);
    }

    @Test
    @Order(8)
    public void performanceBenchmark() {
        S.withStrongConsistency(this::performanceBenchmarkImpl);
    }

    /**
     * 测试完整的类型CRUD操作
     */
    private void testFullTypeCRUDImpl() {
        log.info("--- 测试完整类型CRUD ---");

        TimerUtils.measureExecutionTime("完整类型CRUD测试", () -> {
            String code = "FULL_TYPE_" + dateCode;

            // 1. 创建包含所有类型的实体
            TypeTestEntity entity = TypeTestEntity.createFullTestInstance(code, REV_ID);
            log.info("创建实体: {}", entity);

            // 2. 保存
            ObjectService objectService = S.service(ObjectService.class);
            TypeTestEntity saved = objectService.save(entity);
            assertNotNull(saved);
            assertNotNull(saved.getId());
            log.info("保存成功，ID: {}", saved.getId());

            // 3. 查询验证
            TypeTestEntity found = objectService.findById(saved.getId());
            assertNotNull(found);

            // 验证基础类型
            assertEquals("测试字符串", found.getStringField());
            assertEquals(Integer.valueOf(12345), found.getIntegerField());
            assertEquals(Long.valueOf(123456789L), found.getLongField());
            assertEquals(Double.valueOf(123.456), found.getDoubleField());
            assertEquals(Float.valueOf(12.34f), found.getFloatField());
            assertTrue(found.getBooleanField());
            assertEquals(Short.valueOf((short) 123), found.getShortField());
            assertEquals(Byte.valueOf((byte) 12), found.getByteField());

            // 验证大数类型
            assertTrue(new BigDecimal("12345.6789").doubleValue() - found.getBigDecimalField().doubleValue() < 0.001);
            assertTrue(new BigInteger("123456789012345").doubleValue() - found.getBigIntegerField().doubleValue() < 0.001);

            // 验证日期时间类型
            assertNotNull(found.getSqlDateField());
            assertNotNull(found.getTimestampField());
            assertNotNull(found.getLocalDateField());
            assertNotNull(found.getLocalDateTimeField());
            assertNotNull(found.getInstantField());

            // 验证UUID
            assertNotNull(found.getUuidField());

            // 验证枚举
            assertEquals(TypeTestEntity.Status.ACTIVE, found.getStatusField());
            assertEquals(TypeTestEntity.Priority.HIGH, found.getPriorityField());

            // 验证集合类型
            assertNotNull(found.getStringListField());
            assertEquals(3, found.getStringListField().size());
            assertTrue(found.getStringListField().contains("item1"));

            assertNotNull(found.getStringSetField());
            assertEquals(3, found.getStringSetField().size());

            assertNotNull(found.getStringMapField());
            assertEquals("value1", found.getStringMapField().get("key1"));

            // 验证复杂对象
            assertNotNull(found.getMultiLangField());
            assertEquals("中文名称", found.getMultiLangField().get("zh_CN"));

            assertTrue(found.getKeepBinaryField() instanceof KeyValueObject);
            assertEquals("A", ((KeyValueObject) found.getKeepBinaryField()).get("prop1"));
            assertEquals(2l, ((KeyValueObject) found.getKeepBinaryField()).get("prop2"));

            assertNotNull(found.getNestedObjectField());
            assertEquals("测试嵌套", found.getNestedObjectField().getName());
            assertEquals(Integer.valueOf(42), found.getNestedObjectField().getValue());

            // 4. 更新操作
            found.setStringField("更新后的字符串");
            found.setIntegerField(54321);
            found.getStringListField().add("新增项");

            TypeTestEntity updated = objectService.save(found);
            assertEquals("更新后的字符串", updated.getStringField());
            assertEquals(Integer.valueOf(54321), updated.getIntegerField());
            assertEquals(4, updated.getStringListField().size());

            log.info("CRUD测试完成");
        });
    }

    /**
     * 测试NULL值处理
     */
    private void testNullValueHandlingImpl() {
        log.info("--- 测试NULL值处理 ---");

        TimerUtils.measureExecutionTime("NULL值处理测试", () -> {
            String code = "NULL_TEST_" + dateCode;

            // 创建包含NULL值的实体
            TypeTestEntity entity = TypeTestEntity.createNullTestInstance(code, REV_ID);

            ObjectService objectService = S.service(ObjectService.class);
            TypeTestEntity saved = objectService.save(entity);
            assertNotNull(saved);

            // 查询验证NULL值
            TypeTestEntity found = objectService.findById(saved.getId());
            assertNotNull(found);

            assertEquals("基础字符串", found.getStringField());
            assertEquals(Integer.valueOf(100), found.getIntegerField());
            assertFalse(found.getBooleanField());

            // 验证NULL字段
            assertNull(found.getNullableStringField());
            assertNull(found.getNullableIntegerField());
            assertNull(found.getNullableListField());
            assertNull(found.getNullableMapField());

            log.info("NULL值处理测试完成");
        });
    }

    /**
     * 测试空集合处理
     */
    private void testEmptyCollectionHandlingImpl() {
        log.info("--- 测试空集合处理 ---");

        TimerUtils.measureExecutionTime("空集合处理测试", () -> {
            String code = "EMPTY_COLLECTION_" + dateCode;

            TypeTestEntity entity = TypeTestEntity.createEmptyCollectionTestInstance(code, REV_ID);

            ObjectService objectService = S.service(ObjectService.class);
            TypeTestEntity saved = objectService.save(entity);
            assertNotNull(saved);

            // 查询验证空集合
            TypeTestEntity found = objectService.findById(saved.getId());
            assertNotNull(found);

            // 验证空集合
            assertNotNull(found.getStringListField());
            assertTrue(found.getStringListField().isEmpty());

            assertNotNull(found.getStringSetField());
            assertTrue(found.getStringSetField().isEmpty());

            assertNotNull(found.getStringMapField());
            assertTrue(found.getStringMapField().isEmpty());

            log.info("空集合处理测试完成");
        });
    }

    /**
     * 测试类型转换边界情况
     */
    private void testTypeConversionEdgeCasesImpl() {
        log.info("--- 测试类型转换边界情况 ---");

        TimerUtils.measureExecutionTime("类型转换边界测试", () -> {
            String code = "EDGE_CASE_" + dateCode;
            TypeTestEntity entity = new TypeTestEntity(code, REV_ID);
            entity.setName("边界测试");

            // 测试极值
            entity.setIntegerField(Integer.MAX_VALUE);
            entity.setLongField(Long.MAX_VALUE);
            entity.setDoubleField(Double.MAX_VALUE);
            entity.setFloatField(Float.MAX_VALUE);
            entity.setShortField(Short.MAX_VALUE);
            entity.setByteField(Byte.MAX_VALUE);

            // 测试大数 - PostgreSQL NUMERIC(38,18)约束
            // 最大值: 99999999999999999999.999999999999999999 (20位整数 + 18位小数)
            entity.setBigDecimalField(new BigDecimal("99999999999999999999.999999999999999999"));
            entity.setBigIntegerField(new BigInteger("99999999999999999999"));

            // 测试特殊日期
            entity.setSqlDateField(new java.sql.Date(0));
            entity.setTimestampField(new Timestamp(0));
            entity.setLocalDateField(LocalDate.of(1970, 1, 1));
            entity.setLocalDateTimeField(LocalDateTime.of(1970, 1, 1, 0, 0, 0));
            entity.setInstantField(Instant.EPOCH);

            // 测试特殊字符串
            entity.setStringField("包含特殊字符: 🌍 \n\t\r\"'\\");

            ObjectService objectService = S.service(ObjectService.class);
            TypeTestEntity saved = objectService.save(entity);
            assertNotNull(saved);

            // 验证保存和查询
            TypeTestEntity found = objectService.findById(saved.getId());
            assertNotNull(found);

            assertEquals(Integer.MAX_VALUE, found.getIntegerField().intValue());
            assertEquals(Long.MAX_VALUE, found.getLongField().longValue());
            assertTrue(found.getStringField().contains("🌍"));

            // 验证BigDecimal精度保持
            BigDecimal expectedDecimal = new BigDecimal("99999999999999999999.999999999999999999");
            assertTrue("BigDecimal precision should be maintained",
                    found.getBigDecimalField().compareTo(expectedDecimal) == 0);

            log.info("类型转换边界测试完成");
        });
    }

    /**
     * 测试批量操作
     */
    private void testBatchOperationsImpl() {
        log.info("--- 测试批量操作 ---");

        TimerUtils.measureExecutionTime("批量操作测试", () -> {
            List<TypeTestEntity> entities = new ArrayList<>();

            // 创建多个不同类型配置的实体
            for (int i = 1; i <= 10; i++) {
                String code = "BATCH_" + dateCode + "_" + i;
                TypeTestEntity entity = new TypeTestEntity(code, REV_ID);
                entity.setName("批量测试-" + i);
                entity.setIntegerField(i * 100);
                entity.setStringField("批量字符串-" + i);
                entity.setBooleanField(i % 2 == 0);

                // 添加一些集合数据
                entity.setStringListField(Arrays.asList("item" + i + "_1", "item" + i + "_2"));

                Map<String, String> map = new HashMap<>();
                map.put("key" + i, "value" + i);
                entity.setStringMapField(map);

                entities.add(entity);
            }

            // 批量保存
            ObjectService objectService = S.service(ObjectService.class);
            List<TypeTestEntity> saved = objectService.saveAll(entities);
            assertEquals(10, saved.size());

            // 验证所有实体都有ID
            for (TypeTestEntity entity : saved) {
                assertNotNull(entity.getId());
                assertNotNull(entity.getId());
            }

            log.info("批量保存了 {} 个实体", saved.size());

            // 批量查询验证
            for (TypeTestEntity entity : saved) {
                TypeTestEntity found = objectService.findById(entity.getId());
                assertNotNull(found);
                assertEquals(entity.getCode(), found.getCode());
                assertEquals(entity.getStringField(), found.getStringField());
                assertEquals(entity.getIntegerField(), found.getIntegerField());
                assertEquals(entity.getBooleanField(), found.getBooleanField());
            }

            log.info("批量操作测试完成");
        });
    }

    /**
     * 测试复杂查询
     */
    private void testComplexQueriesImpl() {
        log.info("--- 测试复杂查询 ---");

        TimerUtils.measureExecutionTime("复杂查询测试", () -> {
            // 创建用于查询测试的实体
            String code = "QUERY_TEST_" + dateCode;
            TypeTestEntity entity = TypeTestEntity.createFullTestInstance(code, REV_ID);

            ObjectService objectService = S.service(ObjectService.class);
            TypeTestEntity saved = objectService.save(entity);
            assertNotNull(saved);

            // 通过RevisionService查询
            RevisionService revisionService = S.service(RevisionService.class);
            TypeTestEntity found = revisionService.queryRevision(
                    new Revisionable.CriteriaByCodeAndRevId<>(
                            TypeTestEntity.class.getName(),
                            code,
                            REV_ID
                    )
            );

            assertNotNull(found);
            assertEquals(code, found.getCode());
            assertEquals(REV_ID, found.getRevId());
            assertEquals(saved.getId(), found.getId());

            // 验证复杂对象字段在查询后仍然正确
            assertNotNull(found.getStringListField());
            assertEquals(3, found.getStringListField().size());

            assertNotNull(found.getNestedObjectField());
            assertEquals("测试嵌套", found.getNestedObjectField().getName());

            log.info("复杂查询测试完成");
        });
    }

    /**
     * 测试数据类型兼容性
     */
    private void testDataTypeCompatibilityImpl() {
        log.info("--- 测试数据类型兼容性 ---");

        TimerUtils.measureExecutionTime("数据类型兼容性测试", () -> {
            String code = "COMPATIBILITY_" + dateCode;
            TypeTestEntity entity = new TypeTestEntity(code, REV_ID);
            entity.setName("兼容性测试");

            // 测试类型自动转换
            entity.setIntegerField(123);
            entity.setLongField(123L);
            entity.setDoubleField(123.0);
            entity.setStringField("123");
            entity.setBooleanField(true);

            ObjectService objectService = S.service(ObjectService.class);
            TypeTestEntity saved = objectService.save(entity);
            assertNotNull(saved);

            // 验证类型保持正确
            TypeTestEntity found = objectService.findById(saved.getId());
            assertNotNull(found);

            assertTrue(found.getIntegerField() instanceof Integer);
            assertTrue(found.getLongField() instanceof Long);
            assertTrue(found.getDoubleField() instanceof Double);
            assertTrue(found.getStringField() instanceof String);
            assertTrue(found.getBooleanField() instanceof Boolean);

            log.info("数据类型兼容性测试完成");
        });
    }

    /**
     * 性能基准测试
     */
    private void performanceBenchmarkImpl() {
        log.info("--- 性能基准测试 ---");

        final int testSize = 100;

        TimerUtils.measureExecutionTime("保存 " + testSize + " 个复杂类型实体", () -> {
            List<TypeTestEntity> entities = new ArrayList<>();

            for (int i = 0; i < testSize; i++) {
                String code = "PERF_" + dateCode + "_" + i;
                TypeTestEntity entity = TypeTestEntity.createFullTestInstance(code, REV_ID);
                entities.add(entity);
            }

            ObjectService objectService = S.service(ObjectService.class);
            List<TypeTestEntity> saved = objectService.saveAll(entities);
            assertEquals(testSize, saved.size());

            log.info("性能测试：批量保存 {} 个复杂实体完成", testSize);
        });

        TimerUtils.measureExecutionTime("查询 " + testSize + " 个复杂类型实体", () -> {
            ObjectService objectService = S.service(ObjectService.class);

            for (int i = 0; i < testSize; i++) {
                String code = "PERF_" + dateCode + "_" + i;
                RevisionService revisionService = S.service(RevisionService.class);
                TypeTestEntity found = revisionService.queryRevision(
                        new Revisionable.CriteriaByCodeAndRevId<>(
                                TypeTestEntity.class.getName(),
                                code,
                                REV_ID
                        )
                );
                assertNotNull(found);
            }

            log.info("性能测试：查询 {} 个复杂实体完成", testSize);
        });
    }
}