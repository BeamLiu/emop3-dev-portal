# DSL 语法参考

## 概述

EMOP DSL（领域特定语言）是一个强大的声明式语言，用于定义和操作EMOP平台中的业务模型和数据。本参考手册详细说明了DSL的语法规则和使用方法。

### DSL 操作一览表

| 操作类型 | 操作指令 | 说明 | 使用场景 |
|---------|---------|------|----------|
| 元数据操作 | create type | 创建类型定义 | 定义新的业务模型 |
|  | update type | 更新类型定义 | 修改现有模型的结构 |
|  | delete type | 删除类型定义 | 移除不需要的模型 |
|  | show type | 查看类型定义 | 查看模型结构 |
| 对象操作 | create object | 创建对象实例 | 创建业务数据 |
|  | update object | 更新对象属性 | 修改业务数据 |
|  | delete object | 删除对象实例 | 移除业务数据 |
|  | delete tree | 删除树形结构 | 删除层级数据 |
|  | show object | 查看对象实例 | 查询业务数据 |
| 关系操作 | relation -> | 创建关系 | 建立对象间关联(暂时只支持关联关系) |
|  | remove relation | 删除关系 | 解除对象间关联(暂时只支持关联关系) |
| 数据导入 | import | 导入表格数据 | 批量导入业务数据 |
|  | import tree | 导入树形数据 | 导入层级结构数据 |
| 命令操作 | execute script | 执行脚本 | 执行自定义逻辑 |
|  | execute start-workflow | 发起流程 | 启动业务流程 |
|  | execute approve-workflow | 审批流程 | 处理审批任务 |
|  | execute reject-workflow | 拒绝流程 | 处理审批任务 |
|  | execute abort-workflow | 终止流程 | 中止业务流程 |
|  | execute state-change | 变更状态 | 修改对象生命周期状态 |
## 基本语法规则

### 注释
```javascript
// 单行注释使用双斜线
# 也可以使用井号作为单行注释
```

