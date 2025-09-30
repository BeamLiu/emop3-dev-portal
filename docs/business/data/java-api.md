# Java API 数据操作

本文档介绍如何使用 EMOP 的 Java API 进行数据操作。需要注意，API 的使用位置（Plugin 开发或业务服务开发）会影响具体的调用方式和性能表现，见[后台开发指南](../backend-development)。

## API 使用场景

### 1. Plugin 开发（EMOP Server 内）
- JVM 内直接调用，无 RPC 开销
- 使用 `@Transactional` 注解进行事务控制
- 适用于核心领域模型和基础操作的实现

### 2. 业务服务开发（PLM 等）
- 通过 `sofa-rpc` 进行 RPC 调用，需要考虑网络延迟（约 0.5~2 毫秒/次）
- 需要手动控制事务
- 应尽量使用批量接口减少 RPC 调用次数

## 基础操作

### 1. 查询操作

所有查询都从 `Q` 类开始：

```java
// 根据 ID 查询单个对象
ItemRevision revision = Q.objectType(ItemRevision.class.getName())
                        .whereById(123L)
                        .first();

// 根据条件计数
Long count = Q.result(Relation.class)
                        .where("primaryId = ? and relationName = ? and secondaryId = ?", primary.getId(), relationType.getName(), secondary.getId())
						.count();

// 根据条件判断是否存在
boolean exists = Q.result(Relation.class)
                        .where("primaryId = ? and relationName = ? and secondaryId = ?", primary.getId(), relationType.getName(), secondary.getId())
						.exists();

// 使用条件查询
List<ItemRevision> revisions = Q.result(ItemRevision.class)
                               .where("code = ?", "A-123")
                               .query();

// 使用 SQL 查询
String sql = "SELECT * FROM COMMON.Item_Revision WHERE code = ?";
List<ItemRevision> results = Q.result(ItemRevision.class)
                             .sql(sql, "A-123")
                             .query();

// 全文检索查询							 
FullTextSearchService.SearchResult results = Q.fullTextSearch("发动机设计").search();							 
```

#### 元组批量查询

当需要查询多个字段组合条件时，相较于`IN`操作只能是一个column，元组操作可以是 IN 一个元组数组，例如 `(codA, revA), (codeB, revB)`，使用元组批量查询可以显著提升性能：

```java
import static io.emop.model.query.tuple.Tuples.*;

// 2元组批量查询 - 替代多次单独查询
List<Tuple2<String, String>> codeRevPairs = Arrays.asList(
    tuple("A-123", "1"),
    tuple("B-456", "2"),
    tuple("C-789", "3")
);

List<ItemRevision> revisions = Q.result(ItemRevision.class)
    .whereTuples("code, revId", codeRevPairs)
    .query();

// 3元组查询
List<Tuple3<Long, String, String>> triples = Arrays.asList(
    tuple(1L, "FRIEND", "active"),
    tuple(2L, "FOLLOW", "active"),
    tuple(3L, "BLOCK", "inactive")
);

List<Relation> relations = Q.result(Relation.class)
    .whereTuples("primaryId, relationName, status", triples)
    .asc("primaryId")
    .query();

// 支持分页和排序
Page<ItemRevision> page = Q.result(ItemRevision.class)
    .whereTuples("code, revId", codeRevPairs)
    .asc("code")
    .pageSize(20)
    .pageNumber(1)
    .queryPage();
```

**性能优势：**
- 将 N 次数据库查询合并为 1 次查询
- 减少网络往返次数，特别适合 RPC 调用场景
- 典型性能提升：10-50 倍（取决于批量大小）

**适用场景：**
- 需要查询多个不同条件组合的场景
- 替代传统的多次 IN 查询
- 高并发场景下的性能优化

#### 使用场景指导

**方案选择对比：**

| 场景 | 推荐方案 | 理由 |
|------|----------|------|
| 简单多条件批量查询 | `whereTuples` DSL | 简单易用，类型安全 |
| 需要 JOIN 多表 | SQL + UNNEST | 灵活性更好 |
| 复杂 WHERE 条件 | SQL + UNNEST | 避免 DSL 限制 |
| 需要聚合/分组 | SQL + UNNEST | 原生 SQL 表达力更强 |
| 性能敏感的复杂查询 | SQL + UNNEST | 可精细控制查询计划 |

