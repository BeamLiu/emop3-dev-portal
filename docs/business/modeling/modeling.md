# 如何定义业务模型

## 概述

在 EMOP 中定义业务模型有两种方式：Java 注解和 DSL。本文将通过文档管理和 BOM 的实际场景，详细说明如何定义业务模型。

:::warning ⚠️注意事项
- 系统中所有对象的ID都是基于模型定义(TypeDefinition)的ID使用雪花算法生成
- 修改类型定义时，必须保持原有的模型定义ID不变，不可以删除再新增，以确保历史数据的有效性
- 强制删除已创建数据的模型，也会导致历史数据失效
- 如果一定要变更模型定义ID，需要针对历史数据进行迁移
:::

## 属性命名规范

在定义模型时，属性命名遵循以下规范：

1.  **普通属性（无前缀）**
    -   用于存储业务数据
    -   例如：`name`, `description`, `status`
    -   这些属性会持久化到数据库
2.  **单下划线属性（_prefix）**
    -   用于系统级别的数据
    -   会持久化到数据库
    -   例如：`_creationDate`, `_state`, `_version`
    -   通常由系统自动管理
3.  **双下划线属性（__prefix）**
    -   用于框架内部使用或临时存储
    -   不会持久化到数据库
    -   例如：`__loadedProperties__`, `__computedProperties__`
    -   通常用于运行时的计算值或缓存
4.  **命名空间属性（namespace$name）**
    -   用于特定领域或上下文的数据
    -   通过`_properties`持久化到数据库
    -   使用`$`符号分隔命名空间和名称
    -   常用命名空间包括：
        - `meta$`: 元数据，例如: `meta$showIcon`
        - `ctx$`: 上下文数据，例如: `ctx$classification`
5.  **给前端展示属性（属性名__display__）**

    前端渲染系统中，对于任何元数据属性，系统会按以下优先级显示，序列化时可以按照以下约定让前端正确显示对应的显示值：
    -   优先显示：`属性名__display__` - 用户友好的显示值
    -   后备显示：`属性名` - 原始属性值
```javascript
const modelObject = {
    _objectType: "ItemRevision",
    _objectType__display__: "版本对象"
}
const task = {
    _creator: "1234567890",
    _creator__display__: "张三 (研发部)"
}
```
:::warning ⚠️注意事项

-   在设计模型时，要根据数据的用途选择合适的命名方式
-   双下划线属性在对象序列化时可能会被清理掉
-   命名空间属性不会直接作为对象字段持久化，而是存储在`_properties`中
:::

## 业务需求分析

在开始定义模型之前，我们需要分析业务需求。以文档管理为例：

1. **业务对象的层次结构**
   - 文件夹（Folder）：用于组织文档
   - 文档（Document）：具体的文件

2. **对象关系**
   - 文件夹可以包含其他文件夹和文档集
   - 文档集可以包含多个文档
   - 文档属于某个文档集

3. **属性需求**
   - 基本属性：名称、描述等
   - 业务属性：文件大小、类型等
   - 系统属性：创建信息、状态等

## 通过 Java 注解定义模型

### 构造函数规范

:::warning ⚠️在使用 Java 注解定义模型时，需要遵循以下构造函数规范：

1. **objectType 要求**：
   - 每个实体类的实例必须在创建时指定 `objectType`
   - 默认 `objectType` 就是类的完全限定名（包名+类名）
   - `objectType` 一旦设置就不能更改

2. **构造函数设计原则**：
   - 如果类只提供默认构造函数，必须将类声明为 `final`
   - 非 `final` 类不应提供**无参构造**函数，防止子类误用
   - 建议提供带 `objectType` 参数的构造函数
   - 推荐提供静态 `newModel()` 方法来创建实例

3. **实例创建最佳实践**：
   - 使用静态 `newModel()` 方法创建对象
   - 可以将业务键 `BusinessKey` 作为 `newModel()` 的参数，如 `newModel(@NonNull String code, @NonNull String revId)`
   - 子类应该实现自己的 `newModel()` 静态方法
:::

1. 定义文件夹类

```java
@PersistentEntity(schema = Schema.DOCUMENT, name = "Folder")
@LocalizedNameDesc(name = "文件夹")
@Index(fields = {"name"})
public final class Folder extends AbstractModelObject {
    @AssociateRelation
    @LocalizedNameDesc(name = "内容", description = "文件夹下面的内容")
    private List<ModelObject> content = new ArrayList<>();

    // 由于是 final 类，可以提供默认无参构造
    public Folder() {
        super(Folder.class.getName());
    }
}
```

