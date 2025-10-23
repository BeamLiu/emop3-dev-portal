package io.emop.integrationtest.domain;

import io.emop.model.annotation.KeepBinary;
import io.emop.model.annotation.PersistentEntity;
import io.emop.model.annotation.QuerySqlField;
import io.emop.model.common.ItemRevision;
import io.emop.model.common.KeyValueObject;
import io.emop.model.common.MultiLanguage;
import io.emop.model.common.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 类型集成测试实体 - 测试各种数据类型的建模和持久化
 */
@PersistentEntity(schema = Schema.SAMPLE, name = "TypeTestEntity")
@Getter
@Setter
public final class TypeTestEntity extends ItemRevision {

    // ========== 基础数据类型 ==========
    @QuerySqlField
    private String stringField;

    @QuerySqlField
    private Integer integerField;

    @QuerySqlField
    private Long longField;

    @QuerySqlField
    private Double doubleField;

    @QuerySqlField
    private Float floatField;

    @QuerySqlField
    private Boolean booleanField;

    @QuerySqlField
    private Short shortField;

    @QuerySqlField
    private Byte byteField;

    // ========== 大数类型 ==========
    @QuerySqlField
    private BigDecimal bigDecimalField;

    @QuerySqlField
    private BigInteger bigIntegerField;

    // ========== 日期时间类型 ==========
    @QuerySqlField
    private java.sql.Date sqlDateField;

    @QuerySqlField
    private Timestamp timestampField;

    @QuerySqlField
    private LocalDate localDateField;

    @QuerySqlField
    private LocalDateTime localDateTimeField;

    @QuerySqlField
    private Instant instantField;

    // ========== 二进制类型 ==========
    @QuerySqlField
    private byte[] binaryField;

    // ========== UUID 类型 ==========
    @QuerySqlField
    private UUID uuidField;

    @QuerySqlField
    @KeepBinary
    private Object keepBinaryField;

    // ========== 枚举类型 ==========
    public enum Status {
        ACTIVE, INACTIVE, PENDING, COMPLETED
    }

    @QuerySqlField
    private Status statusField;

    public enum Priority {
        LOW(1), MEDIUM(2), HIGH(3), CRITICAL(4);

        private final int value;

        Priority(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    @QuerySqlField
    private Priority priorityField;

    // ========== 集合类型（存储为JSONB）==========
    @QuerySqlField
    private List<String> stringListField;
    @QuerySqlField
    private Set<String> stringSetField;
    @QuerySqlField
    private Map<String, String> stringMapField;
    @QuerySqlField
    private List<Integer> integerListField;
    @QuerySqlField
    private Set<Long> longSetField;
    @QuerySqlField
    private Map<String, Double> doubleMapField;

    // ========== 复杂对象类型 ==========
    @QuerySqlField
    private MultiLanguage multiLangField;

    // 嵌套对象
    public static class NestedObject {
        private String name;
        private Integer value;
        private List<String> tags;

        public NestedObject() {
        }

        public NestedObject(String name, Integer value, List<String> tags) {
            this.name = name;
            this.value = value;
            this.tags = tags;
        }

        // getters and setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getValue() {
            return value;
        }

        public void setValue(Integer value) {
            this.value = value;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }
    }
    @QuerySqlField
    private NestedObject nestedObjectField;
    @QuerySqlField
    private List<NestedObject> nestedObjectListField;
    @QuerySqlField
    private Map<String, NestedObject> nestedObjectMapField;

    // ========== 数组类型 ==========
    @QuerySqlField
    private String[] stringArrayField;
    @QuerySqlField
    private Integer[] integerArrayField;
    @QuerySqlField
    private int[] primitiveIntArrayField;
    @QuerySqlField
    private double[] primitiveDoubleArrayField;

    // ========== 特殊的 NULL 处理字段 ==========
    @QuerySqlField
    private String nullableStringField;
    @QuerySqlField
    private Integer nullableIntegerField;
    @QuerySqlField
    private List<String> nullableListField;
    @QuerySqlField
    private Map<String, Object> nullableMapField;

    // ========== 构造函数 ==========
    public TypeTestEntity() {
        super(TypeTestEntity.class.getName());
    }

    public TypeTestEntity(String code, String revId) {
        super(TypeTestEntity.class.getName());
        setCode(code);
        setRevId(revId);
    }

    // ========== 便捷方法 ==========

