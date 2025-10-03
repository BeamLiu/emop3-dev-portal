
# XPath 数据操作

XPath 是 EMOP 中一个强大的数据访问和操作工具，它允许您使用路径表达式来导航和操作复杂的对象树结构。XPath 支持被集成在所有数据操作方式中，包括 Java API、DSL 和前端操作。

## 重要说明

**XPath 查询总是返回 List**：为了提供稳定的用户预期和一致的行为，所有 XPath 表达式的查询结果都会被包装在 List 中返回，即使只有一个匹配结果。这样设计的好处是：

- 用户不需要考虑返回的是单个对象还是多个对象
- 避免了类型转换的复杂性和错误
- 提供了一致的 API 体验

## 基本概念

### XPath 路径

EMOP 中的 XPath 路径是一个用斜杠（/）分隔的属性序列，可以包含条件表达式：

```
materials[*]                     // 所有材料
materials[@id>0]                // ID大于0的材料
materials[@code='P123'][*]/code  // 代码为P123的材料的所有子项的代码
//其他一些类似样例
materials[1]
materials[starts-with(code, 'M4')]
materials[name='Bolt M6' and revId='A']/name
count(materials[name='Bolt M6'])
materials[contains(name, 'Screw') or contains(name, 'Washer')]/code
materials[string-length(description) > 10]
```

### 路径类型

- **直接属性**：`code`
- **关系属性**：`materials`
- **条件过滤**：`materials[@type='RAW']`
- **通配符**：`materials[*]`
- **位置索引**：`materials[position()=2]`

## 使用方法

### Java API 中使用

从当前对象开始查找：

```java
// XPath 查询总是返回 List，确保稳定的预期
List<?> codes = product.get("materials[@id>0]/code");

// 获取集合
List<?> materials = product.get("materials[*]");

// 获取第一个匹配项 - 仍然返回 List，需要取第一个元素
List<?> materialList = product.get("materials[@id>0]");
ModelObject material = materialList.isEmpty() ? null : (ModelObject) materialList.get(0);

// 获取所有匹配项的特定属性
List<?> allCodes = product.get("materials[*]/code");

// 聚合函数返回单个值的 List
List<?> countList = product.get("count(materials)");
Integer count = countList.isEmpty() ? 0 : ((Number) countList.get(0)).intValue();
```

### 更新操作

```java
// 更新单个属性
product.set("materials[position()=2]/description", "Updated Description");

// 更新所有匹配项
product.set("materials/description", "Updated Description");

// 需要主动保存
S.service(ObjectService.class).save(product);
```

### 自动创建对象

使用 `ensureXPathTarget` 可以自动创建路径上不存在的对象：

```java
ModelObject info = product.ensureXPathTarget(
    "attributes/specs/material",
    XPathCreationContext.allowCreate((typeName, path) -> {
        if (typeName.equals("ProductSpecs")) {
            return Map.of("code", "SPEC-" + path.hashCode());
        }
        return Collections.emptyMap();
    })
);
```

### DSL 中使用

在 DSL 导入操作中使用 XPath：

```sql
import Part "parts.csv" {
    column "编码" -> [code, "target/code"]
    column "供应商代码" -> "supplier/code"
    column "联系人手机" -> "supplier/contacts[type='mobile']/number"
} with {
    allowXPathAutoCreate: true
}
```

## 常见模式

### 1. 层级数据访问

```java
// 访问多层级属性 - 返回 List
List<?> names = product.get("specs/material/supplier/name");
String name = names.isEmpty() ? null : (String) names.get(0);

// 访问特定索引 - 返回 List
List<?> specs = product.get("materials[1]/specifications");
```

### 2. 条件筛选

```java
// 单个条件 - 返回匹配的材料列表
List<?> rawMaterials = product.get("materials[@type='RAW']");

// 多个条件 - 返回匹配的材料列表
List<?> activeMaterials = product.get("materials[@type='RAW' and @status='ACTIVE']");

// 数值比较 - 返回匹配的材料列表
List<?> highQuantityMaterials = product.get("materials[@quantity>100]");
```

### 3. 集合操作

```java
// 获取所有子项 - 返回所有材料的列表
List<?> allMaterials = product.get("materials[*]");

// 获取特定范围 - 返回前两个材料的列表
List<?> firstTwoMaterials = product.get("materials[position()<3]");

// 获取最后一项 - 返回包含最后一个材料的列表
List<?> lastMaterialList = product.get("materials[last()]");
ModelObject lastMaterial = lastMaterialList.isEmpty() ? null : (ModelObject) lastMaterialList.get(0);
```

### 4. 关系导航

```java
// 正向关系 - 返回内容列表
List<?> contentList = document.get("attachments/content");

// 反向关系 - 返回管理者列表
List<?> managerList = document.get("reviewedBy/department/manager");
ModelObject manager = managerList.isEmpty() ? null : (ModelObject) managerList.get(0);
```

## 最佳实践

1. **性能考虑**
   - 使用具体的路径而不是通配符
   - 避免过深的路径导航
   - 对频繁访问的路径结果进行缓存

2. **自动创建**
   - 使用 `allowXPathAutoCreate` 时要明确类型解析规则
   - 提供必要的默认属性值
   - 处理可能的创建失败情况

3. **错误处理**
   - 检查路径是否存在
   - 处理可能的类型转换错误
   - 验证更新操作的结果

## 注意事项

1. **路径存在性**
   - 使用 `set` 操作时，路径必须存在
   - 使用 `ensureXPathTarget` 可以自动创建不存在的路径

2. **类型安全**
   - 确保路径上的对象类型匹配
   - 注意属性值的类型转换

3. **更新操作**
   - 更新后需要显式保存
   - 注意并发更新的问题

4. **性能影响**
   - 复杂的 XPath 表达式可能影响性能
   - 避免在循环中频繁使用长路径