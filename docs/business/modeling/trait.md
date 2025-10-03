
# Trait(特征)使用手册

## 什么是 Trait？

Trait（特征）是一种可复用的功能单元，它定义了一组相关的属性和行为。通过 Trait，我们可以为业务对象添加一至多个特征达到扩展模型的特定能力的目的，而不需要修改对象的结构。


## Trait 使用场景举例

### 1. 文档管理
需要同时具备层级管理、版本控制和审批流程的文档系统：
```java
public class Document extends ItemRevision {
    
    @QuerySqlField
    private String title;
    
    @QuerySqlField
    private String content;
    
    private final HierarchicalTrait<Document> hierarchical;
	
    private final ApprovableTrait<Document> approvable;
	
	public Document() {
        super(Document.class.getName());
		this.hierarchical = new HierarchicalTraitImpl<>(this);
        this.changeable = new ApprovableTraitImpl<>(this);
    }
}
```

### 2. 产品结构管理
需要支持版本控制和变更管理的产品 BOM：
```java
public class BOMLine extends ItemRevision {
    
    @QuerySqlField
    private String itemNumber;
    
    @QuerySqlField
    private Integer quantity;
    
    private final ChangeableTrait<BOMLine> changeable;
    
    public BOMLine() {
        super(BOMLine.class.getName());
        this.changeable = new ChangeableTraitImpl<>(this);
    }
}
```

### 3. 审批流程
各类需要审批的业务对象：
```java
public class ChangeRequest extends AbstractModelObject {
    
    @QuerySqlField
    private String title;
    
    @QuerySqlField
    private String description;
}
```

## 如何使用 Trait

### 1. 在 Java 中使用静态 Trait
当 Trait 在 Java 中定义时，可以通过接口实现和委托的方式使用：

```java
// 1. 在业务类中实现 Trait 接口
public final class Department extends AbstractModelObject {

    private final HierarchicalTrait<Department> hierarchical;
    
    public Department() {
        super(Department.class.getName());
        this.hierarchical = new HierarchicalTraitImpl<>(this);
    }
	
	public void setParent(@NonNull Department parent) {
		this.hierarchical.setParent(parent);
	}
}

// 2. 使用 Trait 功能
Department rd = new Department();
rd.setName("研发部");
rd.setParent(parentDept);  // 直接调用 trait 方法
```

### 2. DSL 方式添加 Trait
可以通过 DSL 为已有的业务对象添加 Trait：

```javascript
// 为 Document 类型添加审计和层级特征
create type document.Document extends AbstractModelObject {
    use traits [
        HierarchicalTrait,  // 使用已有的 Java Trait
        AuditableTrait      // 使用已有的 Java Trait
    ]
    
    attribute title: String {
        required: true
    }
}
```

### 3. 在 Java 中使用 DSL 添加的 Trait

当 Trait 是通过 DSL 动态添加时，在 Java 代码中有两种使用方式：

#### 使用 ModelObject 的 trait 方法（推荐）
```java
// 获取业务对象
Document doc = Q.result(Document.class).where("id = ?", docId).first();

// 1. 判断是否有某个 trait
if (doc.hasTrait(HierarchicalTrait.class.getName())) {
    // 2. 获取 trait 实例
    HierarchicalTrait<?> hierarchical = doc.trait(HierarchicalTrait.class.getName());
    
    // 3. 使用 trait 功能
    hierarchical.setParent(parentDoc);
    //或者
    doc.set("parent", parentDoc);
    String path = hierarchical.getPath();
}

// 同样的方式使用其他 trait
if (doc.hasTrait(AuditableTrait.class.getName())) {
    AuditableTrait<?> auditable = doc.trait(AuditableTrait.class.getName());
    auditable.addAuditLog("update", "更新文档内容");
}
```

#### 使用建议
- 优先使用 `trait()` 方法，更灵活且安全
- 在调用 trait 方法前检查 trait 是否存在
- 如果确定有该 trait，可以使用接口方式简化代码
- 对于通过 DSL 动态添加的 trait，建议使用 `trait()` 方法

## 内置的 Trait

EMOP提供了几个常用的内置Trait,它们实现了常见的业务功能。

### 1. HierarchicalTrait - 层级关系管理

用于管理对象之间的父子层级关系。使用结构关系(@StructuralRelation)实现,提供高效的层级数据访问。

#### 1.1 存储字段
```java
@QuerySqlField(index = true)
private Long parentId;            // 父节点ID

@StructuralRelation(foreignKeyField = "parentId")
private T parent;                 // 父节点引用，通过 parentId 自动查找，不存数据库

@StructuralRelation(foreignKeyField = "parentId")
private List<T> children;         // 子节点列表，通过 parentId 自动查找，不存数据库
```

#### 1.2 核心能力
- 父节点管理
  - getParent(): 获取父节点(不触发加载)
  - queryParent(): 获取父节点(懒加载)
  - setParent(T): 设置父节点

- 子节点管理
  - getChildren(): 获取子节点列表(不触发加载)
  - queryChildren(): 获取子节点列表(懒加载)

