# 前端数据操作

本文档介绍如何使用EMOP前端数据操作API进行数据访问和操作。前端API设计保持与后端Java API的一致性，同时提供了完整的TypeScript类型支持和响应式数据处理能力。

ℹ️提示
>所有前端数据操作最终都会调用平台提供的[REST API](../data/rest-api)。可以直接使用这些REST API进行开发。

## 概述

### 设计理念

EMOP前端数据操作API具有以下特点：

- 与后端Java API保持一致的接口设计
- 完整的TypeScript类型支持
- 链式API调用
- 内置的数据验证
- 支持树形数据的预加载
- 响应式数据处理

### 核心概念

1. **Q类**: 查询的入口点，提供了创建查询的静态方法
2. **QueryBuilder**: 查询构建器，支持链式调用
3. **ModelObject**: 所有数据对象的基类

## 基础查询操作

### 初始化查询

使用`Q.from`方法开始一个查询：

```typescript
import { Q } from '@platform/query'
import { ItemRevision } from '@platform/types/modelObject'

// 基本查询
const query = Q.from<ItemRevision>('ItemRevision')

// 带上下文的查询
const contextQuery = Q.from<ItemRevision>('ItemRevision', contextObject)
```

### 直接ID查询

可以使用`Q.id`和`Q.ids`方法直接根据ID查询单个或多个对象：

```typescript
typescript// 根据ID获取单个对象
const document = await Q.id<Document>("123456");
if (document) {
   console.log(`文档名称: ${document.name}`);
}

// 批量查询多个对象
const parts = await Q.ids<Part>(["P001", "P002", "P003"]);
console.log(`获取到 ${parts.length} 个零件`);

// 处理获取到的多个对象
parts.forEach(part => {
   console.log(`零件: ${part.code}, 版本: ${part.revId}`);
});
```

### CRUD操作

#### 创建和更新操作

前端提供了S对象用于方便地访问后端服务（暂时未完成，后续再更新）:

```typescript
import { S } from '@platform/services'

// 创建对象
const newItem = await S.service(DataService).create('ItemRevision', {
    code: 'A123',
    name: 'Test Item'
}, {
    // 可选的上下文信息
    currentObjectId: contextObject?.id,
    selectedObjectIds: selectedObjects?.map(obj => obj.id)
})

// 更新对象
const updated = await S.service(DataService).update(item.id, {
    name: 'Updated Name'
})
```

使用axios直接调用REST API:

```typescript
// 创建对象
const response = await axios.post(`/webconsole/api/data/${typeName}`, {
    data: formData,
    context: {
        currentObjectId: contextObject?.id,
        selectedObjectIds: selectedObjects?.map(obj => obj.id)
    }
})

// 更新对象
const response = await axios.put(`/webconsole/api/data/${objectId}`, {
    data: updateData,
    context: context
})
```

#### 查询操作

```typescript
// 查询单个对象
const revision = await Q.from<ItemRevision>('ItemRevision')
    .field('code').equals('A123')
    .first()

// 查询列表
const revisions = await Q.from<ItemRevision>('ItemRevision')
    .field('state').equals('Released')
    .query()

// 分页查询
const page = await Q.from<ItemRevision>('ItemRevision')
    .field('state').equals('Released')
    .page(0, 20)
    .queryPage()
```

### 条件查询

提供了丰富的条件操作符：

```typescript
const query = Q.from<ItemRevision>('ItemRevision')
    .field('name').contains('Test')        // LIKE查询
    .field('code').startsWith('A')         // 前缀匹配
    .field('revId').equals('A')            // 精确匹配
    .field('createDate').greaterThan(new Date('2024-01-01')) // 大于
    .field('state').in(['Released', 'Working'])  // IN查询
```

### 排序和分页

```typescript
const result = await Q.from<ItemRevision>('ItemRevision')
    .field('state').equals('Released')
    .field('createDate').desc()           // 按创建日期降序
    .field('name').asc()                  // 按名称升序
    .page(0, 20)                          // 分页（第1页，每页20条）
    .queryPage()

console.log(result.content)               // 当前页数据
console.log(result.totalElements)         // 总记录数
console.log(result.totalPages)            // 总页数
```

