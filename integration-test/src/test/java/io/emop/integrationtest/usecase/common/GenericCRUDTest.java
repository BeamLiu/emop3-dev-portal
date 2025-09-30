package io.emop.integrationtest.usecase.common;

import io.emop.model.common.*;
import io.emop.integrationtest.util.TimerUtils;
import io.emop.service.S;
import io.emop.service.api.data.ObjectService;
import io.emop.service.api.domain.common.RevisionService;
import io.emop.service.api.other.MockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.emop.integrationtest.util.Assertion.*;

/**
 * 基本的增删改查
 */
@RequiredArgsConstructor
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GenericCRUDTest {

    private static final String revId = "35";
    private static final String dateCode = String.valueOf(System.currentTimeMillis());
    private static final int batchSize = 1000;

    @BeforeAll
    public void setup() {
        UserContext.setCurrentUser(new UserContext(100L, List.of("admin")));
    }

    @Test
    @Order(1)
    public void testSave() {
        S.withStrongConsistency(this::save);
    }

    @Test
    @Order(2)
    public void testBatchSaveWithLoopInOneTransaction() {
        S.withStrongConsistency(this::batchSaveWithLoopInOneTransaction);
    }

    @Test
    @Order(3)
    public void testBatchSave() {
        S.withStrongConsistency(this::batchSave);
    }

    @Test
    @Order(4)
    public void testQuery() {
        S.withStrongConsistency(this::query);
    }

    @Test
    @Order(5)
    public void testComplexObjectSerialization() {
        S.withStrongConsistency(() -> {
            UserContext.runAsSystem(this::testComplexObjectSerializationInternal);
        });
    }

    @Test
    @Order(6)
    public void testSimpleTypesConversion() {
        S.withStrongConsistency(() -> {
            UserContext.runAsSystem(this::testSimpleTypesConversionInteral);
        });
    }

    @Test
    @Order(7)
    public void testDateTimeTypesConversion() {
        S.withStrongConsistency(() -> {
            UserContext.runAsSystem(this::testDateTimeTypesConversionInternal);
        });
    }

    @Test
    @Order(8)
    public void testNumberTypesConversion() {
        S.withStrongConsistency(() -> {
            UserContext.runAsSystem(this::testNumberTypesConversionInternal);
        });
    }

    @Test
    @Order(9)
    public void testCollectionTypesConversion() {
        S.withStrongConsistency(() -> {
            UserContext.runAsSystem(this::testCollectionTypesConversionInternal);
        });
    }

    private void testComplexObjectSerializationInternal() {
        TimerUtils.measureExecutionTime("测试 ItemRevision 带 CheckoutInfo 保存和查询", () -> {
            // 创建 ItemRevision
            String itemCode = "CHECKOUT-TEST-" + dateCode;
            ItemRevision itemRevision = ItemRevision.newModel(itemCode, revId);
            itemRevision.setName("测试签出的Item-" + dateCode);

            // 创建 CheckoutInfo
            String comment = "测试签出-" + dateCode;
            int expiryMinutes = 60;
            CheckoutInfo originalCheckoutInfo = CheckoutInfo.createCheckout(comment, expiryMinutes);

            log.info("创建的 CheckoutInfo: {}", originalCheckoutInfo);

            // 将 CheckoutInfo 设置到 ItemRevision 中
            itemRevision.set("_checkout", originalCheckoutInfo);

            // 保存 ItemRevision
            ItemRevision savedRevision = S.service(ObjectService.class).save(itemRevision);

            log.info("保存后的 ItemRevision: {}", savedRevision);
            assertNotNull(savedRevision);
            assertEquals(CheckoutInfo.class, savedRevision.get("_checkout").getClass());

            // 查询回来验证
            ItemRevision queriedRevision = S.service(RevisionService.class).queryRevision(
                    new Revisionable.CriteriaByCodeAndRevId<>(ItemRevision.class.getName(), itemCode, revId)
            );

            assertNotNull(queriedRevision);
            assertEquals(itemCode, queriedRevision.getCode());
            assertEquals(revId, queriedRevision.getRevId());

            assertNotNull(queriedRevision.get_properties().get("_checkout"));
            assertEquals(LinkedHashMap.class, queriedRevision.get_properties().get("_checkout").getClass());

            // 获取 CheckoutInfo - 这里会触发类型转换
            CheckoutInfo retrievedCheckoutInfo = queriedRevision.get("_checkout", CheckoutInfo.class);

            // 验证 CheckoutInfo 数据
            assertNotNull(retrievedCheckoutInfo);
            assertNotNull(retrievedCheckoutInfo.getCheckedoutByUserId());
            assertNotNull(retrievedCheckoutInfo.getCheckoutDate());
            assertEquals(comment, retrievedCheckoutInfo.getComment());
            assertEquals(expiryMinutes, retrievedCheckoutInfo.getExpiryMinutes());

            // _properties已经更新
            assertEquals(CheckoutInfo.class, queriedRevision.get_properties().get("_checkout").getClass());

            // 验证时间戳是否合理（应该是最近创建的）
            long timeDiff = System.currentTimeMillis() - retrievedCheckoutInfo.getCheckoutDate().getTime();
            assertTrue("签出时间应该在合理范围内", timeDiff >= 0 && timeDiff < 10000); // 10秒内

            // 验证用户信息一致性
            assertEquals(originalCheckoutInfo.getCheckedoutByUserId(), retrievedCheckoutInfo.getCheckedoutByUserId());

            log.info("ItemRevision 带 CheckoutInfo 测试通过 - 原始对象: {}, 查询对象: {}",
                    originalCheckoutInfo, retrievedCheckoutInfo);
        });
    }

    private void testSimpleTypesConversionInteral() {
        TimerUtils.measureExecutionTime("测试简单类型自动转换", () -> {
            String itemCode = "SIMPLE-TYPES-" + dateCode;
            ItemRevision itemRevision = ItemRevision.newModel(itemCode, revId);
            itemRevision.setName("测试简单类型转换-" + dateCode);

            // 设置各种简单类型
            itemRevision.set("stringValue", "Hello World");
            itemRevision.set("intValue", 42);
            itemRevision.set("longValue", 123456789L);
            itemRevision.set("doubleValue", 3.14159);
            itemRevision.set("floatValue", 2.718f);
            itemRevision.set("booleanValue", true);
            itemRevision.set("byteValue", (byte) 255);
            itemRevision.set("shortValue", (short) 32767);

            log.info("设置的原始值类型: string={}, int={}, long={}, double={}, float={}, boolean={}, byte={}, short={}",
                    itemRevision.get("stringValue").getClass().getSimpleName(),
                    itemRevision.get("intValue").getClass().getSimpleName(),
                    itemRevision.get("longValue").getClass().getSimpleName(),
                    itemRevision.get("doubleValue").getClass().getSimpleName(),
                    itemRevision.get("floatValue").getClass().getSimpleName(),
                    itemRevision.get("booleanValue").getClass().getSimpleName(),
                    itemRevision.get("byteValue").getClass().getSimpleName(),
                    itemRevision.get("shortValue").getClass().getSimpleName()
            );

            // 保存并查询
            S.service(ObjectService.class).save(itemRevision);
            ItemRevision queriedRevision = S.service(RevisionService.class).queryRevision(
                    new Revisionable.CriteriaByCodeAndRevId<>(ItemRevision.class.getName(), itemCode, revId)
            );

            // 验证查询回来后的原始类型
            log.info("查询回来的原始类型: string={}, int={}, long={}, double={}, float={}, boolean={}, byte={}, short={}",
                    queriedRevision.get_properties().get("stringValue").getClass().getSimpleName(),
                    queriedRevision.get_properties().get("intValue").getClass().getSimpleName(),
                    queriedRevision.get_properties().get("longValue").getClass().getSimpleName(),
                    queriedRevision.get_properties().get("doubleValue").getClass().getSimpleName(),
                    queriedRevision.get_properties().get("floatValue").getClass().getSimpleName(),
                    queriedRevision.get_properties().get("booleanValue").getClass().getSimpleName(),
                    queriedRevision.get_properties().get("byteValue").getClass().getSimpleName(),
                    queriedRevision.get_properties().get("shortValue").getClass().getSimpleName()
            );

            // 测试类型转换
            String stringValue = queriedRevision.get("stringValue", String.class);
            Integer intValue = queriedRevision.get("intValue", Integer.class);
            Long longValue = queriedRevision.get("longValue", Long.class);
            Double doubleValue = queriedRevision.get("doubleValue", Double.class);
            Float floatValue = queriedRevision.get("floatValue", Float.class);
            Boolean booleanValue = queriedRevision.get("booleanValue", Boolean.class);
            Byte byteValue = queriedRevision.get("byteValue", Byte.class);
            Short shortValue = queriedRevision.get("shortValue", Short.class);

            // 验证值的正确性
            assertEquals("Hello World", stringValue);
            assertEquals(Integer.valueOf(42), intValue);
            assertEquals(Long.valueOf(123456789L), longValue);
            assertEquals(Double.valueOf(3.14159), doubleValue);
            assertEquals(Float.valueOf(2.718f), floatValue);
            assertEquals(Boolean.TRUE, booleanValue);
            assertEquals(Byte.valueOf((byte) 255), byteValue);
            assertEquals(Short.valueOf((short) 32767), shortValue);

            // 测试跨类型转换
            String intAsString = queriedRevision.get("intValue", String.class);
            assertException(() -> {
                queriedRevision.get("stringValue", Integer.class);
            });

            assertEquals("42", intAsString);
            // stringAsInt 转换可能会失败，这是正常的

            log.info("简单类型转换测试通过");
        });
    }

    private void testDateTimeTypesConversionInternal() {
        TimerUtils.measureExecutionTime("测试日期时间类型转换", () -> {
            String itemCode = "DATETIME-TYPES-" + dateCode;
            ItemRevision itemRevision = ItemRevision.newModel(itemCode, revId);
            itemRevision.setName("测试日期时间类型转换-" + dateCode);

            // 设置各种日期时间类型
            Date now = new Date();
            Timestamp timestamp = new Timestamp(System.currentTimeMillis());

            itemRevision.set("dateValue", now);
            itemRevision.set("timestampValue", timestamp);
            itemRevision.set("longTimestamp", System.currentTimeMillis());
            itemRevision.set("isoDateString", "2024-07-11T10:30:00");

            log.info("设置的日期时间类型: date={}, timestamp={}, long={}, string={}",
                    itemRevision.get("dateValue").getClass().getSimpleName(),
                    itemRevision.get("timestampValue").getClass().getSimpleName(),
                    itemRevision.get("longTimestamp").getClass().getSimpleName(),
                    itemRevision.get("isoDateString").getClass().getSimpleName()
            );

            // 保存并查询
            S.service(ObjectService.class).save(itemRevision);
            ItemRevision queriedRevision = S.service(RevisionService.class).queryRevision(
                    new Revisionable.CriteriaByCodeAndRevId<>(ItemRevision.class.getName(), itemCode, revId)
            );

            // 验证查询回来后的原始类型
            log.info("查询回来的日期时间类型: date={}, timestamp={}, long={}, string={}",
                    queriedRevision.get_properties().get("dateValue").getClass().getSimpleName(),
                    queriedRevision.get_properties().get("timestampValue").getClass().getSimpleName(),
                    queriedRevision.get_properties().get("longTimestamp").getClass().getSimpleName(),
                    queriedRevision.get_properties().get("isoDateString").getClass().getSimpleName()
            );

            // 测试日期时间类型转换
            Date retrievedDate = queriedRevision.get("dateValue", Date.class);
            Timestamp retrievedTimestamp = queriedRevision.get("timestampValue", Timestamp.class);

            // 测试跨类型转换
            Date longAsDate = queriedRevision.get("longTimestamp", Date.class);
            Timestamp stringAsTimestamp = queriedRevision.get("isoDateString", Timestamp.class);

            assertNotNull(retrievedDate);
            assertNotNull(retrievedTimestamp);
            assertNotNull(longAsDate);
            assertNotNull(stringAsTimestamp);

            log.info("日期时间类型转换测试通过");
        });
    }

    private void testNumberTypesConversionInternal() {
        TimerUtils.measureExecutionTime("测试数字类型转换", () -> {
            String itemCode = "NUMBER-TYPES-" + dateCode;
            ItemRevision itemRevision = ItemRevision.newModel(itemCode, revId);
            itemRevision.setName("测试数字类型转换-" + dateCode);

            // 设置各种数字类型
            BigDecimal bigDecimal = new BigDecimal("123456.789");
            BigInteger bigInteger = new BigInteger("123456789012345");

            itemRevision.set("bigDecimalValue", bigDecimal);
            itemRevision.set("bigIntegerValue", bigInteger);
            itemRevision.set("numberString", "98765.4321");
            itemRevision.set("integerString", "98765");

            log.info("设置的数字类型: bigDecimal={}, bigInteger={}, numberString={}, integerString={}",
                    itemRevision.get("bigDecimalValue").getClass().getSimpleName(),
                    itemRevision.get("bigIntegerValue").getClass().getSimpleName(),
                    itemRevision.get("numberString").getClass().getSimpleName(),
                    itemRevision.get("integerString").getClass().getSimpleName()
            );

            // 保存并查询
            S.service(ObjectService.class).save(itemRevision);
            ItemRevision queriedRevision = S.service(RevisionService.class).queryRevision(
                    new Revisionable.CriteriaByCodeAndRevId<>(ItemRevision.class.getName(), itemCode, revId)
            );

            // 验证查询回来后的原始类型
            log.info("查询回来的数字类型: bigDecimal={}, bigInteger={}, numberString={}, integerString={}",
                    queriedRevision.get_properties().get("bigDecimalValue").getClass().getSimpleName(),
                    queriedRevision.get_properties().get("bigIntegerValue").getClass().getSimpleName(),
                    queriedRevision.get_properties().get("numberString").getClass().getSimpleName(),
                    queriedRevision.get_properties().get("integerString").getClass().getSimpleName()
            );

            // 测试数字类型转换
            BigDecimal retrievedBigDecimal = queriedRevision.get("bigDecimalValue", BigDecimal.class);
            BigInteger retrievedBigInteger = queriedRevision.get("bigIntegerValue", BigInteger.class);

            // 测试字符串到数字的转换
            BigDecimal stringAsBigDecimal = queriedRevision.get("numberString", BigDecimal.class);
            Integer stringAsInteger = queriedRevision.get("integerString", Integer.class);
            Double stringAsDouble = queriedRevision.get("numberString", Double.class);

            assertNotNull(retrievedBigDecimal);
            assertNotNull(retrievedBigInteger);
            assertNotNull(stringAsBigDecimal);
            assertNotNull(stringAsInteger);
            assertNotNull(stringAsDouble);

            assertEquals(bigDecimal, retrievedBigDecimal);
            assertEquals(bigInteger, retrievedBigInteger);
            assertEquals(new BigDecimal("98765.4321"), stringAsBigDecimal);
            assertEquals(Integer.valueOf(98765), stringAsInteger);
            assertEquals(Double.valueOf(98765.4321), stringAsDouble);

            log.info("数字类型转换测试通过");
        });
    }

    private void testCollectionTypesConversionInternal() {
        TimerUtils.measureExecutionTime("测试集合类型转换", () -> {
            String itemCode = "COLLECTION-TYPES-" + dateCode;
            ItemRevision itemRevision = ItemRevision.newModel(itemCode, revId);
            itemRevision.setName("测试集合类型转换-" + dateCode);

            // 设置集合类型
            List<String> stringList = Arrays.asList("apple", "banana", "cherry");
            List<Integer> intList = Arrays.asList(1, 2, 3, 4, 5);
            Map<String, Object> complexMap = new HashMap<>();
            complexMap.put("name", "测试用户");
            complexMap.put("age", 25);
            complexMap.put("active", true);

            itemRevision.set("stringListValue", stringList);
            itemRevision.set("intListValue", intList);
            itemRevision.set("complexMapValue", complexMap);

            log.info("设置的集合类型: stringList={}, intList={}, complexMap={}",
                    itemRevision.get("stringListValue").getClass().getSimpleName(),
                    itemRevision.get("intListValue").getClass().getSimpleName(),
                    itemRevision.get("complexMapValue").getClass().getSimpleName()
            );

            // 保存并查询
            S.service(ObjectService.class).save(itemRevision);
            ItemRevision queriedRevision = S.service(RevisionService.class).queryRevision(
                    new Revisionable.CriteriaByCodeAndRevId<>(ItemRevision.class.getName(), itemCode, revId)
            );

            // 验证查询回来后的原始类型
            log.info("查询回来的集合类型: stringList={}, intList={}, complexMap={}",
                    queriedRevision.get_properties().get("stringListValue").getClass().getSimpleName(),
                    queriedRevision.get_properties().get("intListValue").getClass().getSimpleName(),
                    queriedRevision.get_properties().get("complexMapValue").getClass().getSimpleName()
            );

            // 测试集合类型的获取（注意：这里可能需要特殊处理泛型类型）
            Object retrievedStringList = queriedRevision.get("stringListValue");
            Object retrievedIntList = queriedRevision.get("intListValue");
            Object retrievedComplexMap = queriedRevision.get("complexMapValue");

            assertNotNull(retrievedStringList);
            assertNotNull(retrievedIntList);
            assertNotNull(retrievedComplexMap);

            // 验证集合内容（可能需要进一步的类型转换）
            log.info("stringList content: {}", retrievedStringList);
            log.info("intList content: {}", retrievedIntList);
            log.info("complexMap content: {}", retrievedComplexMap);

            log.info("集合类型转换测试完成");
        });
    }

    private void save() {
        TimerUtils.measureExecutionTime("不同事物单个保存 " + batchSize + " ItemRevision对象", singleSaveItemRevisions(batchSize));
    }

    private void batchSaveWithLoopInOneTransaction() {
        TimerUtils.measureExecutionTime("同一事务中循环创建 " + batchSize + " ItemRevision对象", new Runnable() {
            @Override
            public void run() {
                List<ItemRevision> data = IntStream.rangeClosed(1, batchSize).mapToObj(idx -> {
                    ItemRevision rev = ItemRevision.newModel("A-" + dateCode + "-1-" + idx, revId);
                    rev.setName("保存BOM-" + dateCode + "-1-" + idx);
                    return rev;
                }).collect(Collectors.toList());
                S.service(MockService.class).batchSaveWithTransaction(data);
            }
        });
    }

    private void batchSave() {
        TimerUtils.measureExecutionTime("同一事务中批量创建 " + batchSize + " ItemRevision对象", batchSaveItemRevisions(batchSize));
    }

    private void query() {
        RevisionService revisionService = S.service(RevisionService.class);
        TimerUtils.measureExecutionTime("查询ItemRevision对象", () -> {
            ItemRevision rev = revisionService.queryRevision(new Revisionable.CriteriaByCodeAndRevId<>(ItemRevision.class.getName(), "A-" + dateCode + "-1", revId));
            log.info("query result: {}", rev);
            assertNotNull(rev);
            assertEquals("A-" + dateCode + "-1", rev.getCode());
            assertEquals(revId, rev.getRevId());
        });
    }

    private static Runnable batchSaveItemRevisions(int count) {
        return new Runnable() {
            @Override
            public void run() {
                List<ItemRevision> data = IntStream.rangeClosed(1, count).mapToObj(idx -> {
                    ItemRevision rev = ItemRevision.newModel("A-" + dateCode + "-" + idx, revId);
                    rev.setName("保存BOM-" + dateCode + "-" + idx);
                    return rev;
                }).collect(Collectors.toList());
                S.service(ObjectService.class).saveAll(data);
            }
        };
    }

    private static Runnable singleSaveItemRevisions(int count) {
        return new Runnable() {
            @Override
            public void run() {
                List<ItemRevision> data = IntStream.rangeClosed(1, count).mapToObj(idx -> {
                    ItemRevision rev = ItemRevision.newModel("B-" + dateCode + "-" + idx, revId);
                    rev.setName("保存BOM-" + dateCode + "-" + idx);
                    S.service(ObjectService.class).save(rev);
                    return rev;
                }).collect(Collectors.toList());
            }
        };
    }
}