2. 定义文档类

```java
@PersistentEntity(schema = Schema.DOCUMENT, name = "Document")
@LocalizedNameDesc(name = "文档", description = "单个文件的定义")
public class Document extends AbstractModelObject {
    @QuerySqlField(notNull = true)
    private Long fileSize;

    @QuerySqlField(notNull = true)
    @LocalizedNameDesc(name = "文件类型")
    private String fileType;

    @QuerySqlField(notNull = true)
    @LocalizedNameDesc(name = "存储路径")
    private String path;

	//可扩展的枚举类型，使用@DynamicEnum
	@DynamicEnum
	@QuerySqlField
	private String documentType;
	
    // 非 final 类，只提供带 objectType 的构造
    protected Document(String objectType) {
        super(objectType);
    }

    // 推荐的创建方法
    public static Document newModel() {
        return new Document(Document.class.getName());
    }
}
```

:::info 枚举值定义
`documentType`具体有哪些枚举值需要使用[dsl定义](#_2-定义可扩展枚举)
:::

3. 定义ItemRevision

```java
@PersistentEntity(schema = Schema.COMMON, name = "ItemRevision")
@LocalizedNameDesc(name = "版本对象", description = "能追溯的带版本的对象")
@BusinessKeys({
        @BusinessKeys.BusinessKey({"code", "revId"})
})
public class ItemRevision extends AbstractModelObject implements Revisionable {

    public static final String ATTR_CODE = "code";
    public static final String ATTR_REVISION_ID = "revId";

    @QuerySqlField(index = true, inlineSize = 18, notNull = true)
    @FullTextSearchField
    @LocalizedNameDesc(name = "编码")
    private String code;

    @QuerySqlField(notNull = true)
    @LocalizedNameDesc(name = "版本号")
    private String revId;

    public ItemRevision(String objectType) {
        super(objectType);
    }

	// 推荐的创建方法，带BusinessKey参数创建
    public static ItemRevision newModel(@NonNull String code, @NonNull String revId) {
        ItemRevision revision = new ItemRevision(ItemRevision.class.getName());
        revision.code = code;
        revision.revId = revId;
        return revision;
    }
}
```
4. 定义TypeDefinition
```java
@PersistentEntity(schema = Schema.EMOPSYS, name = "TypeDefinition")
@LocalizedNameDesc(name = "类型定义", description = "普通的类型或者视图类型的元数据定义")
public class TypeDefinition extends AbstractModelObject {
    
    // 复杂的属性定义映射，使用二进制存储提升性能
    @QuerySqlField
    @KeepBinary
    @LocalizedNameDesc(name = "属性定义", description = "类型的所有属性定义")
    private Map<String, AttributeDefinition> attributes = new LinkedHashMap<>();
    
    // 复杂的方法定义映射，使用二进制存储
    @QuerySqlField
    @KeepBinary
    @LocalizedNameDesc(name = "方法定义", description = "类型的所有方法定义")
    private Map<String, MethodDefinition> methods = new LinkedHashMap<>(0);
}
```

ℹ️关键注解说明
>- @PersistentEntity：指定存储架构和表名
>- @LocalizedNameDesc：提供多语言支持，支持名称和描述的国际化
>- @QuerySqlField：定义数据库字段，支持索引、排序、非空约束等配置
>- @AssociateRelation：定义对象间的关联关系，支持多对多关系，使用COMMON.Relation表存储
>- @StructuralRelation：定义对象间的结构关系，支持一对一和一对多关系，使用外键字段存储
>- @KeepBinary：标记字段使用二进制序列化存储，不会序列化为 JSONB，提供更好的性能但需要注意类稳定性，且 SQL 无法根据内容字段进行过滤
>- @DynamicEnum：标记属性为可扩展枚举类型，支持自定义value-label键值对
>- @Computed：标记计算属性，通过Groovy表达式动态计算生成，不持久化到数据库
>- @UserCreatable：标记对象类型可在UI界面中由用户直接创建，用于类型选择器
>- @FullTextSearchField：标记字段需要被全文检索，支持设置搜索权重因子
>- @GraphNode：标记实体类作为图节点进行同步到图数据库
>- @Index/@Indexes：定义单个或多个数据库索引，支持复合索引和唯一索引
>- @BusinessKeys：声明业务唯一键组合，用于定义业务层面的唯一性约束，同时会自动建立唯一索引
>- @Cacheable：配置对象级别的缓存策略，包括预加载关系和版本对象

`@KeepBinary` 注解适用于以下场景：

1. **复杂的嵌套对象**：JSON 反序列化容易出错的复杂对象结构
2. **包含泛型的复杂集合**：如 `List<CustomObject>`、`Map<String, ComplexType>`
3. **需要保持对象完整性的场景**：确保序列化和反序列化的一致性
4. **性能敏感的场景**：频繁读写且数据结构复杂的属性

:::warning ⚠️使用注意事项
- 使用 `@KeepBinary` 的字段无法在 SQL 查询中根据内容进行过滤
- 必须确保被序列化的类结构稳定，避免版本不兼容问题
- 二进制数据在数据库中不可读，调试时需要通过应用程序查看
- 建议与 `@QuerySqlField` 一起使用以确保字段持久化
:::
## 通过 DSL 定义模型

### 1. 定义类型

```javascript
create type document.Document extends AbstractModelObject {
    attribute type: String {
        required: true
    }
    attribute code: String {
        required: true
    }
    -> File[] as content
    schema: DOCUMENT
    tableName: Document
    multilang {
        name.zh_CN: "文档集合"
        name.en_US: "Document"
        description.zh_CN: "一系列的相关文档的集合"
    }
}
```

### 2. 定义可扩展枚举

```javascript
create type material.HanslaserMaterial extends Material {
    attribute status: String {
        valueDomain: enum
        multilang {
            name.zh_CN: "状态"
            name.en_US: "Status"
        }
    }
}

// 创建枚举值
create object ValueDomainData {
    attributePath: "material.HanslaserMaterial.status"
    domainType: "ENUM"
    value: "active"
    name.zh_CN: "活跃"
    name.en_US: "Active"
}

// 还可以往枚举值对象中附加更多的任意的属性值，例如活跃的图标是什么
create object ValueDomainData {
    attributePath: "material.HanslaserMaterial.status"
    domainType: "ENUM"
	icon: 'active.svg'
    value: "active"
    name.zh_CN: "活跃"
    name.en_US: "Active"
}
```

### 3. 定义级联属性

```javascript
create type sample.Address extends GenericModelObject {
    attribute province: String {
        valueDomain: cascade(province, city)
    }
    attribute city: String {
        valueDomain: cascade(province, city)
    }
    schema: SAMPLE
    tableName: ADDRESS
}
```


## 视图定义（实现中）

View（视图）是 EMOP 中的只读视图类型，用于提供基于 SQL 查询的数据视图能力。与与普通的 ModelObject 不同，View 不需要定义 `tableName`，而是通过 SQL 查询动态生成数据。

### 视图特点

1. **只读特性**
   - View 类型的对象只能查询，不能进行创建、修改、删除操作
   - 数据来源于底层的 SQL 查询结果

2. **自动权限应用**
   - 自动应用 EMOP 中定义的原表当中的 RLS（Row Level Security）权限规则
   - 用户只能看到有权限访问的数据行

3. **字段自动映射**
   - SQL 查询结果自动映射到 View 的属性定义
   - 支持复杂的 SQL 查询，包括多表关联、聚合计算等

4. **API 一致性**
   - View 的查询 API 与 ModelObject 完全一致
   - 可以使用相同的查询语法和过滤条件
   - 支持分页、排序等标准查询功能

### Java 注解方式定义视图

```java
@PersistentEntity(schema = Schema.SAMPLE, name = "MyTaskView")
@LocalizedNameDesc(name = "我的任务视图", description = "当前用户有权限的任务列表")
@ViewDefinition(sql = """
    SELECT t.id, t.name, t.status, t.assignee, t.create_time
    FROM SAMPLE.TASK t
    WHERE t.assignee = :currentUserId
    """)
public final class MyTaskView extends AbstractModelObject {
    
    @QuerySqlField
    @LocalizedNameDesc(name = "任务名称")
    private String name;
    
    @QuerySqlField
    @LocalizedNameDesc(name = "状态")
    private String status;
    
    @QuerySqlField
    @LocalizedNameDesc(name = "负责人")
    private Long assignee;
    
    @QuerySqlField
    @LocalizedNameDesc(name = "创建时间")
    private Date createTime;
    
    public MyTaskView() {
        super(MyTaskView.class.getName());
    }
}
```

### DSL 方式定义视图

```javascript
create view sample.ProjectStatView extends AbstractModelObject {
    attribute projectCode: String {
        persistent: true
    }
    attribute projectName: String {
        persistent: true
    }
    attribute totalTasks: Integer {
        persistent: true
    }
    attribute completedTasks: Integer {
        persistent: true
    }
    attribute progress: Double {
        persistent: true
    }
    
    schema: SAMPLE
    sql: """
        SELECT 
            p.code as projectCode,
            p.name as projectName,
            COUNT(t.id) as totalTasks,
            COUNT(CASE WHEN t.status = 'COMPLETED' THEN 1 END) as completedTasks,
            CAST(COUNT(CASE WHEN t.status = 'COMPLETED' THEN 1 END) AS DOUBLE) / 
                NULLIF(COUNT(t.id), 0) * 100 as progress
        FROM SAMPLE.PROJECT p
        LEFT JOIN SAMPLE.TASK t ON t.project_id = p.id
        GROUP BY p.code, p.name
    """
    
    multilang {
        name.zh_CN: "项目统计视图"
        name.en_US: "Project Statistics View"
        description.zh_CN: "项目任务完成情况统计"
    }
}
```

### 视图查询示例

```java
// 查询方式与普通 ModelObject 完全一致
List<MyTaskView> myTasks = Q.result(MyTaskView.class)
    .where("status = ?", "进行中")
    .orderBy("createTime desc")
    .query();

// 带参数的视图查询
Map<String, Object> params = Map.of("currentUserId", currentUser.getId());
List<MyTaskView> tasks = Q.result(MyTaskView.class)
    .withParams(params)
    .query();

// 分页查询
Page<ProjectStatView> page = Q.result(ProjectStatView.class)
    .where("progress < ?", 80.0)
    .page(1, 20)
    .query();
```

### 应用场景

1. **权限过滤视图**
   - 创建"我的任务"视图，自动过滤出当前用户有权限的任务
   - 创建"部门文档"视图，只显示用户所在部门的文档

2. **聚合统计视图**
   - 创建"项目统计"视图，汇总项目的进度、成本等信息
   - 创建"物料库存"视图，实时计算物料的可用库存

3. **多表关联视图**
   - 创建"BOM成本"视图，关联物料、供应商、价格等多表数据
   - 创建"产品全景"视图，整合产品、图纸、BOM、工艺等信息

4. **历史数据视图**
   - 创建"变更历史"视图，展示对象的历史版本和变更记录
   - 创建"审批记录"视图，追溯审批流程的完整历史

### 注意事项

::: warning ⚠️使用限制
1. View 目前还在开发中，部分功能可能尚未完全实现
2. View 不支持写操作（创建、修改、删除）
3. View 不需要定义 `tableName`
4. View 的 SQL 中可以使用 `:paramName` 形式的参数占位符
5. View 的性能取决于底层 SQL 查询的复杂度，建议合理使用索引
:::

## 关系定义

### 1. 结构关系定义

适用于父子关系固定的场景，如 BOM 结构。结构关系通过显式定义的外键字段直接存储，具有以下特点：

- 表达对象之间的强从属关系，子对象依赖于父对象
- 关系是对象定义的固定组成部分
- 必须显式指定用于存储关系的外键字段（`Long`类型且必须加上`@QuerySqlField`注解）
- 查询性能好，适合频繁访问的关系

::: warning ⚠️结构关系限制
- 仅支持一对一和一对多关系，不支持多对多关系
- 外键目标对象的存在性由应用层负责校验和处理
- 级联删除和级联更新的业务逻辑需要在应用层实现
- 如果需要根据外键查询，需要应用层手动在`foreignKey`上建立索引
:::

外键规则：
- 对于单个引用关系（一对一），外键字段可以定义在当前类或目标类中
- 对于集合引用关系（一对多），外键字段必须定义在目标类（集合元素类）中

使用注意：
- 通过`@StructuralRelation`标记的关系属性是只读的，只能用于懒加载数据
- 要建立或修改对象之间的关系，必须显式设置对应的外键字段值
- Getter方法**不会**自动触发懒加载，需要调用`ModelObject.get("fieldName")`方法
- 当使用泛型类型或基类作为字段类型时，必须通过`foreignKeyTargetClz`指定具体的目标类型

Java构建样例
```java
// BOM结构示例（一对多关系，外键在子项中）
@StructuralRelation(foreignKeyField = "parentId")
@LocalizedNameDesc(name = "子项", description = "子BOM行")
private List<BomLine> children;

// 产品关联默认BOM（一对一关系，外键在当前类）
@StructuralRelation(foreignKeyField = "defaultBomId")
@LocalizedNameDesc(name = "默认BOM", description = "产品的默认BOM结构")
private BomHeader defaultBom;

// 用户档案关系（一对一关系，外键在目标类）
@StructuralRelation(foreignKeyField = "userId")
private UserProfile profile;

// 使用基类或泛型类型时，需要指定具体目标类
@StructuralRelation(foreignKeyField = "userId", foreignKeyTargetClz = UserProfile.class)
private ModelObject profile;
```

DSL构建样例
```java
// 1. 结构关系(一对多)示例
create type BomLine extends GenericModelObject {
    attribute parentId: Long
	-> BomLine[] as children {  // 定义一对多的结构关系
        foreignKey: parentId    // 指定外键字段
    }
    schema: BOM
    tableName: BOM_LINE
}

// 2. 结构关系(一对一)示例 
create type UserProfile extends GenericModelObject {
    schema: USER
    tableName: USER_PROFILE
}

create type User extends GenericModelObject {
	attribute userProfileId: Long
    -> UserProfile as profile {  // 定义一对一的结构关系
        foreignKey: userProfileId      // 指定外键字段
    }
    schema: USER
    tableName: USER
}
```

### 2. 关联关系定义

适用于对象之间的动态关联场景，通过`COMMON.Relation`表存储。具有以下特点：

- 双方都是独立的业务实体
- 具有独立的生命周期
- 关系可以动态建立和解除
- 支持在关系上定义任意key-value属性
- 支持一对多和多对多关系

使用注意：
- 关系属性默认在保存时不会自动处理关系
- 如果要平台自动处理关系，必须：
  1. 将`ModelObject`中的关系设置正确
  2. 调用`ModelObject.directiveHandleAssociateRelations()`方法
  3. 调用`ObjectService`相关的保存方法

Java构建样例
```java
// 文档标签关系示例
@AssociateRelation
@LocalizedNameDesc(name = "标签")
private List<Tag> tags;

// 选课关系示例（带成绩、选课时间等属性），关系属性元数据定义的注解支持稍后推出
@AssociateRelation
private List<Course> courses;

//构建关系时添加关系属性
S.service(AssociateRelationService.class).appendRelation(student, "choosed", Map.of("_time", new Date()), courses.toArray(Course[0]));

//获取关系上的属性
S.service(AssociateRelationService.class).findRelations(student, "choosed").forEach(rel -> System.out.println(rel.get("_time")));
```

DSL构建样例
```java
create type Document extends GenericModelObject {
    -> Tag[] as tags   // 定义多对多的关联关系
    schema: DOCUMENT
    tableName: DOCUMENT
}
```

### 3. 内嵌关系定义

适用于作为主对象属性存在的复杂对象：

```java
@QuerySqlField
private Parameters parameters;
```

### 4. 版本引用关系定义

适用于版本对象的引用关系，可根据版本规则`RevisionRule`进行动态匹配具体版本对象，典型场景即BOM依据版本规则动态组装具体版本的对象，形成对应的BOM树。
该引用通过[RevisionableRefTrait](trait#_3-revisionablereftrait-可修订对象引用)来实现

```java
public class BomLine extends AbstractModelObject {
    private final RevisionableRefTrait<BomLine> reference;
    
    public BomLine() {
        super(BomLine.class.getName());
        this.reference = new RevisionableRefTraitImpl<>(this);
    }
}
```

::: info ℹ️最佳实践
- 关系的默认的Getter方法不会触发懒加载，防止在序列化的时候触发非必要数据的序列化
- 对于关联的对象需要序列化时，请手工触发懒加载后会自动缓存至属性值并在序列化的时候顺利下发
- 以属性名作为方法名的方法，默认触发懒加载，例如
```java

@AssociateRelation
private List<Course> courses;

//不会触发懒加载
public List<Course> getCourses(){
    return this.courses;
}

//触发懒加载, 同时自动赋值至courses
public List<Course> courses(){
    return get("courses");
}
```
:::
## 值域定义

### 1. 可扩展枚举

```javascript
// DSL 定义
attribute status: String {
    valueDomain: enum
}

// Java 注解定义
@DynamicEnum
private String status;
```

### 2. 级联数据

```javascript
// DSL 定义
attribute province: String {
    valueDomain: cascade(province, city)
}
attribute city: String {
    valueDomain: cascade(province, city)
}

//暂不支持Java注解直接定义，需要在系统根据Java生成TypeDefinition(元数据)后再使用DSL更新定义或直接Java API更新元数据
```

### 3. 计算属性

#### Java注解方式
```java
public class BOMLine extends AbstractModelObject {
	//modelObject为内置引用，指向BOMLine的实例
    @Computed("sum(modelObject.children.cost)")
    private Double totalCost;

    @Computed("modelObject.children.count()")
    private Integer childCount;
	
	//revisionRule及isPrecise为上下文引用，由SampleComputedContextProvider提供
	@Computed(
        value = "revisionRule.apply(modelObject, isPrecise)",
        contextProvider = SampleComputedContextProvider.class
    )
	private ModelObject target;
}

// 定义BOM专用的上下文提供者
public class SampleComputedContextProvider implements ComputedContextProvider {
    @Override
    public Map<String, Object> getContext(ModelObject modelObject, String propertyName) {
        // 从当前用户会话、系统配置等获取上下文
        RevisionRule rule = UserContext.getCurrentRevisionRule();
        Boolean isPrecise = UserContext.isPrecise();
        
        return Map.of(
            "revisionRule", rule,
            "isPrecise", isPrecise
        );
    }
}
```
#### DSL方式
⚠️未实现
```java
create type bom.BOMLine extends AbstractModelObject {
    attribute totalCost: Double {
        valueDomain: computed
        expression: "sum(modelObject.children.cost)" 
    }
    
    attribute childCount: Integer {
        valueDomain: computed
        expression: "modelObject.children.count()"
    }
}
```

#### 获取计算值
```java
//无上下文直接获取
bomline.get("childCount")

//上下文由metadata定义的class获取，例如从SampleComputedContextProvider
bomline.get("target")

//上下文由get方法显示地传递
bomline.get("target", Map.of("revisionRule",new LatestWorkingRule(), "isPrecise", true))
```
计算属性的特点：

- 通过 `@Computed` 注解或 DSL 中的 computed 值域定义
- 计算属性不会持久化到数据库，计算属性具有内存实例级别的缓存机制: 在同一个对象实例中,首次访问计算属性时会执行计算并缓存结果,后续访问时将直接返回缓存值而不会重新计算。但如果是同一条记录的不同对象实例,则会重新执行计算
- 不能与其他域注解（如 `@DynamicEnum`、`@AssociateRelation`、`@StructuralRelation`）同时使用
- 支持 Groovy 表达式或脚本，内置`modelObject`引用，即计算属性所在的对象实例，同时也可以通过`bomline.get("target", context)`或`ComputedContextProvider`传递上下文给 Groovy

## 最佳实践

1. **选择定义方式**
   - 使用 Java 注解：
     - 基础模型定义
     - 需要强类型检查
     - 复杂的业务逻辑
   - 使用 DSL：
     - 动态模型扩展
     - 运行时配置
     - 快速原型开发

2. **模型组织**
   - 按业务域划分 Schema
   - 遵循单一职责原则
   - 合理使用继承关系

3. **关系选择**
   - 结构关系：固定的父子关系
   - 关联关系：动态关联的对象
   - 内嵌关系：不独立存在的对象

4. **性能考虑**
   - 合理使用索引
   - 避免过度使用动态属性
   - 注意关联关系的查询性能
   - 对于复杂对象（如包含泛型的集合、复杂嵌套对象）优先考虑使用 `@KeepBinary` 提升序列化性能
   - 使用 `@KeepBinary` 时需要确保类结构的向后兼容性，避免频繁修改对象结构

5. **扩展性设计**
   - 使用接口而不是具体类
   - 合理使用多态