## 逻辑对象查询

EMOP前端提供了完整的逻辑对象查询API，支持基于业务编码和版本规则的查询操作。

### 1. 创建逻辑查询

```typescript
import { Q, RevisionRule } from '@platform/query'
import { ItemRevision } from '@platform/types/modelObject'

// 使用对象类型创建查询
const query = Q.logical<ItemRevision>('ItemRevision')

// 带上下文的查询
const contextQuery = Q.logical<ItemRevision>('ItemRevision', contextObject)
```

### 2. 条件查询与直接解析

```typescript
// 查询并直接解析为物理对象
const results = await Q.logical<ItemRevision>('ItemRevision')
    .where(builder => {
        builder.field('name').contains('发动机')
        builder.field('state').equals('Released')
    })
    .withRule(RevisionRule.LATEST_RELEASED)
    .query()

// 查询单个对象
const single = await Q.logical<ItemRevision>('ItemRevision')
    .where(builder => builder.field('code').equals('ENG-001'))
    .withRule(RevisionRule.LATEST)
    .first()

```

### 3. 分页查询

```typescript
// 分页解析为物理对象
const physicalPage = await Q.logical<ItemRevision>('ItemRevision')
    .where(builder => builder.field('state').equals('Released'))
    .withRule(RevisionRule.LATEST_RELEASED)
    .field('name').asc()
    .page(0, 20)
    ..queryPage()

console.log(physicalPage.content)        // 物理对象数组
console.log(physicalPage.totalElements)  // 总数

// 分页获取逻辑对象
const logicalPage = await Q.logical<ItemRevision>('ItemRevision')
    .where(builder => builder.field('createDate').greaterThan(new Date()))
    .withRule(RevisionRule.LATEST)
    .field('createDate').desc()
    .page(0, 50)
    .queryPage()
```

### 4. 内置版本规则

```typescript
import { RevisionRule } from '@platform/query'

// 最新版本（不考虑状态）
const latest = await Q.logical<ItemRevision>('ItemRevision')
    .where(builder => builder.field('category').equals('Part'))
    .withRule(RevisionRule.LATEST)
    .query()

// 最新发布版本
const released = await Q.logical<ItemRevision>('ItemRevision')
    .where(builder => builder.field('category').equals('Part'))
    .withRule(RevisionRule.LATEST_RELEASED)
    .query()

// 最新工作版本
const working = await Q.logical<ItemRevision>('ItemRevision')
    .where(builder => builder.field('category').equals('Part'))
    .withRule(RevisionRule.LATEST_WORKING)
    .query()

// 精确版本（需要指定revId）
const precise = await Q.logical<ItemRevision>('ItemRevision')
    .where(builder => builder.field('code').equals('ENG-001'))
    .withRule(RevisionRule.PRECISE)
    // 注意：PRECISE规则需要在逻辑对象中指定revId
    .first()
```

### 5. 动态版本规则

```typescript
// 根据用户选择的版本规则查询
const selectedRule = userSelectedRule // 来自UI选择
const results = await Q.logical<ItemRevision>('ItemRevision')
    .where(builder => builder.field('project').equals('PROJECT-001'))
    .withRule(selectedRule)
    .query()

// 版本规则切换
let query = Q.logical<ItemRevision>('ItemRevision')
    .where(builder => builder.field('code').equals('ENG-001'))

const latestVersion = await query.withRule(RevisionRule.LATEST).first()
const releasedVersion = await query.withRule(RevisionRule.LATEST_RELEASED).first()
```

### 6. 组合条件查询

```typescript
const complexResults = await Q.logical<ItemRevision>('ItemRevision')
    .where(builder => {
        builder.and(andBuilder => {
            andBuilder.field('name').contains('发动机')
            andBuilder.field('createDate').greaterThan(new Date('2024-01-01'))
        })
        
        builder.or(orBuilder => {
            orBuilder.field('state').equals('Released')
            orBuilder.field('state').equals('Working')
        })
    })
    .withRule(RevisionRule.LATEST_RELEASED)
    .field('createDate').desc()
    .query()
```

