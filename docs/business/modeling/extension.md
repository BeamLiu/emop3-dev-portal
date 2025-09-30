# 模型扩展方法

## 概述
本文档将从三个主要维度介绍EMOP的模型扩展方法：

1. **存储方式**：如何存储扩展的数据
   - 新建独立表存储：创建新的模型类和新的数据库表
   - 复用原表存储：在原表的`_properties`字段中存储新属性

2. **实现方式**：如何实现模型扩展
   - Java注解方式：使用`@PersistentEntity`等注解定义
   - DSL方式：使用EMOP的领域特定语言定义

3. **部署位置**：Java实现的代码部署在哪里
   - Plugin-API：适用于核心领域模型
   - 业务服务：适用于特定领域的定制化模型

## 存储方式详解

### 1. 新建独立表存储
当您需要创建全新的对象类型，并希望数据独立存储时，使用此方式。

- **特点**：
  - 创建新的数据库表
  - 完全独立的数据存储
  - 支持完整的索引功能

- **适用场景**：
  - 需要独立的数据表
  - 字段较多（超过5个）
  - 有复杂的业务规则

### 2. 复用原表存储
当您只需要为现有模型添加少量新字段时，使用此方式。

- **特点**：
  - 新字段存储在原表的`_properties`列
  - 简单快速的实现方式

- **适用场景**：
    - 新增字段较少（1-10个）
    - 字段类型简单
    - 与原模型业务紧密相关
    - 推荐用于同一类对象的细微差异扩展

- **PLM系统推荐场景**：
    - **文档类别扩展**：CAD文档 → Solidworks文档（增加内部版本号）
    - **项目管理扩展**：基础项目 → 工程项目（增加发生地）

::: warning ⚠️查询条件限制
1. 如果需要根据`_properties`中的字段进行查询，需要使用JSONB操作符，因为该字段在数据库中存储为JSONB格式
```java
// 示例：查询自定义属性的值（文本类型）
List<ItemRevision> revisions = Q.result(ItemRevision.class)
    .where("code = ? and CAST(_properties->>'customProperty' AS TEXT) = ?", "A-123", "SomeValue")
    .query();
	
// 示例：查询自定义属性的值（数值类型）
List<ItemRevision> revisions = Q.result(ItemRevision.class)
    .where("code = ? and CAST(_properties->>'customProperty' AS INT) > ?", "A-123", 18)
    .query();

// 示例：复合条件查询
List<ItemRevision> revisions = Q.result(ItemRevision.class)
    .where("code = ? AND CAST(_properties->>'managerUserId' AS INT) > ? and CAST(_properties->>'factoryLocation' AS TEXT)=?", 
           "A-123", 99, "Zhuhai")
    .query();
```
2. `_properties` 字段中的数据存储为 JSONB 类型，查询时无法使用索引。为了确保查询的性能，建议避免过多依赖此类字段的查询，同时尽量附加更多已建立索引的字段进行联合查询。
   :::

::: danger ⚠️重要约束
**避免修改系统内置表！**
- 不要对系统内置核心业务表进行同表存储扩展
- 推荐仅对业务相关的同类对象进行同表存储
- `_properties`中属性默认不会建立索引
  :::

## 实现方式详解

### 1. Java注解方式

#### 新建表实现示例
```java
@PersistentEntity(schema = Schema.SAMPLE, name="UserLocationEntity")
public class UserLocationEntity extends ItemRevision {
    @QuerySqlField
    private String city;

    @QuerySqlField
    private String country;

    @QuerySqlField
    private Long userId;
}
```
重要说明：
- 使用 `@PersistentEntity` 指定新的存储表
- 继承自已有模型类（如 `ItemRevision`）
- 使用 `@QuerySqlField` 标记需要查询的字段

#### 复用原表实现示例
```java
@Data
@PersistentEntity(schema = Schema.COMMON, name = "ItemRevision")  // 必须显式指向需要复用的同表
public final class FactoryEntity extends ItemRevision {
    
    @QuerySqlField(mapToDBColumn = false)  // 关键：设置为false，存储到_properties
    private String factoryLocation;

    @QuerySqlField(mapToDBColumn = false)  // 关键：设置为false，存储到_properties
    private Long managerUserId;

    public FactoryEntity() {
        super(FactoryEntity.class.getName());
    }
}
```

**关键内容：**
- **显式添加** `@PersistentEntity` 注解
- **重要**：尽量不要更改原表的schema，保持与父类一致，如果一定修改，不要是必填项，否则其他类型的数据无法插入
- **设置** `@QuerySqlField(mapToDBColumn = false)` 来指示字段存储到`_properties`