#### 1.3 使用示例
```java
public final class Department extends AbstractModelObject {
    private final HierarchicalTrait<Department> hierarchical;
    
    public Department() {
        super(Department.class.getName());
        this.hierarchical = new HierarchicalTraitImpl<>(this);
    }

    // 访问父节点
    public Department getParent() {
        return hierarchical.getParent();
    }
    
    // 查询子节点(会触发懒加载)
    public List<Department> queryChildren() {
        return hierarchical.queryChildren();
    }
}

// 使用示例
Department rd = Department.newModel();
rd.setParent(parentDept);        // 设置父节点
List<Department> subs = rd.queryChildren();  // 获取子节点
```

### 2. BusinessCodeTrait - 业务编码生成

提供业务对象编码的自动生成和管理能力。支持灵活的编码规则配置,确保编码唯一性。

#### 2.1 存储字段
```java
@QuerySqlField(index = true, inlineSize = 18, notNull = true)
@FullTextSearchField
private String code;             // 业务编码
```

#### 2.2 核心能力
- 编码管理
  - getCode(): 获取业务编码
  - setCode(String): 设置业务编码
  - generateCode(boolean): 自动生成新编码

#### 2.3 使用示例
```java
public class Part extends AbstractModelObject {
    private final BusinessCodeTrait<Part> businessCode;
    
    public Part() {
        super(Part.class.getName());
        this.businessCode = new BusinessCodeTraitImpl<>(this);
    }
    
    public String getCode() {
        return businessCode.getCode();
    }
    
    public String generateCode(boolean force) {
        return businessCode.generateCode(force);
    }
}

// 使用示例
Part part = Part.newModel();
String code = part.generateCode(false);  // 手工生成新编码
System.out.println(part.getCode());      // 获取编码

part.setCode(null); //保持code为空
S.service(ObjectService.class).save(part); //编码为空时自动生成新的编码
```
ℹ️ 编码规则配置见[这里](../core/code-pattern)

### 3. RevisionableRefTrait - 可修订对象引用

用于引用和解析可修订对象(`Revisionable`),支持根据版本规则(`RevisionRule`)动态解析目标对象。

#### 3.1 存储字段
```java
@QuerySqlField
private String targetObjectType;  // 目标对象类型

@QuerySqlField
private String targetItemCode;    // 目标对象编码

@QuerySqlField
private String targetRevId;       // 目标对象版本号
```

#### 3.2 核心能力
- 引用管理
  - setTarget(Revisionable): 设置引用目标
  - resolveTarget(RevisionRule): 解析目标对象

#### 3.3 使用示例
```java
public class BomLine extends AbstractModelObject {
    private final RevisionableRefTrait<BomLine> reference;
    
    public BomLine() {
        super(BomLine.class.getName());
        this.reference = new RevisionableRefTraitImpl<>(this);
    }
    
    public void setTarget(Revisionable target) {
        reference.setTarget(target);
    }
    
    public Revisionable resolveTarget(RevisionRule rule) {
        return reference.resolveTarget(rule);
    }
}

// 使用示例
BomLine line = BomLine.newModel();
line.setTarget(part);              // 设置引用
Revisionable target = line.resolveTarget(RevisionRule.LATEST);  // 解析目标对象
```

## 如何扩展 Trait

### 1. 分析业务需求
- 确定需要复用的功能
- 设计合适的接口
- 规划数据结构

### 2. 定义新的 Trait
```java
// 1. 定义接口
public interface AuditableTrait<T extends ModelObject> extends Trait<T> {
    // trait ID 就是接口的全限定名: io.emop.model.traits.AuditableTrait
    
    String getLastModifier();
    Date getLastModifiedDate();
    List<AuditLog> getAuditLogs();
    void addAuditLog(String action, String detail);
}

// 2. 实现功能
public class AuditableTraitImpl<T extends ModelObject> implements AuditableTrait<T> {
    private final T owner;
    
    @QuerySqlField
    private String lastModifier;
    
    @QuerySqlField
    private Date lastModifiedDate;
    
    @QuerySqlField
    private List<AuditLog> auditLogs = new ArrayList<>();
    
    // 实现方法...
}
```

ℹ️注意事项：由于`Trait`表达的是复用的原子功能，因此`Trait`不支持嵌套，也就是说`Trait`里面不能再定义`Trait`类型的属性，但是一个`ModelObject`可以包含多个`Trait`以达到组合效果。

## 最佳实践

### 1. 设计原则
- Trait 应该表达单一的业务能力
- 保持接口的简洁性
- 合理使用默认实现
- 注意性能影响

### 2. 实现建议
- 使用 final 修饰 owner 引用
- 合理设计数据结构
- 处理好并发问题
- 注意异常处理

### 3. 使用建议
- 优先使用内置 Trait
- 避免过度使用
- 保持代码可读性
- 编写完整的单元测试

通过合理使用 Trait 机制，我们可以：
- 提高代码复用度
- 使系统更容易维护
- 提升开发效率