### 7. 带附加属性的查询

```typescript
const results = await Q.logical<ItemRevision>('ItemRevision')
    .where(builder => builder.field('category').equals('Engine'))
    .withRule(RevisionRule.LATEST)
    .withAdditionalProp('hasChildren', 'count(children)>0')
    .withAdditionalProp('latestRevId', 'target/revId')
    .query()
```

### 8. 实际应用场景

#### 1. 产品BOM管理

```typescript
// BOM树形结构加载
async function loadBomTree(productCode: string) {
    const bomLines = await Q.logical<BomLine>('BomLine')
        .where(builder => builder.field('parent/code').equals(productCode))
        .withRule(RevisionRule.LATEST_RELEASED)
        .withAdditionalProp('targetName', 'target/name')
        .withAdditionalProp('targetRevId', 'target/revId')
        .field('lineNumber').asc()
        .query()
        
    return bomLines.map(line => ({
        id: line.id,
        lineNumber: line.lineNumber,
        targetCode: line.target?.code,
        targetName: line.targetName,
        targetRevId: line.targetRevId,
        quantity: line.quantity
    }))
}
```

#### 2. 文档检索界面

```typescript
// 文档搜索与分页
async function searchDocuments(keyword: string, pageNum: number, pageSize: number) {
    const page = await Q.logical<Document>('Document')
        .where(builder => {
            if (keyword) {
                builder.or(orBuilder => {
                    orBuilder.field('name').contains(keyword)
                    orBuilder.field('description').contains(keyword)
                })
            }
            builder.field('state').equals('Released')
        })
        .withRule(RevisionRule.LATEST_RELEASED)
        .field('lastModifiedDate').desc()
        .page(pageNum, pageSize)
        ..queryPage()
        
    return {
        documents: page.content.map(doc => ({
            id: doc.id,
            name: doc.name,
            code: doc.code,
            revId: doc.revId,
            lastModifiedDate: doc._lastModifiedDate
        })),
        total: page.totalElements,
        hasNext: page.hasNext
    }
}
```

#### 3. 版本规则切换界面

```typescript
// 版本规则选择组件
interface RevisionRuleSelectorProps {
    objectType: string
    baseCondition: (builder: ConditionBuilder) => void
    onResultsChange: (results: ItemRevision[]) => void
}

function RevisionRuleSelector({ objectType, baseCondition, onResultsChange }: RevisionRuleSelectorProps) {
    const [selectedRule, setSelectedRule] = useState(RevisionRule.LATEST)
    const [loading, setLoading] = useState(false)
    
    const fetchResults = async (rule: RevisionRule) => {
        setLoading(true)
        try {
            const results = await Q.logical<ItemRevision>(objectType)
                .where(baseCondition)
                .withRule(rule)
                .query()
            onResultsChange(results)
        } finally {
            setLoading(false)
        }
    }
    
    const handleRuleChange = (rule: RevisionRule) => {
        setSelectedRule(rule)
        fetchResults(rule)
    }
    
    return (
        <div>
            <select value={selectedRule.name} onChange={e => handleRuleChange(RevisionRule.valueOf(e.target.value))}>
                <option value="LATEST">最新版本</option>
                <option value="LATEST_RELEASED">最新发布版</option>
                <option value="LATEST_WORKING">最新工作版</option>
            </select>
            {loading && <div>加载中...</div>}
        </div>
    )
}
```

#### 4. 变更影响分析

