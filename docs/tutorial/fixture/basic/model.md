# 工装夹具库管理系统 - Java模型实现指南

本文档将指导您完成工装夹具库管理系统的Java模型实现，包括环境准备、代码实现和验证步骤。

## 1. 环境准备

### 1.1 前置条件
确保您的开发环境满足以下要求：
- JDK 17+
- Maven 3.8+
- Git
- DBeaver (用于数据库验证)

### 1.2 创建开发分支
```bash
# 进入项目根目录
cd emop3

# 从master创建新分支，YourName替换为你的名字
git checkout -b {YourName}/feature/fixture-model master
```

### 1.3 编译项目
```bash
# 进入项目根目录
cd emop3

# 编译平台核心
cd emop-starters
mvn clean install

# 编译业务服务
cd ../emop-business
mvn clean install -DskipTests
```

## 2. 模型实现

IDE打开maven项目`emop-business`后，在`emop-business/emop-server-plugins/emop-server-plugin-api/src/main/java/io/emop/model/sample`目录下创建以下类：

### 2.1 夹具分类 (Category)
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
    
    // 树形结构能力
    private final HierarchicalTrait<Category> hierarchical;
    
    // 自动编码能力
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
```

### 2.2 夹具信息 (Fixture)
```java
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
```

### 2.3 使用记录 (UseRecord)
```java
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
```

### 2.3 产品 (Product)
```java
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

## 3. 启动服务

### 3.1 编译 plugin 内容
```bash
# 进入项目根目录
cd emop3

cd emop-business/emop-server-plugins
mvn compile
```

### 3.2 启动 EMOP Server
```bash
# 进入项目根目录
cd emop3

cd emop-business/emop-server-plugins
startEMOPServer.bat
```

## 4. 验证实现

### 4.1 REST API 验证
访问元数据定义：
```bash
//确认记录存在io.emop.model.sample.Category, io.emop.model.sample.Fixture, io.emop.model.sample.Product, io.emop.model.sample.UseRecord
curl http://localhost:870/webconsole/api/metadata/types

//获取io.emop.model.sample.Fixture的详细的元数据定义
curl http://localhost:870/webconsole/api/metadata/types/io.emop.model.sample.Fixture
```

### 4.2 DSL 验证
执行以下DSL查看各个类型的定义：
```bash
curl -X POST 'http://localhost:870/webconsole/api/dsl/execute' \
--header 'Content-Type: text/plain' \
--data-raw 'show type Category \
show type Fixture \
show type Product'
```

### 4.3 数据库结构验证

使用DBeaver连接Postgresql数据库：
> - 主库：`jdbc:postgresql://emop-db-master.emop.emopdata.com:5432/emop`
> - 用户名/密码：`emop/EmopIs2Fun!`

预期索引说明：

1. Category表索引：
   - `CATEGORY_ID_IDX`: ID主键
   - `CATEGORY_CODE_IDX`: 编码索引
   - `CATEGORY_PARENTID_IDX`: 父节点索引（树形结构）

2. Fixture表索引：
   - `FIXTURE_ID_IDX`: ID主键
   - `FIXTURE_CODE_IDX`: 编码索引
   - `FIXTURE_CATEGORYID_IDX`: 分类外键索引

3. Product表索引：
   - `PRODUCT_ID_IDX`: ID主键
   - `PRODUCT_CODE_IDX`: 编码索引

4. UseRecord表索引：
   - `USE_RECORD_ID_IDX`: ID主键
   - `USE_RECORD_FIXTUREID_IDX`: 夹具外键索引

注意：
- 所有表都自动创建主键索引（`*_ID_IDX`）
- 包含`BusinessCodeTrait`或继承自`ItemRevision`的实体类会自动创建编码索引（`*_CODE_IDX`）
- `StructuralRelation`及`HierarchicalTrait`的外键字段索引

### 4.3 业务唯一键验证

访问元数据定义API,确认`businessUniqueKeys`的配置:

1. 验证`Category`的`code`唯一键:
```bash
curl http://localhost:870/webconsole/api/metadata/types/Category

# 应该能看到以下businessUniqueKeys配置:
"businessUniqueKeys": [
    {
        "attributes": [
            "code"
        ]
    }
]
```

2. 验证`Fixture`的`code`+`revId`复合唯一键:
```bash
curl http://localhost:870/webconsole/api/metadata/types/Fixture

# 应该能看到以下businessUniqueKeys配置:
"businessUniqueKeys": [
    {
        "attributes": [
            "code",
            "revId"
        ]
    }
]
```

3. 验证`Product`的`code`+`revId`复合唯一键:
```bash
curl http://localhost:870/webconsole/api/metadata/types/Product

# 应该能看到以下businessUniqueKeys配置:
"businessUniqueKeys": [
    {
        "attributes": [
            "code",
            "revId"
        ]
    }
]
```

:::warning ⚠️提示
- 对于继承自`ItemRevision`的类(如`Fixture`和`Product`),会自动继承code+revId作为业务唯一键
- 业务唯一键会在数据插入和更新时自动进行校验
- 违反唯一键约束时会抛出`UniqueConstraintException`异常
:::

## 5. 注意事项

1. **表结构说明**
   - Category表包含树形结构所需的parentId字段
   - Fixture表继承自ItemRevision，包含版本控制相关字段
   - UseRecord表采用简单存储结构，适合大量记录的场景

2. **关系说明**
   - Category之间：树形结构关系（结构关系）
   - Fixture与Category：多对一结构关系
   - Fixture与Product：多对多关联关系
   - UseRecord与Fixture：多对一结构关系

3. **性能考虑**
   - 已在关键字段上建立索引

## 6. 后续步骤

完成模型实现后，您可以：
1. 实现数据访问层代码
2. 开发业务服务层
3. 实现前端界面
4. 编写单元测试
5. 进行性能测试和优化

## 7. 常见问题

1. **启动失败**
   - 确认JDK版本是否正确
   - 检查端口是否被占用

2. **表未创建**
   - 确认@PersistentEntity注解配置正确
   - 检查schema名称是否正确
   - 验证服务启动日志是否有错误

3. **索引未生成**
   - 确认@QuerySqlField注解使用正确
   - 检查字段名称是否符合规范