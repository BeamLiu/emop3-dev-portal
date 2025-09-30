# DSL 数据操作

EMOP 提供了强大的 DSL（领域特定语言）来执行数据操作。DSL 提供了简洁直观的语法，特别适合系统实施人员使用。本文介绍 DSL 针对数据操作的基本语法和常见操作。

ℹ️提示
>有关 DSL 的完整语法和高级用法，请参考 [DSL指南](/business/dsl/guide)。


## 对象操作

### 创建对象

创建对象支持设置基本属性、多语言属性和关系：

```sql
// 基本对象创建
create object Customer {
    name: "John Doe",
    status: "Active",
    age: 30,
    code: "CUS-001",
    revId: "A"
}

// 创建带关系的对象
create object Document {
    title: "项目计划",
    // 通过ID引用
    -> User(101) as author,
    // 通过条件引用
    -> User(email="john@example.com") as reviewers,
    // 多个关系对象
    -> [
       Attachment(201),
       Attachment(202)
    ] as attachments
}
```

### 更新对象

更新对象支持条件匹配和多语言属性更新：

```sql
// 通过 ID 更新
update object Customer where id = 123 {
    status: "Inactive",
    name.zh_CN: "张三",
    name.en_US: "Zhang San"
}

// 通过条件更新
update object Customer where status = 'Active' {
    lastLoginTime: "2024-01-14"
}
```
::: warning ⚠️查询条件限制见[这里](../modeling/extension#_2-复用原表存储)
:::
### 删除对象

支持条件删除和树形删除：

```sql
// 条件删除
delete object Customer where status = 'Inactive'

// 删除树形结构
delete tree BOM where id = 1001 with {
    maxDepth: 3,
    relations: [CONTAINS, REFERENCES]
}
```
::: warning ⚠️查询条件限制见[这里](../modeling/extension#_2-复用原表存储)
:::
## 关系操作

### 创建关系

```sql
// 创建单个关系
relation Document(1001) -> User(101) as author

// 批量创建关系
relation Dataset(1001) {
    -> User(101) as author
    -> User(102) as reviewer
    -> [Document(201), Document(202)] as attachments
}

// 带属性的关系
relation Document(1001) -> User(102) as approver {
    approvedDate: "2024-01-01",
    comments: "Approved"
}
```

### 删除关系

```sql
// 删除特定关系
remove relation between Document(1001) and User(101)

// 删除特定类型的关系
remove relation reviewer from Document(1001)

// 删除所有关系
remove relation all from Document(1001)
```

## 数据导入

### 表格数据导入

```sql
import Part "parts.csv" {
    // 基本列映射
    column "编号" -> code
    column "名称" -> name
    
    // 多重映射
    column "编号" -> [code, "target/code"]
    
    // 关系对象映射
    column "供应商编号" -> "supplier/code"
    column "联系人电话" -> "supplier/contacts/phone"
} with {
    existence: skip,           // 存在性处理：skip, update, error
    allowXPathAutoCreate: true // 自动创建路径上的对象
}
```

### 树形数据导入

```sql
// 单键父项导入
import tree Assembly "bom.xlsx" {
    parent "父项编号" -> "编号"
    column "编号" -> code
    column "版本" -> revId
} with {
    existence: update,
    relationType: "children"
}

// 复合键父项导入
import tree Assembly "bom.xlsx" {
    parent ["父项编号", "父项版本"] -> ["编号", "版本"]
    column "编号" -> code
    column "版本" -> revId
}
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

### 类型解析器

在导入时可以使用 Groovy 脚本编写的类型解析器动态决定目标类型:

```sql
import Part "data.xlsx" {
    // 列映射配置
} with {
    typeResolver: """
        if(data['type'] == '工位') {
            return 'Station'
        } else if(data['type'] == '材料') {
            return 'Material' 
        }
        // 默认返回 Part 类型
        return 'Part'
    """
}
```
使用xpath实现多级嵌套对象创建样例
```sql
import Product "products.xlsx" {
    column "产品编号" -> code
    column "制造商编号" -> "manufacturer/code"
    column "制造商联系人" -> "manufacturer/contact/name"
    column "制造商电话" -> "manufacturer/contact/phone"
} with {
	allowXPathAutoCreate: true,
    typeResolver: """
        // xpath 示例:
        // - null: 根对象 (Product)
        // - "manufacturer": 制造商对象
        // - "manufacturer/contact": 联系人对象
        switch (xpath) {
            case "manufacturer":
                return "Manufacturer"
            case "manufacturer/contact":
                return "Contact"
            default:
                return "Product"
        }
    """
}
```
::: info ℹ️脚本内置变量
1. `data`: Map<String, Object> - 当前行映射后的数据
2. `xpath`: String - 当前处理的xpath路径(可能为null)
:::

### 数据映射函数

可以使用 Groovy 脚本编写的映射函数进行复杂的数据转换:

```sql
import Part "data.xlsx" {
    // 列映射配置
} with {
    dataMappingFunction: """
        // 示例:转换日期格式
        if(data.createDate) {
            data.createDate = new SimpleDateFormat("yyyy-MM-dd")
                .parse(data.createDate)
        }
        
        // 示例:设置固定值
        data.put("bomViewId", -118852310660227072L)
        
        // 必须返回转换后的数据Map
        return data
    """
}
```
::: info ℹ️脚本内置变量
1. `data`: Map<String, Object> - 当前行映射后的数据
2. `row`: Map<String, String> - 原始行数据
3. `context`: ImportContext - 导入上下文
4. `xpath`: String - 当前处理的xpath路径(可能为null)
:::

### 数据校验
ℹ️DSL暂时不支持，请使用[Java API](java-api.html#数据校验扩展点)

## 数据导出
TBD

## 注意事项

1. DSL 操作需要具有幂等性(业务层保障)，重复执行相同的操作会得到相同的结果

2. 导入操作中的几个重要选项：
   - `existence`: 处理已存在数据的方式（skip/update/error）
   - `allowXPathAutoCreate`: 是否自动创建 XPath 路径上的对象
   - `typeResolver`: 动态决定目标对象类型
   - `dataMappingFunction`: 自定义数据转换逻辑

3. 删除树形结构时：
   - 会自动断开当前树与外部对象的引用关系
   - 如果树节点被其他对象引用，需要先断开关联
   - 使用 maxDepth 控制删除深度

4. xpath路径说明：
   - 在数据导入时用于表示数据的层级位置
   - 使用正斜杠(/)作为层级分隔符，如 "manufacturer/contact/name"
   - 根据不同的xpath路径，可以在typeResolver中动态决定对象类型：
     ```sql
     typeResolver: """
         switch (xpath) {
             case "manufacturer":
                 return "Manufacturer"
             case "manufacturer/contact":
                 return "Contact"
             default:
                 return "Product"
         }
     """
     ```
   - 在dataMappingFunction中可根据xpath执行不同的数据转换逻辑：
     ```sql
     dataMappingFunction: """
         if (xpath == "author") {
             data.type = "Employee"
         } else if (xpath == "attachments") {
             data.category = "DOC"
         }
         return data
     """
     ```
   - xpath为null时表示正在处理根对象