```typescript
// 分析零件变更影响
async function analyzeChangeImpact(partCode: string) {
    // 1. 获取零件的最新工作版本
    const workingPart = await Q.logical<ItemRevision>('ItemRevision')
        .where(builder => builder.field('code').equals(partCode))
        .withRule(RevisionRule.LATEST_WORKING)
        .first()
        
    if (!workingPart) {
        throw new Error(`零件 ${partCode} 不存在`)
    }
    
    // 2. 查找所有引用此零件的产品BOM
    const referencingBoms = await Q.logical<BomLine>('BomLine')
        .where(builder => builder.field('target/code').equals(partCode))
        .withRule(RevisionRule.LATEST_RELEASED)
        .withAdditionalProp('parentName', 'parent/name')
        .withAdditionalProp('parentCode', 'parent/code')
        .query()
        
    // 3. 分析影响范围
    const impactAnalysis = {
        changedPart: {
            code: workingPart.code,
            name: workingPart.name,
            revId: workingPart.revId
        },
        affectedProducts: referencingBoms.map(bom => ({
            productCode: bom.parentCode,
            productName: bom.parentName,
            bomLineId: bom.id,
            quantity: bom.quantity
        })),
        totalAffectedProducts: new Set(referencingBoms.map(b => b.parentCode)).size
    }
    
    return impactAnalysis
}
```

## 高级查询特性

### 复杂条件组合

使用`beginAnd`/`beginOr`和`endGroup`组合复杂条件：

```typescript
const revisions = await Q.from<ItemRevision>('ItemRevision')
    .beginAnd()
        .field('name').contains('Test')
        .field('createDate').greaterThan(new Date('2024-01-01'))
    .endGroup()
    .beginOr()
        .field('state').equals('Released')
        .field('state').equals('Working')
    .endGroup()
    .query()
```

### 树形数据查询

对于树形结构数据（如BOM），可以使用`asTreePreload`进行预加载：

```typescript
const tree = await Q.from<BomLine>('BomLine')
    .field('code').equals('ARJ21-700')
    .asTreePreload()
        .maxDepth(3)                      // 最大加载深度
        .relations(['children'])           // 预加载的关系
        .approximateNodeLimit(1000)       // 节点数量限制
    .first()
```

### 附加属性查询

可以在查询时动态加载额外的属性：

```typescript
const result = await Q.from<BomLine>('BomLine')
    .withAdditionalProp('hasChildren', 'count(children)>0')
    .withAdditionalProp('targetRevId', 'target/revId')
    .query()
```

## 引用关系查询

### 查询引用者

Q类提供了用于查找引用关系的静态方法：

```typescript
import { Q } from '@platform/query'

// 查找所有引用指定对象的对象
const referencers = await Q.findReferencers<ModelObject>(targetObject)

// 也可以直接使用对象ID
const referencersByID = await Q.findReferencers<ModelObject>('123456')

// 指定是否精确匹配（针对可版本化引用）
const preciseReferencers = await Q.findReferencers<ModelObject>(targetObject, true)
```

### 按关系类型查询引用者

可以按特定的关系类型查询引用者：

```typescript
// 查找通过特定关系类型引用对象的引用者
const typeReferencers = await Q.findReferencersByTypes<ModelObject>(
    targetObject,
    ['ASSOCIATE', 'STRUCTURAL'], // 关系类型
    true                         // 精确匹配
)
```

### 仅查询结构与关联关系

针对不需要版本引用关系的场景，可以使用专门的方法：

```typescript
// 仅查找结构和关联关系的引用者
const structuralAndAssociateReferencers = await Q.findAssociateAndStructuralReferencers<ModelObject>(targetObject)
```

### 引用关系响应结构

引用关系查询返回的数据结构包含引用者及关系信息：

```typescript
interface ReferenceResponse {
    referencer: ModelObject;     // 引用者对象
    referenceType: string;       // 引用类型(ASSOCIATE, STRUCTURAL, REVISIONABLE_REFERENCE)
    relationName: string;        // 关系名称
    relationProps: Record<string, any>; // 关系属性
}
```

### 使用示例

```typescript
// 查找所有引用该文档的对象
const document = await Q.from<Document>('Document')
    .field('code').equals('DOC-001')
    .first()

// 获取所有引用者
const referencers = await Q.findReferencers<ModelObject>(document)

// 分析引用关系
const result = {
    totalReferencers: referencers.length,
    byType: {} as Record<string, number>
}

// 统计不同类型引用者
referencers.forEach(ref => {
    const type = ref.referencer._objectType
    result.byType[type] = (result.byType[type] || 0) + 1
})

console.log(`文档被 ${result.totalReferencers} 个对象引用`)
console.log('引用类型统计:', result.byType)
```