**whereTuples 适用场景：**
```java
// 简单的多字段条件查询
List<ItemRevision> revisions = Q.result(ItemRevision.class)
    .whereTuples("code, revId", pairs)
    .asc("code")
    .query();
```

**SQL + UNNEST 适用场景：**
```java
// ✅ 复杂的多表连接 + 聚合查询
String sql = """
    SELECT r.code, r.name, COUNT(b.id) as bom_count, 
           AVG(s.rating) as avg_supplier_rating
    FROM item_revision r
    INNER JOIN (
        SELECT UNNEST(?::text[]) AS code, 
               UNNEST(?::text[]) AS rev_id
    ) AS conditions ON r.code = conditions.code 
                    AND r.rev_id = conditions.rev_id
    LEFT JOIN bom_line b ON r.id = b.parent_id
    LEFT JOIN supplier s ON r.supplier_id = s.id
    WHERE r._properties->>'category' = ?
      AND r.create_date >= ?
    GROUP BY r.code, r.name
    HAVING COUNT(b.id) > ?
    ORDER BY avg_supplier_rating DESC NULLS LAST
    """;

List<Map<String, Object>> results = Q.objectType(ItemRevision.class.getName())
    .sql(sql, codes, revIds, category, sinceDate, minBomCount)
    .queryRaw();
```

**使用场景选择：**
- **简单批量查询**：使用 `whereTuples` DSL 接口
- **复杂查询**：直接使用 SQL + UNNEST 语法

**注意事项：**
- `whereTuples`：建议单次查询控制在 1000 个元组以内
- 复杂字段表达式、多表连接、复杂条件：直接使用 SQL + UNNEST
- 支持 2-5 元组，根据实际需求选择

