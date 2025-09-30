# 需求分析与设计

## 业务场景分析

### 1. 核心业务流程

1. **夹具库存管理**
   - 夹具入库登记
   - 分类管理
   - 状态变更
   - 库存盘点

2. **夹具使用流程**
   - 领用申请
   - 使用记录
   - 归还登记
   - 维护保养

### 2. 功能需求清单

| 功能模块 | 具体需求 | 技术要点 |
|---------|---------|---------|
| 分类管理 | - 树形分类结构<br>- 分类编码规则<br>- 分类属性定义 | - 继承AbstractModelObject<br>- BusinessCodeTrait<br>- HierarchicalTrait |
| 夹具管理 | - 基础信息维护<br>- 产品关联<br>- 版本控制 | - 继承ItemRevision（业务唯一键为code+revId）<br>- 关联关系处理<br>- 结构关系(分类) |
| 使用记录 | - 领用记录<br>- 归还记录<br>- 状态跟踪 | - 继承RuntimeObject<br>- 结构关系(夹具)<br>- 简单数据存储 |
| 产品管理 | - 基础信息维护<br>- 夹具关联<br>- 版本控制 | - 继承ItemRevision（业务唯一键为code+revId）<br>- 关联关系处理<br>- 自动编码规则 |

### 3. 数据需求分析

#### 3.1 数据规模
- 夹具种类：预计1000+
- 分类数量：100+
- 使用记录：每日新增100+

#### 3.2 性能需求
- 列表查询响应时间 < 100ms
- 树形数据加载时间 < 100ms
- 数据导入速度 > 300条/秒

## 技术方案设计

### 1. 领域模型设计

#### 1.1 模型关系图
```
[Category]<--(结构:categoryId)--[Fixture]<--(结构:fixtureId)--[UseRecord]
    ^                              |
    |                             |
(结构:parentId)           (关联:appliesTo)
    |                             |
    +--[Category]                 +--[Product]
```

#### 1.2 对象关系说明
- Category (分类): 树形结构，通过`parentId`关联，`code`作为业务唯一键
- Fixture (夹具): 属于某个`Category`，可关联多个`Product`，继承自`ItemRevision`使用`code` + `revId`作为业务唯一键
- UseRecord (使用记录): 通过`fixtureId`关联到具体的`Fixture`
- Product (产品): 可被多个`Fixture`关联，继承自`ItemRevision`使用`code` + `revId`作为业务唯一键


#### 1.3 Java模型定义

```java
@Getter
@Setter
@PersistentEntity(schema = Schema.SAMPLE, name = "Category")
@LocalizedNameDesc(name = "夹具分类", description = "夹具的分类定义")
@BusinessKeys({
        @BusinessKeys.BusinessKey({"code"})
})
public class Category extends AbstractModelObject {
    @QuerySqlField
    private String path;
    
	//树形结构能力
    private final HierarchicalTrait<Category> hierarchical;
	
	//自动编码能力
    private final BusinessCodeTrait<Category> businessCode;

	// 该分类下的夹具，categoryId位于Fixture中
    @StructuralRelation(foreignKeyField = "categoryId")
    private List<Fixture> fixtures;
	
    public Category(String objectType) {
        super(objectType);
        this.hierarchical = new HierarchicalTraitImpl<>(this);
        this.businessCode = new BusinessCodeTraitImpl<>(this);
    }

    public static Category newModel() {
        return new Category(Category.class.getName());
    }
}

@Getter
@Setter
@PersistentEntity(schema = Schema.SAMPLE, name = "Fixture")
@LocalizedNameDesc(name = "夹具", description = "工装夹具基本信息")
public class Fixture extends ItemRevision {
    @QuerySqlField
    private String status;
    
    @QuerySqlField
    private String specifications;
    
    @QuerySqlField
    private String location;
    
    @QuerySqlField(index = true)
    private Long categoryId;
    
    @StructuralRelation(foreignKeyField = "categoryId")
    private Category category;
    
    @AssociateRelation
    private List<Product> applicableProducts;

    public Fixture(String objectType) {
        super(objectType);
    }

    public static Fixture newModel() {
        return new Fixture(Fixture.class.getName());
    }
}

@Getter
@Setter
@PersistentEntity(schema = Schema.SAMPLE, name = "UseRecord")
@LocalizedNameDesc(name = "使用记录", description = "夹具使用记录")
public final class UseRecord extends SimpleModelObject {
    @QuerySqlField
    private Timestamp operateTime;

    @QuerySqlField
    private String operator;

    @QuerySqlField
    private String type;

    @QuerySqlField
    private String status;

    @QuerySqlField(index = true)
    private Long fixtureId;

    @StructuralRelation(foreignKeyField = "fixtureId")
    private Fixture fixture;

    public UseRecord() {
        super(UseRecord.class.getName());
    }
}

@Getter
@Setter
@PersistentEntity(schema = Schema.SAMPLE, name = "Product")
@LocalizedNameDesc(name = "产品", description = "夹具适用的产品信息")
public class Product extends ItemRevision {
    @QuerySqlField
    private String specifications;

    public Product(String objectType) {
        super(objectType);
    }

    public static Product newModel() {
        return new Product(Product.class.getName());
    }
}
```