## 数据模型和类型

### ModelObject基类

所有业务对象都继承自`ModelObject`，提供了以下基础功能：

```typescript
interface ModelObject {
    id: string
    name: string
    description?: string
    
    // 系统属性
    _properties: Record<string, any>
    _creationDate: string
    _creator: number
    _lastModifiedDate: string
    _state?: 'Released' | 'Working' | 'Draft'
    _version: number
    _objectType: string
    
    // 加载相关方法
    load(propertyName: string): Promise<void>
    loadMany(propertyNames: string[]): Promise<void>
    isPropertyLoaded(propertyName: string): boolean
}
```

### 延迟加载

对于引用类型的属性，支持延迟加载：

```typescript
// 显式加载
const bomLine = await Q.from<BomLine>('BomLine').first()
await bomLine.load('children')            // 加载子项
await bomLine.load('target')              // 加载目标对象

// 批量加载
await bomLine.loadMany(['children', 'target'])

// 检查加载状态
if (!bomLine.isPropertyLoaded('children')) {
    await bomLine.load('children')
}
```

## 实战示例

### BOM树形结构加载

以下是一个完整的BOM树加载示例：

```typescript
async function loadBomTree() {
    const rootBomLine = await Q.from<BomLine>('AircraftBomLine')
        .field('code').equals('ARJ21-700')
        .withAdditionalProp('hasChildren', 'count(children)>0')
        .withAdditionalProp('revId', 'target/revId')
        .asTreePreload()
            .maxDepth(5)
            .approximateNodeLimit(100)
            .relations(['children'])
        .first()
        
    if (rootBomLine) {
        // 处理节点
        const processNode = (node: BomLine, parentPath?: string) => {
            // 设置节点路径
            node.idPath = parentPath 
                ? `${parentPath}/${node.id}` 
                : node.id.toString()
            
            if (node.children) {
                node.children.forEach(child => {
                    processNode(child, node.idPath)
                })
            }
        }
        
        processNode(rootBomLine)
        return rootBomLine
    }
}
```
## 版本管理操作

EMOP平台提供了完整的版本对象(Revision)管理API，可以方便地创建新版本、查询历史版本以及解析版本引用。

### 核心概念

1. **版本对象**：`ItemRevision` 是所有需要版本控制的业务对象的基类
2. **复制规则**：创建新版本时的关系处理策略
   - `NoCopy`: 不复制任何关系
   - `CopyReference`: 复制引用关系
   - `CopyObject`: 复制对象和关系

### 创建新版本

```typescript
// 使用RevisionService创建新版本
const newPart = await RevisionService.revise(part, CopyRule.CopyReference);

// 或者使用对象方法
const newPart2 = await part.revise(CopyRule.CopyReference);

// 批量创建新版本
const newParts = await RevisionService.reviseMany(parts, CopyRule.NoCopy);
```

### 查询特定版本
```typescript
// 查询特定版本
const part = await RevisionService.queryRevision<Part>('Part', 
  'P-001',  // 编码
  'A'       // 版本号
);

// 查询最新版本
const latestPart = await RevisionService.queryLatestRevision<Part>(
  'Part', 
  'P-001'   // 编码
);

// 查询最新发布版本
const latestReleasedPart = await RevisionService.queryLatestReleasedRevision<Part>('Part', 'P001');

// 使用便捷查询方法
const part = await Q.fromRevisionCode<Part>('Part', 'P-001', 'A');
const latestPart = await Q.fromRevisionCode<Part>('Part', 'P-001');
const latestReleasedVersion = await part.getLatestReleasedRevision<Part>();
```
::: warning ⚠️最新版本 V.S. 最新发布版本
最新版本 (Latest Revision)
- 返回版本号最高的对象，无论状态如何
- 适用于获取工作中的最新版本

最新发布版本 (Latest Released Revision)
- 仅返回状态为 Released 的最高版本号对象
- 适用于获取正式发布的版本，忽略工作中或草稿状态的版本
:::