::: warning ⚠️查询条件限制见[这里](../modeling/extension#_2-复用原表存储)
:::
### 2. 对象引用(ObjectRef)
ObjectRef让查询都可以面向对象，从对象出发，而不是从Service出发
```java
//使用id查询
new ObjectRef(-6734228567990456320L).get("target/name")
//使用code和revId作为引用
new ObjectRef(ItemRevision.class.getName(),"S10-221-2", "A").get("code")
```

### 3. 创建操作

```java
// 创建单个对象
ItemRevision revision = new ItemRevision();
revision.setCode("A-123");
revision.setName("示例修订版本");
revision = S.service(ObjectService.class).save(revision);

// 批量创建（推荐）
List<ItemRevision> revisions = new ArrayList<>();
// ... 添加多个对象
List<ItemRevision> saved = S.service(ObjectService.class).saveAll(revisions);
```

### 4. 更新操作

```java
// 单个对象更新
revision.setName("新名称");
revision = S.service(ObjectService.class).save(revision);

// 使用 Map 更新属性
Map<String, Object> updateData = new HashMap<>();
updateData.put("name", "新名称");
ModelObject updated = S.service(ObjectService.class).update(revision.getId(), updateData);
```

### 5. 删除操作

```java
// 删除单个对象
S.service(ObjectService.class).delete(123L);

// 条件删除
Q.objectType(ItemRevision.class.getName())
 .where("code LIKE ?", "TEST-%")
 .delete();
```
::: warning ⚠️查询条件限制见[这里](../modeling/extension#_2-复用原表存储)
:::

## 逻辑对象查询
EMOP 提供了强大的逻辑对象查询能力，允许基于业务编码和版本规则进行查询，支持一步到位的解析操作。

逻辑对象查询主要用于处理版本化对象的业务场景：
- **[LogicalModelObject](../modeling/guide#logicalmodelobject-逻辑对象)**: 表示业务概念的逻辑对象，基于业务编码定义
- **[RevisionRule](../modeling/guide#revisionrule-版本规则)**: 版本规则，决定如何从多个版本中选择具体的物理对象
- **一步解析**: 直接从逻辑查询得到物理对象，无需手动解析

### 1. 基础逻辑查询

```java
// 创建逻辑查询构建器
Q.logical(ItemRevision.class)     // 使用Class
Q.logical("ItemRevision")         // 使用对象类型字符串
```

### 2. 条件查询与解析

```java
// 查询并直接解析为物理对象
List<ItemRevision> results = Q.logical(ItemRevision.class)
    .where("name LIKE ?", "发动机%")
    .withRule(RevisionRule.LATEST_RELEASED)
    .query();

// 查询单个对象
ItemRevision single = Q.logical(ItemRevision.class)
    .where("code = ?", "ENG-001")
    .withRule(RevisionRule.LATEST)
    .first();
```

### 3. 分页查询

```java
// 分页解析为物理对象
Page<ItemRevision> physicalPage = Q.logical(ItemRevision.class)
    .where("category = ?", "Engine")
    .withRule(RevisionRule.LATEST_RELEASED)
    .asc("name")
    .page(0, 20)
    .queryPage();
```

### 4. 版本规则应用

```java
// 使用不同版本规则
List<ItemRevision> latestVersions = Q.logical(ItemRevision.class)
    .where("category = ?", "Part")
    .withRule(RevisionRule.LATEST)           // 最新版本
    .query();

List<ItemRevision> releasedVersions = Q.logical(ItemRevision.class)
    .where("category = ?", "Part")
    .withRule(RevisionRule.LATEST_RELEASED)  // 最新发布版本
    .query();

List<ItemRevision> workingVersions = Q.logical(ItemRevision.class)
    .where("category = ?", "Part")
    .withRule(RevisionRule.LATEST_WORKING)   // 最新工作版本
    .query();

// 精确版本查询
ItemRevision precise = Q.logical(ItemRevision.class)
    .where("code = ?", "ENG-001")
    .withRule(RevisionRule.PRECISE)
    .first();
```

### 5. 排序和统计

```java
// 排序查询
List<ItemRevision> sorted = Q.logical(ItemRevision.class)
    .where("code = ?", "ENG-001")
    .withRule(RevisionRule.LATEST_RELEASED)
    .asc("code")
    .desc("_creationDate")
    .query();

// 统计查询
long count = Q.logical(ItemRevision.class)
    .where("category = ?", "Engine")
    .withRule(RevisionRule.LATEST)
    .count();

// 存在性检查
boolean exists = Q.logical(ItemRevision.class)
    .where("code = ?", "ENG-001")
    .withRule(RevisionRule.RELEASED)
    .exists();
```

### 6. 实际应用场景

#### 1. BOM 结构查询

```java
// 查询产品的最新发布版BOM结构
List<BomLine> bomLines = Q.logical(BomLine.class)
    .where("parent/code = ?", "AIRCRAFT-001")
    .withRule(RevisionRule.LATEST_RELEASED)
    .asc("lineNumber")
    .query();

// 处理BOM层级关系
bomLines.forEach(line -> {
    // 获取目标物料的最新发布版本
    ItemRevision target = line.getTarget();
    System.out.println("物料: " + target.getCode() + " 版本: " + target.getRevId());
});
```

#### 2. 文档管理

```java
// 查询项目相关的最新文档
List<Document> documents = Q.logical(Document.class)
    .where("project = ? AND type = ?", "PROJECT-001", "设计图纸")
    .withRule(RevisionRule.LATEST_RELEASED)
    .desc("lastModifiedDate")
    .query();

// 分页展示文档列表
Page<Document> docPage = Q.logical(Document.class)
    .where("category = ?", "技术文档")
    .withRule(RevisionRule.LATEST)
    .asc("name")
    .page(pageNum, pageSize)
    .queryPage();
```

#### 3. 物料需求计划

```java
// 查询生产计划需要的物料清单
List<Part> requiredParts = Q.logical(Part.class)
    .where("status = ? AND category IN ?", "Active", Arrays.asList("原材料", "标准件"))
    .withRule(RevisionRule.LATEST_RELEASED)  // 使用最新发布版本进行生产
    .asc("code")
    .query();

// 检查物料可用性
requiredParts.forEach(part -> {
    // 基于最新发布版本检查库存
    checkInventory(part);
});
```

### 7. 与用户上下文结合

```java
// 在指定版本规则的上下文中执行查询
List<ItemRevision> items = UserContext.withRevisionRule(
    RevisionRule.LATEST_RELEASED,
    () -> Q.logical(ItemRevision.class)
        .where("category = ?", "Engine")
        .query()  // 自动使用上下文中的版本规则
);
```

## 进阶操作

### 1. Business Key 操作

系统支持通过业务唯一键进行对象的查找、更新和插入操作。Business Key 是确保数据唯一性的业务规则，在类型定义中通过 `businessUniqueKeys` 指定。

```java
// 通过业务键查找对象
ModelObject existing = Q.objectType(typeDef.getName())
    .whereByBusinessKeys(obj.toKeyValue())
    .first();

// 基于业务键的更新或插入(Upsert)
ModelObject result = S.service(ObjectService.class)
    .upsertByBusinessKey(obj);

// 根据查询条件进行更新或插入
Query<T> query = Q.objectType(typeDef.getName())
    .where("code = ? and revId = ?", "P001", "A");
    
List<T> results = S.service(ObjectService.class)
    .upsertByCondition(obj, query, UpsertStrategy.UPDATE_ALL);

// 可选的 UpsertStrategy:
// - THROW_ERROR: 如果找到多条记录则抛出异常
// - UPDATE_ALL: 更新所有匹配的记录
// - UPDATE_FIRST: 只更新第一条记录
```

::: warning ⚠️注意
在保存或更新时，系统会自动检查业务唯一键约束。如果违反约束，会抛出 UniqueConstraintException。
:::

### 2. 级联更新处理
EMOP 提供了 directive 机制来控制对象的保存行为：
```java
// 1. 自动处理关系
BomLine bomLine = new BomLine();
bomLine.setChildren(subLines);    // 设置子项关系
bomLine.setTarget(material);      // 设置目标对象关系

// 保存时自动处理关系
bomLine.directiveHandleAssociateRelations();
S.service(ObjectService.class).save(bomLine);

// 2. 基于业务键的 upsert
Material material = new Material();
material.setCode("M001");     // 业务键
material.setName("示例物料");

// 如果存在则更新，不存在则插入
material.directiveUpsertForSave();
S.service(ObjectService.class).save(material);
```
directive 机制的主要用途：

- `directiveHandleAssociateRelations()`: 保存时自动处理对象的所有关联关系
- `directiveUpsertForSave()`: 执行基于业务键的 upsert 操作


::: info ℹ️最佳实践
1. 在处理复杂对象（如 BOM）时，推荐使用 directiveHandleAssociateRelations 来自动维护关联关系，避免手动管理关系带来的复杂性。
2. 由于结构关系(StructuralRelation)是外键设置，比较简单，需要应用层自行维护外键值以达到关系更新的目的。
:::


### 3. 数据导入

#### a) 基础表格导入
```java
TableData tableData = TableData.builder()
    .headers(Arrays.asList("物料编号", "物料名称", "版本号"))
    .rows(Arrays.asList(
        createRow("MAT-001", "物料1", "A"),
        createRow("MAT-002", "物料2", "A")
    ))
    .build();

ImportRequest request = ImportRequest.builder()
    .tableData(tableData)                 
    .targetType(Material.class.getName()) 
    .columnMappings(Map.of(
        "物料编号", List.of("code"),
        "物料名称", List.of("name"),
        "版本号", List.of("revId")
    ))
    .config(ImportConfigModel.builder()
        .existence(DuplicateStrategy.skip)
        .build())
    .build();

ImportResult result = S.service(DataImportService.class).importTable(request);
```

::: info ℹ️支持的重复数据处理策略:
-   error: 遇到重复数据时报错
-   skip: 跳过重复数据
-   update: 更新已存在数据 
:::

#### b) 树形结构导入
```java
TreeImportRequest request = TreeImportRequest.builder()
    .tableData(tableData)
    .targetType(Task.class.getName())
    .parentKeyMapping(ParentKeyMapping.builder()
        .fromColumns(List.of("父任务编号"))
        .toColumns(List.of("任务编号"))
        .build())
    .config(ImportConfig.builder()
        .relationType("childTasks")
        .build())
    .columnMappings(columnMappings)
    .build();
ImportResult result = S.service(DataImportService.class).importTree(request);
```
::: warning ⚠️注意事项
为防止数据混乱，树形结构导入是Merge操作，也就是说只会添加新的节点和关系，更新已存在节点的属性，而不会删除任何现有的子节点或关系。例如，当前树结构为：
```
A001
├── B001
│   └── C001
└── B002
```
如果导入数据只包含：
```
A001
├── B001
└── B003
```
最终结果会是：
```
A001
├── B001
│   └── C001 (保留)
├── B002 (保留)
└── B003 (新增)
```
如果需要完全重建树结构，请在导入前使用`remove relation` 或 `delete tree` 命令清理现有结构。
:::

#### c) 高级特性

##### XPath导入支持

```java
ImportRequest request = ImportRequest.builder()
    .tableData(tableData)
    .targetType(Part.class.getName())
    .columnMappings(Map.of(
        "编码", List.of("code"),
        "供应商代码", List.of("suppliers/code"), 
        "供应商名称", List.of("suppliers/name"),
        "联系人", List.of("suppliers/contact")
    ))
    .config(ImportConfigModel.builder()
        .allowXPathAutoCreate(true)
        .build())
    .build();
ImportResult result = S.service(DataImportService.class).importTable(request);
```

##### 类型解析扩展点

```java
ImportRequest request = ImportRequest.builder()
    .config(ImportConfigModel.builder()
        .typeResolverScript("""
            if(data.get('type') == 'GROUP') {
                return 'ItemRevision'
            }
            return 'Part'
        """)
        .build())
    .build();
ImportResult result = S.service(DataImportService.class).importTable(request);
```

##### 数据转换扩展点
```java
ImportRequest request = ImportRequest.builder()
    .config(ImportConfigModel.builder()
        .dataMappingScript("""
            if (data.createDate) {
                data.createDate = new SimpleDateFormat("yyyy-MM-dd").parse(data.createDate)
            }
            return data
        """)
        .build())
    .build();
ImportResult result = S.service(DataImportService.class).importTable(request);
```

##### 数据校验扩展点
针对输入数据进行校验
```java
ImportRequest request = ImportRequest.builder()
    .tableData(tableData)
    .targetType(Employee.class.getName())
    .config(ImportConfigModel.builder()
        // 规则校验
        .rules(Arrays.asList(
            // 必填校验
            ValidationRule.builder()
                .field("name")
                .condition("required")
                .message("姓名不能为空")
                .build(),
            
            // 正则校验
            ValidationRule.builder()
                .field("email")
                .condition("regex")
                .params(Map.of("pattern", "^[A-Za-z0-9+_.-]+@(.+)$"))
                .message("邮箱格式不正确")
                .build(),
            
            // 数值范围校验
            ValidationRule.builder()
                .field("age")
                .condition("range")
                .params(Map.of("min", 0, "max", 150))
                .message("年龄必须在0-150之间")
                .build(),
            
            // 长度校验 
            ValidationRule.builder()
                .field("description")
                .condition("length")
                .params(Map.of("min", 10, "max", 500))
                .message("描述长度必须在10-500字符之间")
                .build(),
            
            // 枚举值校验
            ValidationRule.builder()
                .field("status")
                .condition("enum") 
                .params(Map.of("values", Arrays.asList("ACTIVE", "INACTIVE", "ONLEAVE")))
                .message("状态值不正确")
                .build()
        ))
        // 脚本校验 
        .validationScript("""
            // 输入变量:
            // - rows: List<Map<String,String>> 所有数据行
            def errors = []
            // 示例: 检查年龄必须大于18岁
            rows.findAll { Integer.parseInt(it.age) < 18 }.each { row ->
                errors << new ValidationError(
                    rowIndex: rows.indexOf(row),
                    field: "age", 
                    value: row.age,
                    message: "员工必须年满18岁",
                    level: 2
                )
            }
            
            // 示例: 检查邮箱域名
            rows.findAll { !it.email?.endsWith("@company.com") }.each { row ->
                errors << new ValidationError(
                    rowIndex: rows.indexOf(row),
                    field: "email",
                    value: row.email, 
                    message: "邮箱必须使用公司域名",
                    level: 2  
                )
            }
            
            return errors
            """)
        .build())
    .build();

ImportResult result = S.service(DataImportService.class).importTable(request);
```
针对输入数据进行校验
```java
ImportRequest request = ImportRequest.builder()
    .tableData(tableData)
    .targetType(Employee.class.getName())
    .config(ImportConfigModel.builder()
        // 规则校验
        .rules(Arrays.asList(
            // 必填校验
            ValidationRule.builder()
                .field("name")
                .condition("required")
                .message("姓名不能为空")
                .build(),
            
            // 正则校验
            ValidationRule.builder()
                .field("email")
                .condition("regex")
                .params(Map.of("pattern", "^[A-Za-z0-9+_.-]+@(.+)$"))
                .message("邮箱格式不正确")
                .build(),
            
            // 数值范围校验
            ValidationRule.builder()
                .field("age")
                .condition("range")
                .params(Map.of("min", 0, "max", 150))
                .message("年龄必须在0-150之间")
                .build(),
            
            // 长度校验 
            ValidationRule.builder()
                .field("description")
                .condition("length")
                .params(Map.of("min", 10, "max", 500))
                .message("描述长度必须在10-500字符之间")
                .build(),
            
            // 枚举值校验
            ValidationRule.builder()
                .field("status")
                .condition("enum") 
                .params(Map.of("values", Arrays.asList("ACTIVE", "INACTIVE", "ONLEAVE")))
                .message("状态值不正确")
                .build()
        ))
        // 脚本校验 
        .validationScript("""
            // 输入变量:
            // - rows: List<Map<String,String>> 所有数据行
            
            def errors = []
            
            // 示例: 检查年龄必须大于18岁
            rows.findAll { Integer.parseInt(it.age) < 18 }.each { row ->
                errors << new ValidationError(
                    rowIndex: rows.indexOf(row),
                    field: "age", 
                    value: row.age,
                    message: "员工必须年满18岁",
                    level: 2
                )
            }
            
            // 示例: 检查邮箱域名
            rows.findAll { !it.email?.endsWith("@company.com") }.each { row ->
                errors << new ValidationError(
                    rowIndex: rows.indexOf(row),
                    field: "email",
                    value: row.email, 
                    message: "邮箱必须使用公司域名",
                    level: 2  
                )
            }
            
            return errors
            """)
        .build())
    .build();

ImportResult result = S.service(DataImportService.class).importTable(request);
```

::: info ℹ️校验规则说明
1. `required`: 检查字段是否为空
2. `regex`: 正则表达式校验，params需要提供pattern参数
3. `range`: 数值范围校验，params需要提供min和max参数
4. `length`: 字符串长度校验，params需要提供min和max参数
5. `enum`: 枚举值校验，params需要提供values参数(List类型)
:::

::: info ℹ️Groovy校验脚本说明

输入参数:

`rows`: `List<Map<String,String>>` 类型，包含所有数据行

返回值: `List<ValidationError>` 类型的错误列表
`ValidationError`属性:

1. `rowIndex`: 行号，-1表示全局错误
2. `field`: 字段名
3. `value`: 字段值
4. `message`: 错误消息
5. `level`: 错误级别(1:警告, 2:错误)
:::


##### 基于接口的扩展配置
```java
// 自定义类型解析器
public class CustomTypeResolver implements TypeResolverExtension {
    @Override
    public String resolveType(Map<String, Object> data, String xpath) {
        if (xpath != null && xpath.startsWith("associateRelation")) {
            return "Supplier";
        }
        String code = (String) data.get("code");
        return code != null && code.startsWith("P1-") ? "Part" : "ItemRevision";
    }
}

// 自定义数据映射器
public class CustomDataMapper implements DataMappingExtension {
    @Override
    public Map<String, Object> mapData(Map<String, Object> data, ImportContext context) {
        Map<String, Object> result = new HashMap<>(data);
        // 转换日期
        if (result.containsKey("_creationDate")) {
            try {
                result.put("_creationDate", new SimpleDateFormat("yyyy-MM-dd")
                    .parse((String) result.get("_creationDate")));
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        }
        return result;
    }
}

// 使用示例
ImportRequest request = ImportRequest.builder()
    .tableData(tableData)
    .targetType(Part.class.getName())
    .config(ImportConfig.builder()
        .typeResolver(new CustomTypeResolver())
        .dataMapper(new CustomDataMapper())
        .existence(DuplicateStrategy.update)
        .build())
    .build();
ImportResult result = S.service(DataImportService.class).importTable(request);
```

### 4. 数据导出
TBD

## 事务处理

### Plugin 开发中的事务

使用 `@Transactional` 注解：

```java
@Transactional
public void saveWithTransaction(List<ModelObject> objs) {
    queryHelper().saveAll(objs);
}
```

### 主从数据一致性控制

EMOP 默认采用[主从架构](../../platform/metadata.md#主从分离架构)，框架提供了灵活的数据一致性控制机制，允许开发人员根据业务需求选择合适的一致性级别。

#### 一致性级别

```java
//数据一致性要求, 默认在事务外的查询都是走从库, 有些实时性要求较高的场景, 需要从主库获取数据
public enum ConsistencyLevel {
    EVENTUAL,    // 最终一致性（默认，从库读取）
    STRONG      // 强一致性（主库读取）
}
```

#### 使用方式

1. **通过工具类控制**

```java
// 使用强一致性读取数据
User user = S.withStrongConsistency(() -> 
    objectService.findById(userId)
);

// 批量操作使用强一致性
List<User> users = S.withStrongConsistency(() -> {
    List<User> result = objectService.findByDepartment(deptId);
    result.addAll(objectService.findByRole(roleId));
    return result;
});
```

#### 最佳实践

1. **默认使用最终一致性**

* 大多数查询场景下，使用从库读取（最终一致性）可以提供更好的性能和可扩展性
* 系统默认使用最终一致性，无需特别配置

2. **适合使用强一致性的场景**

* 更新后立即读取的场景
* 要求实时准确性的关键业务操作

```java
// 示例：更新用户信息后立即读取
public User updateAndGet(Long userId, Map<String, Object> updates) {
    // 先更新并提交事务
    objectService.update(userId, updates);
    
    // 使用强一致性读取最新数据
    return S.withStrongConsistency(() -> 
        objectService.findById(userId)
    );
}
```

3. **避免大范围使用强一致性**

* 强一致性会增加主库负载
* 尽量将强一致性限制在必要的场景

```java
// 不推荐：整个服务都使用强一致性
@Service
public class UserService {
    public void someMethod() {
        ConsistencyContext.setConsistencyLevel(ConsistencyLevel.STRONG);
        // ... 大量操作
        ConsistencyContext.clear();
    }
}

// 推荐：只在必要的操作使用强一致性
@Service
public class UserService {
    public void someMethod() {
        // 普通查询使用从库
        List<User> users = objectService.findByDepartment(deptId);
        
        // 只在关键操作使用强一致性
        User updated = S.withStrongConsistency(() -> 
            objectService.findById(userId)
        );
    }
}
```

4. **注意事项**

* 在事务中的查询会自动使用强一致性（主库），无需手动设置
* 嵌套使用时，内层会继承外层的一致性级别
* 确保正确清理一致性上下文，推荐使用 `S.withStrongConsistency()` 方法
* 异步操作中需要特别注意一致性级别的传递

## 最佳实践

### Plugin 开发最佳实践

1. 合理使用 `@Transactional` 注解
2. 提供粗粒度的批量操作接口
3. 实现必要的补偿机制

### 业务服务开发最佳实践

1. **优化 RPC 调用**
   ```java
   // 推荐：批量处理
   public void goodPractice() {
       List<BOMItem> items = prepareBOMItems();
       S.service(ObjectService.class).saveAll(items);  // 一次 RPC
   }

   // 避免：循环调用
   public void badPractice() {
       for (BOMItem item : items) {
           S.service(ObjectService.class).save(item);  // 多次 RPC
       }
   }
   ```

2. **合理控制事务范围**
   - 避免跨多个 RPC 的大事务
   - 必要时实现补偿机制

3. **使用批量接口**
   - 优先使用 `saveAll`、`deleteAll` 等批量操作
   - 合并多个操作到一次 RPC 调用

## 注意事项

1. **事务使用**
   - Plugin 开发：使用 `@Transactional` 注解
   - 业务服务：手动控制事务
   - 避免跨 RPC 的分布式事务

2. **性能优化**
   - 减少 RPC 调用次数
   - 使用批量接口
   - 注意查询条件的索引使用
   - 优先使用元组查询替代多次单独查询

3. **数据一致性**
   - 正确处理业务唯一键约束
   - 使用乐观锁机制处理并发
   - 注意关系数据的完整性

4. **元组查询注意事项**
   - 建议单次查询的元组数量控制在 1000 个以内
   - **简单场景**：使用 `whereTuples` DSL，参数为 Tuple 对象
   - **复杂场景**：使用 SQL + UNNEST，参数为按字段分组的数组
   - 支持 2-5 元组，根据实际需求选择

   ```java
   // whereTuples 参数方式
   List<Tuple2<String, String>> tuples = Arrays.asList(
       tuple("A-123", "1"),
       tuple("B-456", "2")
   );
   Q.result(ItemRevision.class).whereTuples("code, revId", tuples).query();
   
   // SQL + UNNEST 参数方式  
   String[] codes = {"A-123", "B-456"};
   String[] revIds = {"1", "2"};
   Q.result(ItemRevision.class).sql(unnestSql, codes, revIds).query();
   ```