    /**
     * 创建一个包含所有类型数据的测试实例
     */
    public static TypeTestEntity createFullTestInstance(String code, String revId) {
        TypeTestEntity entity = new TypeTestEntity(code, revId);
        entity.setName("测试实体-" + code);

        // 基础类型
        entity.setStringField("测试字符串");
        entity.setIntegerField(12345);
        entity.setLongField(123456789L);
        entity.setDoubleField(123.456);
        entity.setFloatField(12.34f);
        entity.setBooleanField(true);
        entity.setShortField((short) 123);
        entity.setByteField((byte) 12);

        // 大数类型
        entity.setBigDecimalField(new BigDecimal("12345.6789"));
        entity.setBigIntegerField(new BigInteger("123456789012345"));

        // 日期时间类型
        long currentTime = System.currentTimeMillis();
        entity.setSqlDateField(new java.sql.Date(currentTime));
        entity.setTimestampField(new Timestamp(currentTime));
        entity.setLocalDateField(LocalDate.now());
        entity.setLocalDateTimeField(LocalDateTime.now());
        entity.setInstantField(Instant.now());

        // 二进制类型
        entity.setBinaryField("测试二进制数据".getBytes());

        // UUID
        entity.setUuidField(UUID.randomUUID());

        // 枚举
        entity.setStatusField(Status.ACTIVE);
        entity.setPriorityField(Priority.HIGH);

        // 集合类型
        entity.setStringListField(Arrays.asList("item1", "item2", "item3"));
        entity.setStringSetField(new HashSet<>(Arrays.asList("set1", "set2", "set3")));

        Map<String, String> stringMap = new HashMap<>();
        stringMap.put("key1", "value1");
        stringMap.put("key2", "value2");
        entity.setStringMapField(stringMap);

        entity.setIntegerListField(Arrays.asList(1, 2, 3, 4, 5));
        entity.setLongSetField(new HashSet<>(Arrays.asList(100L, 200L, 300L)));

        Map<String, Double> doubleMap = new HashMap<>();
        doubleMap.put("weight", 12.5);
        doubleMap.put("height", 180.5);
        entity.setDoubleMapField(doubleMap);

        // 复杂对象
        MultiLanguage multiLang = new MultiLanguage();
        multiLang.get_properties().put("zh_CN", "中文名称");
        multiLang.get_properties().put("en_US", "English Name");
        entity.setMultiLangField(multiLang);

        // 嵌套对象
        NestedObject nested = new NestedObject("测试嵌套", 42, Arrays.asList("tag1", "tag2"));
        entity.setNestedObjectField(nested);

        List<NestedObject> nestedList = Arrays.asList(
                new NestedObject("nested1", 1, Arrays.asList("a", "b")),
                new NestedObject("nested2", 2, Arrays.asList("c", "d"))
        );
        entity.setNestedObjectListField(nestedList);

        Map<String, NestedObject> nestedMap = new HashMap<>();
        nestedMap.put("first", new NestedObject("first", 10, Arrays.asList("x")));
        nestedMap.put("second", new NestedObject("second", 20, Arrays.asList("y")));
        entity.setNestedObjectMapField(nestedMap);

        // 数组类型
        entity.setStringArrayField(new String[]{"array1", "array2", "array3"});
        entity.setIntegerArrayField(new Integer[]{10, 20, 30});
        entity.setPrimitiveIntArrayField(new int[]{1, 2, 3});
        entity.setPrimitiveDoubleArrayField(new double[]{1.1, 2.2, 3.3});
        KeyValueObject object = new KeyValueObject();
        object.set("prop1", "A");
        object.set("prop2", 2l);
        entity.setKeepBinaryField(object);

        return entity;
    }

    /**
     * 创建一个包含NULL值的测试实例
     */
    public static TypeTestEntity createNullTestInstance(String code, String revId) {
        TypeTestEntity entity = new TypeTestEntity(code, revId);
        entity.setName("NULL测试实体-" + code);

        // 设置一些基础值
        entity.setStringField("基础字符串");
        entity.setIntegerField(100);
        entity.setBooleanField(false);

        // 其他字段保持NULL
        entity.setNullableStringField(null);
        entity.setNullableIntegerField(null);
        entity.setNullableListField(null);
        entity.setNullableMapField(null);

        return entity;
    }

    /**
     * 创建一个包含空集合的测试实例
     */
    public static TypeTestEntity createEmptyCollectionTestInstance(String code, String revId) {
        TypeTestEntity entity = new TypeTestEntity(code, revId);
        entity.setName("空集合测试实体-" + code);

        // 基础值
        entity.setStringField("空集合测试");
        entity.setIntegerField(200);

        // 空集合
        entity.setStringListField(new ArrayList<>());
        entity.setStringSetField(new HashSet<>());
        entity.setStringMapField(new HashMap<>());
        entity.setIntegerListField(Collections.emptyList());
        entity.setNestedObjectListField(Collections.emptyList());
        entity.setNestedObjectMapField(Collections.emptyMap());

        return entity;
    }

    @Override
    public String toString() {
        return String.format("TypeTestEntity{id=%d, code='%s', revId='%s', name='%s'}",
                getId(), getCode(), getRevId(), getName());
    }
}