### 版本历史与状态
```typescript
// 获取版本历史
const history = await RevisionService.getRevisionHistory('Part', 'P-001');

// 或者直接从对象获取
const history = await part.getRevisionHistory();

// 检查是否可以创建新版本
const canRevise = await part.canRevise();
if (canRevise) {
    const newRevision = await part.revise();
}
```

### 批量操作
```typescript
// 批量查询特定版本
const parts = await RevisionService.queryRevisions<Part>('Part',[{code: 'P-001', revId: 'A'},{code: 'P-002', revId: 'B'}]);

// 批量查询最新版本
const latestParts = await RevisionService.queryLatestRevisions<Part>('Part',['P-001', 'P-002', 'P-003']);

// 批量查询最新发布版本
const latestReleasedParts = await RevisionService.queryLatestReleasedRevisions<Part>('Part', ['P001', 'P002']);
```

## 最佳实践

### 性能优化

1. **合理使用预加载**
   ```typescript
   // 推荐：一次性预加载需要的数据
   const tree = await Q.from<BomLine>('BomLine')
       .asTreePreload()
       .maxDepth(3)
       .relations(['children', 'target'])
       .first()
   
   // 避免：频繁的单个加载
   const bomLine = await Q.from<BomLine>('BomLine').first()
   await bomLine.load('children')
   for (const child of bomLine.children) {
       await child.load('target')  // 性能不佳
   }
   ```

2. **使用合适的查询条件**
   ```typescript
   // 推荐：使用索引字段
   const result = await Q.from<ItemRevision>('ItemRevision')
       .field('code').equals('A123')      // 通常有索引
       .first()
   
   // 避免：对非索引字段进行模糊查询
   const result = await Q.from<ItemRevision>('ItemRevision')
       .field('description').contains('test')  // 可能性能较差
       .query()
   ```

3. **控制加载数据量**
   ```typescript
   // 始终使用分页
   const page = await Q.from<ItemRevision>('ItemRevision')
       .page(0, 20)
       .queryPage()
   
   // 树形数据设置合理的深度和节点限制
   const tree = await Q.from<BomLine>('BomLine')
       .asTreePreload()
       .maxDepth(3)                     // 限制深度
       .approximateNodeLimit(1000)      // 限制节点数
       .first()
   ```

### 类型安全

充分利用TypeScript的类型系统：

```typescript
// 定义业务对象接口
interface ItemRevision extends ModelObject {
    code: string
    revId: string
    state: 'Released' | 'Working'
}

// 使用类型安全的查询
const query = Q.from<ItemRevision>('ItemRevision')
    .field('code').equals('A123')        // 类型检查
    .field('state').in(['Released', 'Working'])  // 枚举值检查
```

### 错误处理

```typescript
try {
    const result = await Q.from<ItemRevision>('ItemRevision')
        .field('code').equals('A123')
        .first()
        
    if (!result) {
        // 处理未找到的情况
        console.warn('ItemRevision not found')
        return
    }
    
    // 使用结果
} catch (error) {
    // 处理查询错误
    console.error('Query failed:', error)
}
```

## 常见问题

1. **延迟加载陷阱**
   ```typescript
   // 问题：在循环中进行延迟加载
   for (const item of items) {
       await item.load('target')  // 性能问题
   }
   
   // 解决：使用预加载或批量加载
   const items = await Q.from<BomLine>('BomLine')
       .withAdditionalProp('targetInfo', 'target/name')
       .query()
   ```

2. **类型转换问题**
   ```typescript
   // 使用 as 方法指定具体类型
   const result = await Q.from('ItemRevision')
       .as<ItemRevision>()  // 指定类型
       .field('code').equals('A123')
       .first()
   ```

3. **内存管理**
   ```typescript
   // 大数据集的处理
   const processLargeData = async () => {
       let pageNum = 0
       const pageSize = 100
       
       while (true) {
           const page = await Q.from<ItemRevision>('ItemRevision')
               .page(pageNum++, pageSize)
               .queryPage()
           
           // 处理当前页数据
           for (const item of page.content) {
               await processItem(item)
           }
           
           if (!page.hasNext) break
       }
   }
   ```