### 标识符
- 基本标识符：由字母、数字和下划线组成，必须以字母或下划线开头
- 转义标识符：使用反引号(\`)包围，可以包含关键字或特殊字符
```javascript
name    // 普通标识符
`type`  // 转义后的关键字
```

### 字符串
```javascript
"普通字符串"           // 双引号字符串
'也可以使用单引号'      // 单引号字符串
"""
多行字符串
支持换行
"""                  // 三引号字符串
```

### 数据类型
- 字符串：`"text"` 或 `'text'`
- 数字：`123`, `-456`, `3.14`
- 布尔值：`true`, `false`
- 空值：`null`
- 数组：`["value1", "value2"]`

## 元数据操作

### 创建类型
用于定义新的业务模型类型。

```javascript
// 基本类型创建
create type com.example.Customer extends AbstractModelObject {
    // 属性定义
    attribute name: String {
        required: true
        persistent: true
        description: "客户姓名"
    }
    
    // 枚举值域
    attribute status: String {
        valueDomain: enum
        multilang {
            name.zh_CN: "状态"
            name.en_US: "Status"
        }
    }
    
    // 级联值域
    attribute province: String {
        valueDomain: cascade(province, city)
    }
    attribute city: String {
        valueDomain: cascade(province, city)
    }
    
    // 结构关系定义
    // 1. 一对多结构关系, 外键在子对象(Line)中
    attribute parentId: Long     // Line中定义外键字段
    -> Line[] as children {
        foreignKey: parentId     // 指定外键字段名
    }
    
    // 2. 一对一结构关系, 外键在当前对象
    attribute profileId: Long    // 当前对象中定义外键
    -> UserProfile as profile {  // 指定关系
        foreignKey: profileId
    }
    
    // 关联关系定义
    // 1. 一对多关联
    -> Document[] as attachments  // 通过关系表存储
    
    // 2. 多对多关联, 支持关系属性
    -> Tag[] as tags
    
    // 3. 内嵌关系定义 
    // 作为属性直接内嵌,暂时未支持dsl,直接在Java中定义复杂对象后针对属性标记 @SqlQueryField
    
    // 业务唯一键
    businessUniqueKeys: [
        orderNumber,           // 单字段唯一键
        (year, sequenceNumber) // 复合唯一键
    ]
	
	// 定义全文检索字段，可指定权重
    fullTextSearchFields: [
        title(boost=3.0),  // 标题有最高权重
        content,           // 默认权重1.0
        summary(boost=2.0) // 摘要有中等权重
    ]
    
    // 基本配置
    schema: COMMON
    tableName: T_CUSTOMER
    icon: "customer.svg"
    
    // 多语言配置
    multilang {
        name.zh_CN: "客户"
        name.en_US: "Customer"
        description.zh_CN: "客户信息"
        description.en_US: "Customer Information"
    }
}

// 带存在性检查的类型创建
create type com.example.Customer extends AbstractModelObject {
    attribute name: String {
        required: true
        description: "客户姓名"
    }
    attribute status: String {
        valueDomain: enum
    }
    schema: COMMON
    tableName: T_CUSTOMER
    multilang {
        name.zh_CN: "客户"
        name.en_US: "Customer"
    }
    businessUniqueKeys: [
        orderNumber, 
        (year, sequenceNumber)
    ]
} if not exists
```

#### if not exists 语法说明

- **语法格式**: `create type TypeName extends ParentType { ... } if not exists`
- **功能**: 在创建类型前检查是否已存在同名类型，如果存在则跳过创建，否则正常创建
- **使用场景**:
   - 初始化脚本：避免重复创建基础类型定义
   - 多环境部署：确保类型定义的一致性
   - 模块化开发：避免类型定义冲突

::: info ℹ️提示
- 如果不使用 `if not exists`，当类型已存在时会抛出异常
- 存在性检查基于完整的类型名称（包括包名）
- 建议在系统初始化和部署脚本中使用此功能
  :::


#### 属性参数说明
- `required`: 是否必填
- `persistent`: 是否持久化
- `description`: 属性描述
- `defaultValue`: 默认值
- `valueDomain`: 值域定义，支持 enum（枚举）和 cascade（级联）

### 更新类型
用于修改现有类型的定义。

```javascript
update type Customer {
    // 添加新属性
    attribute age: Integer {
        description: "客户年龄"
    }
	
	//添加或更新关联关系
	-> Document[] as attachments 
    
	//添加或更新结构关系
    -> Line[] as children {
        foreignKey: parentId     // 指定外键字段名
    }
	
	// 更新全文检索字段，可指定权重
    fullTextSearchFields: [
        title(boost=3.0),  // 标题有最高权重
        content            // 默认权重1.0
    ]
	
    // 更新多语言定义
    multilang {
        description.zh_CN: "更新后的客户信息"
        description.en_US: "Updated Customer Information"
    }
    
    // 更新属性的多语言定义
    attribute status: String {
        multilang {
            name.zh_CN: "状态"
            name.en_US: "Status"
        }
    }
}
```

### 删除类型
```javascript
delete type com.example.Customer
```
::: warning ⚠️注意
删除类型时，必须确保该类型对应的数据表中没有任何数据记录。如果表中还存在数据，删除操作将会失败。这是为了维护数据完整性，防止误删除包含有效业务数据的类型定义。

建议的删除步骤：
1. 使用 `show type` 确认要删除的类型名称
2. 检查并删除该类型的所有数据记录
3. 执行类型删除操作
   :::

### 查看类型定义
```javascript
show type com.example.Customer
```

## 对象操作

### 创建对象
```javascript
// 基本对象创建
create object Customer {
    name: "张三",
    email: "zhangsan@example.com",
    age: 30,
    code: "CUS-001",
    revId: "A"
}

// 带存在性检查的对象创建
create object Customer {
    name: "张三",
    email: "zhangsan@example.com",
    age: 30,
    code: "CUS-001",
    revId: "A"
} if not exists (code='CUS-001')

// 复杂条件的存在性检查
create object Customer {
    name: "李四",
    email: "lisi@example.com",
    code: "CUS-002",
    revId: "A"
} if not exists (code='CUS-002' and revId='A')

// 创建带关系的对象
create object Document {
    title: "项目计划",
    // 通过ID引用
    -> User(101) as author,
    // 通过条件引用
    -> User(email="john@example.com" and department="IT") as reviewers,
    // 多个关系对象
    -> [
       Attachment(201),
       Attachment(202)
    ] as attachments,
    // 反向关系
    <- Department(id=301) as managedBy
}
```

#### if not exists 语法说明

- **语法格式**: `create object TypeName { ... } if not exists (condition)`
- **功能**: 在创建对象前检查是否已存在满足条件的对象，如果存在则跳过创建，否则正常创建
- **条件语法**: 条件部分支持与 `where` 子句相同的语法，包括：
   - 简单条件：`code='CUS-001'`
   - 复合条件：`code='CUS-001' and revId='A'`
   - 比较操作：`=`, `!=`, `>`, `<`, `>=`, `<=`, `like`
   - 逻辑操作：`and`, `or`
   - IN 操作：`status in ('Active', 'Pending')`

- **使用场景**:
   - 初始化脚本：避免重复创建基础数据
   - 数据导入：防止重复记录
   - 多环境部署：确保数据的一致性

::: info ℹ️提示
- 如果不使用 `if not exists`，DSL 会按照正常逻辑创建对象（可能产生重复记录）
- 存在性检查基于指定的条件，而不是对象的所有属性
- 建议在条件中使用业务唯一标识符（如 code、name 等）
  :::

### 更新对象
```javascript
// 基本更新
update object Customer where id = 123 {
    age: 32
}

// 更新多语言属性
update object Customer where code = "CUS-001" {
    name.zh_CN: "张三",
    name.en_US: "Zhang San"
}
```

### 删除对象

::: warning ⚠️安全提示
为了防止误删除数据，删除操作必须包含 where 条件语句。DSL不允许无条件删除所有数据。
:::

```javascript
// 基本删除 - 必须带where条件
delete object Customer where status = 'Inactive'
delete object Part where code like 'TEST-%'
delete object Document where createDate < '2024-01-01'

// 不允许的删除操作
delete object Customer  // ❌ 错误：缺少where条件

// 树形结构删除 - 同样需要where条件
delete tree BOM where id=1001 with {
    maxDepth: 3,
    relations: [CONTAINS, REFERENCES]
}

// 注意where条件后面的内容需要符合SQL规范，同时使用单引号，不是双引号
delete object Part where code like "TEST-%"  // ❌ 错误：应该使用单引号
```

常见的删除条件：
- 按ID删除：`where id = 123`
- 按状态删除：`where status = 'Inactive'`
- 按日期范围：`where createDate < '2024-01-01'`
- 按编码匹配：`where code like 'TEST-%'`
- 复合条件：`where status = 'Draft' and createDate < '2024-01-01'`

ℹ️建议
>在执行删除操作前：
>1. 先使用相同的条件进行查询（show object），确认要删除的数据范围
>2. 在测试环境验证删除语句
>3. 必要时先备份重要数据

### 查询对象
```javascript
// 通过ID查询
show object 123

// 指定属性显示（支持xpath语法）
show object Customer(123) as detail with {
    attributes: [
		'id',
		'name',
		'description',
        'department/name',    // 显示关联部门的名称
        'manager/email'       // 显示关联管理者的邮箱
    ]
}

// 查询特定属性
show object Customer(123).name


// 树形结构查询
show object BOM(id=1001) as tree with {
    maxDepth: 3,
    relations: [reference, children],
    attributes: ['target/name', code, _state]
}
```

## 关系操作

### 创建关系(暂时只支持关联关系)
```javascript
// 创建单个关系
relation Document(1001) -> User(101) as author

// 批量创建关系
relation Document(1001) {
    -> User(101) as author
    -> User(102) as reviewer
    -> [Document(201), Document(202)] as attachments
}

// 带属性的关系
relation Document(1001) -> User(102) as approver {
    approvedDate: "2024-01-01",
    comments: "Approved with minor changes"
}
```

### 删除关系(暂时只支持关联关系)
```javascript
// 删除特定关系
remove relation between Document(1001) and User(101)

// 删除特定类型的关系
remove relation reviewer from Document(1001)

// 删除所有关系
remove relation all from Document(1001)
```

## 数据导入
对应的数据文件需要在服务器端的Minio中，支持`csv`和`xlsx`格式
### 基本导入
```javascript
import Part "parts.csv" {
    // 基本列映射
    column "编码" -> code
    column "名称" -> name
    
    // 多重映射
    column "编号" -> [code, "target/code"]
    
    // 关系对象映射
    column "供应商代码" -> "supplier/code"
    column "联系人手机" -> "supplier/contacts/phone"
} with {
    existence: skip,           // 存在性处理：skip, update, error
    allowXPathAutoCreate: true // 自动创建路径上的对象
}
```

### 树形数据导入
```javascript
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

### 数据导入配置选项

#### existence 配置
控制如何处理已存在的记录：
- `skip`: 跳过已存在记录
- `update`: 更新已存在记录
- `error`: 遇到已存在记录时报错

#### typeResolver
用于动态决定目标对象类型：
```javascript
typeResolver: """
    if(row['类型'] == '工位') {
        return 'Station'
    } else {
        return 'Material'
    }
"""
```

#### dataMappingFunction
用于自定义数据转换逻辑：
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

## 命令操作

### 工作流命令
```javascript
// 发起流程
execute start-workflow Document(id=2001) with {
    template: "ReviewProcess",
    comment: "Please review this document"
}

// 审批流程
execute approve-workflow Task(id=3001) with {
    comment: "Approved after review"
}

// 拒绝流程
execute reject-workflow Task(id=3002) with {
    comment: "Need revision"
}

// 终止流程
execute abort-workflow Task(id=3003) with {
    comment: "Cancel due to requirement change"
}
```

### 状态变更命令

#### 常规状态变更
通过生命周期事件触发状态变更：

```javascript
execute state-change [
    Part(id=1001),
    Part(id=1002),
    Part(status='Draft')
] with {
    action: "Approve"
    comment: "Batch release for production"
}
```

#### 直接状态变更 (moveTo_xxx)

使用 `moveTo_xxx` 语法直接将对象移动到指定状态，绕过正常的状态转换规则：

```javascript
// 直接将渲染模型发布到 "Released" 状态
execute state-change [
    Render(name='io.emop.model.common.AbstractModelObject'),
    Render(name='io.emop.model.document.Folder'),
    Render(name='io.emop.model.document.Dataset'),
    Render(name='io.emop.model.document.Document'),
    Render(name='io.emop.model.bom.BomLine')
] with {
    action: "moveTo_Released"
    comment: "Directly move to Released state using bypass"
}
```

###### moveTo_xxx 语法说明

- 格式: `action: "moveTo_目标状态"`
- 功能: 直接将对象移动到指定状态，无需遵循正常的状态转换规则
- 使用场景:
   - 数据修复
   - 批量状态调整
   - 系统初始化
   - 特殊业务场景需要绕过常规流程

> ⚠️ **注意**: 直接状态变更会绕过常规的状态转换验证和业务规则检查，应谨慎使用。建议仅在管理员操作或特殊维护场景下使用此功能，此外，当前登录用户需要有`byPass`的权限。

### 脚本执行
```javascript
execute script """
    def processedCount = 0
    context.each { task ->
        if (task.isOverdue()) {
            task.escalate()
            task.addComment("Auto escalated due to overdue")
            processedCount++
        }
    }
    println "Processed ${processedCount} tasks"
""" with {
    context: Task(dueDate<CURRENT_DATE and status='Active')
}
```

## 最佳实践

1. **命名规范**
   - 类型名使用驼峰命名法（如 `CustomerOrder`）
   - 属性名使用小写驼峰命名法（如 `firstName`）
   - 关系名使用有意义的动词或名词（如 `approvedBy`, `documents`）

2. **值域使用**
   - 对固定选项使用枚举值域
   - 对层级数据使用级联值域
   - 合理使用多语言配置

3. **关系定义**
   - 明确关系的方向（`->` 或 `<-`）
   - 使用有意义的关系名
   - 适当使用关系属性存储额外信息

4. **数据导入**
   - 导入前验证数据格式
   - 使用合适的存在性处理策略
   - 必要时使用类型解析器和数据映射函数

5. **对象创建**
   - 在初始化脚本和数据导入场景中使用 `if not exists` 避免重复创建
   - 在存在性检查条件中使用业务唯一标识符
   - 合理设计条件以平衡性能和准确性

6. **性能考虑**
   - 批量操作时使用数组形式
   - 适当使用条件限制范围
   - 避免过深的树形结构操作

## 常见问题

1. **类型无法创建**
   - 检查类型名是否重复
   - 确认继承的父类型存在
   - 验证属性定义的类型是否正确

2. **关系创建失败**
   - 确认对象存在且状态正确
   - 检查关系类型的定义
   - 验证关系方向是否正确

3. **导入数据问题**
   - 检查CSV文件格式和编码
   - 确认列映射正确
   - 验证目标对象的约束条件

4. **命令执行失败**
   - 检查对象状态是否允许操作
   - 确认用户权限是否足够
   - 验证命令参数是否完整

5. **if not exists 相关问题**
   - 确认条件语法符合 SQL 规范
   - 检查条件中引用的字段是否存在
   - 验证字符串值使用单引号而非双引号

## 附录：关键字列表

- `type`
- `object`
- `create`
- `update`
- `delete`
- `import`
- `tree`
- `show`
- `where`
- `extends`
- `attribute`
- `schema`
- `tableName`
- `multilang`
- `relation`
- `execute`
- `script`
- `exists`