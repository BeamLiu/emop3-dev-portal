# 业务编码定义

## 简介

业务编码（Business Code）是平台中用于唯一标识业务对象的重要属性。通过 `BusinessCodeTrait` 和编码生成器服务`CodeGeneratorService`，我们可以实现灵活的自动编码功能。

## 编码规则说明

### 基本概念
编码规则通过在类型定义的元数据中设置 `codeGenPattern` 属性来定义。每个编码模式可以包含多个元素，这些元素在运行时会被实际值替换。

### 编码规则示例
让我们通过一些具体示例来理解编码规则：

#### 示例1：文档编码
```
DOC-${attr(name="category")}-${date(pattern="YYMM")}-${autoIncrease(scope="PrePath",start="0001",max="9999")}
```
可能生成的编码：`DOC-TECH-2401-0001`、`DOC-TECH-2401-0002`

这个规则的含义是：
- `DOC-`：固定前缀
- `${attr(name="category")}`：获取文档的category属性值（如：TECH）
- `${date(pattern="YYMM")}`：当前日期，格式为年月（如：2401）
- `${autoIncrease(scope="PrePath",start="0001",max="9999")}`：在相同前缀下自动递增的4位数字

#### 示例2：产品编码
```
PRD-${attr(name="department")}-${alpha(scope="Element[1]",start="AA",max="ZZ")}-${autoIncrease(scope="PrePath",start="001",max="999")}
```
可能生成的编码：`PRD-R&D-AA-001`、`PRD-R&D-AA-002`、`PRD-MKT-AA-001`

这个规则的含义是：
- `PRD-`：固定前缀
- `${attr(name="department")}`：部门属性值
- `${alpha...}`：两位字母序列
- `${autoIncrease(scope="PrePath",start="001",max="999")}`：三位数字序列
### 支持的模式元素
| 元素类型 | 语法 | 说明 | 示例 |
|---------|------|------|------|
| 属性值 | `${attr(name="attrName")}` | 从模型对象中提取指定属性值 | `${attr(name="category")}` |
| 日期 | `${date(pattern="format")}` | 生成指定格式的日期 | `${date(pattern="YYMM")}` |
| 自增序列 | `${autoIncrease(scope="type",start="value",step="n",max="value")}` | 生成数字序列 | `${autoIncrease(scope="PrePath",start="00000",step="1",max="99999")}` |
| 字母序列 | `${alpha(scope="type",start="value",max="value")}` | 生成字母序列 | `${alpha(scope="Rule",start="AA",max="ZZ")}` |
| 脚本 | `${script(content="scriptContent")}` | 执行 Groovy 脚本并使用返回值 | `${script(content="return modelObject.getType()")}` |

1. **属性值提取**
   ```
   ${attr(name="attrName")}
   ```
   - 作用：从对象中提取指定属性的值
   - 示例：如果对象的 category="TECH"，则 `${attr(name="category")}` 会被替换为 "TECH"

2. **日期格式化**
   ```
   ${date(pattern="format")}
   ```
   - 作用：插入当前日期，支持多种日期格式
   - 常用格式：
     - `"YYMM"`：年月（如：2401）
     - `"YYYYMMdd"`：完整日期（如：20240125）
     - `"YY"`：年份（如：24）

3. **数字自增序列**
   ```
   ${autoIncrease(scope="type",start="value",step="n",max="value")}
   ```
   - 作用：生成递增的数字序列
   - 参数说明：
     - `scope`：序列范围（见下文）
     - `start`：起始值（如："0001"）
     - `step`：步长（可选，默认为1）
     - `max`：最大值
   - 示例：`${autoIncrease(scope="PrePath",start="0001",step="1",max="9999")}`

4. **字母序列**
   ```
   ${alpha(scope="type",start="value",max="value")}
   ```
   - 作用：生成递增的字母序列
   - 参数说明：
     - `scope`：序列范围
     - `start`：起始值（如："AA"）
     - `max`：最大值（如："ZZ"）
   - 示例：`${alpha(scope="Rule",start="AA",max="ZZ")}`

5. **脚本执行**
   ```
   ${script(content="scriptContent")}
   ```
   - 作用：执行Groovy脚本并使用返回值
   - 示例：`${script(content="return modelObject.get('name').substring(0,3);")}`

### 序列范围类型详解