### 2. DSL方式

#### 新建表实现示例
```sql
create type sample.UserLocation extends ItemRevision {
    attribute city: String {
        persistent: true
        required: false
    }
    attribute country: String {
        persistent: true
    }
    attribute userId: Long {
        persistent: true
    }
    schema: sample
    tableName: USER_LOCATION
}
```

#### 复用原表实现示例
```sql
update type ItemRevision {
    attribute factoryLocation: String {
        persistent: true
        mapToDBColumn: false  // 关键：存储到_properties
    }
    attribute managerUserId: Long {
        persistent: true
        mapToDBColumn: false  // 关键：存储到_properties
    }
    schema: COMMON
    tableName: ITEM_REVISION // 关键：存储到的已存在的目标表
}
```

:::warning ⚠️注意事项
1. 核心的基础平台定义不建议直接修改，会导致派生的子类都进行字段变更
2. dsl当中的`mapToDBColumn`暂未实现
:::

## 部署位置说明
仅当使用Java方式实现时，需要考虑代码部署位置。

### 1. 部署在 Plugin-API
- **适用场景**：
    - 核心业务模型
    - 多个业务域服务共用的模型
- **特点**：
    - 无需额外配置

## 扩展属性的查询操作

### 1. SQL查询方式
```sql
-- 查询自定义文本属性
SELECT * FROM COMMON.ItemRevision 
WHERE CAST(_properties->>'factoryLocation' AS TEXT) = 'Zhuhai'

-- 查询自定义数值属性
SELECT * FROM COMMON.ItemRevision 
WHERE CAST(_properties->>'managerUserId' AS INT) = 100

-- 复杂查询：条件组合
SELECT * FROM COMMON.ItemRevision 
WHERE code = 'PART-001' 
  AND CAST(_properties->>'packageType' AS TEXT) = 'SMD'
  AND CAST(_properties->>'specification' AS TEXT) LIKE '%resistor%'

-- 使用CASE语句处理JSON属性
SELECT id, 
  CASE _properties->>'factoryLocation' 
    WHEN 'Zhuhai' THEN 'GuangDong' 
    ELSE 'Other' 
  END AS province 
FROM COMMON.ItemRevision 
WHERE code = 'FACTORY-001'
```

### 2. API查询方式
```java
// 使用Q API查询JSONB属性
List<FactoryEntity> factories = Q.result(FactoryEntity.class)
    .where("CAST(_properties->>'factoryLocation' AS TEXT) = ?", "Zhuhai")
    .query();

// 复合条件查询
List<FactoryEntity> factories = Q.result(FactoryEntity.class)
    .where("code = ? AND CAST(_properties->>'managerUserId' AS INT) > ? and CAST(_properties->>'factoryLocation' AS TEXT) = ?", 
           "FACTORY-001", 99, "Zhuhai")
    .query();

// 直接通过属性访问
String location = factoryEntity.getFactoryLocation();
Long managerId = factoryEntity.getManagerUserId();
```

## 性能考虑

### 1. 新建表方式
- 可以使用独立索引
- 查询性能好
- 适合大量数据场景
- 支持复杂查询优化

### 2. 复用原表方式
- `_properties`字段不支持索引
- 查询性能相对较低
- 适合小量数据场景
- 建议配合其他索引字段使用

## 最佳实践

1. **设计原则**
   - 评估数据量和查询性能需求
   - 考虑未来的扩展性
   - 权衡维护成本
   - **优先考虑业务相关性**：仅对同类业务对象进行同表存储

2. **开发规范**
   - 扩展属性使用有意义的名称
   - 遵循项目命名规范
   - 保持命名的一致性
   - **明确标注** `mapToDBColumn = false`

3. **数据一致性**
   - 合理设计数据校验规则
   - 维护数据的完整性
   - 注意并发处理

4. **文档维护**
   - 及时更新模型文档
   - 记录扩展的原因和目的
   - 说明扩展属性的用途

## 注意事项

1. **类型限制**
   - 复用原表时仅支持简单类型的扩展属性
   - 复杂结构建议使用新建表方式
   - 关系类型需要通过关系表实现

2. **性能影响**
   - 复用原表时查询无法使用索引
   - 建议配合其他索引字段使用
   - 控制`_properties`中的字段数量
   - **JSONB查询** 比传统列查询性能低

3. **部署相关**
   - 评估扩展方案的维护成本
   - 考虑未来的升级兼容性
   - 合理控制业务服务中的模型数量