### 2. 数据操作设计

#### 2.1 前端实现
```typescript
// 查询操作
const fixtures = await Q.from<Fixture>('Fixture')
  .field('status').equals('InStock')
  .field('createDate').desc()
  .page(0, 20)
  .queryPage();

// 创建操作
const fixture = await S.service(DataService).create('Fixture', {
  code: 'F001',
  name: '测试夹具',
  status: 'InStock'
});

// 更新操作
const updated = await S.service(DataService).update(fixtureId, {
  status: 'InUse'
});
```

#### 2.2 编码规则设计

##### 分类编码
```
CAT-${date(pattern="YY")}-${autoIncrease(scope="Rule",start="001",max="999")}
```
示例：CAT-25-101

##### 夹具编码
```
${attr(name="category.code")}-${autoIncrease(scope="PrePath",start="0001",max="9999")}
```
示例：CAT-25-001-1001

##### 产品编码
```
PROD-${date(pattern="YY")}-${autoIncrease(scope="Rule",start="100",max="999")}
```
示例：PROD-25-101

### 3. 接口设计

#### 3.1 REST API
所有接口基于平台通用的API，无需新建REST接口。

## 技术要点说明

### 1. 继承关系设计
- `Fixture`继承`ItemRevision`以获得版本管控能力
- `Category`继承`AbstractModelObject`以包含基本的id, name, description, _creator, _version等属性
- `UseRecord`继承自`SimpleModelObject`，只需要简单地往数据库中增加数据，不涉及数据的更新操作
- `Product`继承`ItemRevision`以获得编码、版本控制等基础能力

### 2. Trait定义
- 使用`BusinessCodeTrait` 支持分类和夹具的自动编码
- 使用`HierarchicalTrait` 支持分类的树形结构

### 3. 关系处理
- 分类的父子关系：结构关系(一对多)
- 夹具的分类关系：结构关系(一对多)
- 夹具的产品关联：关联关系(多对多)
- 夹具的使用记录：结构关系(一对多)

### 4. 数据操作
- 批量创建与更新
- 树形数据查询
- 关系数据处理
- 数据导入导出

## 开发步骤规划

1. **基础模型开发**
   - 创建基础模型类
   - 配置业务规则
   - 单元测试验证

2. **数据操作开发**
   - 实现基础CRUD
   - 开发树形操作
   - 实现关系处理

3. **业务逻辑开发**
   - 编码规则实现
   - 状态管理逻辑

4. **界面开发**
   - 基础组件开发
   - 业务页面实现
   - 交互逻辑处理

## 常见问题

1. **为什么使用结构关系而不是关联关系？**
   - 结构关系通过外键直接存储，查询性能更好
   - 适用于固定的父子关系场景
   - 不需要额外的关系表

2. **大数据量下如何优化性能？**
   - 合理使用索引
   - 采用分页查询
   - 优化树形结构查询

## 下一步

完成需求分析后，我们将开始进行编码工作。