序列范围（scope）决定了序号的计数方式。考虑以下编码模式：
```
PRD-${attr(name="type")}-${date(pattern="YY")}-${autoIncrease(scope="PrePath",start="001",max="999")}
```

1. **Rule范围**
   ```
   scope="Rule"
   ```
   - 作用：在整个规则级别共享序列，不考虑前面的值
   - 示例：
     ```
     PRD-HW-24-001  （第一个产品）
     PRD-SW-24-002  （第二个产品，即使type不同）
     PRD-HW-24-003  （第三个产品）
     ```

2. **PrePath范围**
   ```
   scope="PrePath"
   ```
   - 作用：基于前面所有元素的组合作为计数基准
   - 示例：
     ```
     PRD-HW-24-001  （硬件类第一个）
     PRD-HW-24-002  （硬件类第二个）
     PRD-SW-24-001  （软件类重新开始）
     PRD-SW-24-002  （软件类第二个）
     ```

3. **Element[n]范围**
   ```
   scope="Element[n]"
   ```
   - 作用：使用第n个元素值作为计数基准
   - 示例（scope="Element[1]"，基于type值）：
     ```
     PRD-HW-24-001  （基于"HW"计数）
     PRD-HW-25-002  （仍基于"HW"计数，年份变化不影响）
     PRD-SW-24-001  （基于"SW"重新开始）
     ```

## 编码规则定义方式
编码规则只能使用DSL来定义

### 1. 完全使用DSL定义

在 DSL 中定义编码模式:
```javascript
create type sample.Dataset extends AbstractModelObject {
    use traits [
        BusinessCodeTrait
    ]
    
    codeGenPattern: """DOC-${attr(name="type")}-${script(content="return modelObject.get('name').substring(0,3);")}-${date(pattern="YYMM")}-${alpha(scope="Element[1]",start="AA",max="ZZZZ")}"""
}
```

### 2. 使用DSL配合已有Java定义
先在Java中定义Trait
```java
@PersistentEntity(schema = Schema.DOCUMENT, name = "Dataset")
@LocalizedNameDesc(name = "文档集合", description = "一系列的相关文档的集合")
public class Dataset extends AbstractModelObject {

    private final BusinessCodeTrait<Dataset> businessCode;
    
    public Dataset() {
        super(Dataset.class.getName());
        this.businessCode = new BusinessCodeTraitImpl<>(this);
    }
}
```
然后使用DSL指定编码规则
```java
update type sample.Dataset extends AbstractModelObject {    
    codeGenPattern: """DOC-${attr(name="type")}-${script(content="return modelObject.get('name').substring(0,3);")}-${date(pattern="YYMM")}-${alpha(scope="Element[1]",start="AA",max="ZZZZ")}"""
}
```

## 编码生成
#### 方式一：保存时自动生成
在Java或DSL中使用了`BusinessCodeTrait`，保存对象时自动生成编码
:::warning ⚠️暂未实现
基础能力，需要后续平台实现
:::

#### 方式二：直接通过Java中手工 `ModelObject` 使用
```java
Dataset dataset = Dataset.newModel();
dataset.setType("PDF");
dataset.setName("secureDoc001");
// 生成编码并返回
String code = dataset.generateCode(false);
// 生成编码并保存
dataset.generateCode(false);
objectService.save(dataset);
```

#### 方式三：通过Java中手工 `CodeGeneratorService` 使用
```java
// 获取编码生成服务
CodeGeneratorService codeService = S.service(CodeGeneratorService.class);

// 生成编码
Dataset dataset = Dataset.newModel();
dataset.setType("PDF");
dataset.setName("secureDoc001");
String code = codeService.generateCode(dataset);

// 保存对象
dataset.setCode(code);
objectService.save(dataset);
```

## 最佳实践

1. **编码模式设计**
   - 保持编码结构清晰、有意义
   - 合理使用分隔符提高可读性
   - 预留足够的序列空间

2. **序列范围选择**
   - 需要全局唯一时使用 Rule 范围
   - 需要按前缀分组时使用 PrePath 范围
   - 需要按特定元素分组时使用 Element[n] 范围

3. **性能考虑**
   - 避免复杂的脚本运算
   - 合理设计序列步长，避免频繁更新
   - 注意并发场景下的序列生成

## 注意事项

1. 编码一旦生成通常不应修改
2. 确保模式中引用的属性在生成编码时已经设置值


## FAQ
**Q: 是否支持自定义序列格式？**
A: 可以通过 script 元素实现自定义的序列格式。