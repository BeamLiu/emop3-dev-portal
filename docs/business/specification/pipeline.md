# EMOP Pipeline使用规范

## 1. 为什么需要Pipeline？

### 1.1 RPC网络延迟问题

在EMOP分布式架构中，业务服务通过Sofa-RPC调用EMOP Server的服务。根据测试数据：

- **每次RPC调用的网络延迟：0.5~2毫秒**
- **典型的批量处理场景：处理1000个BOM行项**

#### 传统方式的性能问题

```java
// ❌ 传统逐个处理方式
List<ProcessedBomLine> results = new ArrayList<>();
for (Long bomLineId : bomLineIds) {  // 假设1000个ID
    BomLine bomLine = objectService.findById(bomLineId);     // RPC调用1: 1ms
    if (bomLine.needsProcessing()) {
        Material material = objectService.findById(bomLine.getMaterialId()); // RPC调用2: 1ms  
        ProcessedBomLine processed = processBomLine(bomLine, material);
        objectService.save(processed);                       // RPC调用3: 1ms
        results.add(processed);
    }
}

// 性能分析：
// - 总RPC调用次数：1000 × 3 = 3000次
// - 总网络延时：3000 × 2ms = 6000ms
// - 仅网络延时就占用6秒，还不包括实际的业务处理时间！
```

#### Pipeline方式的性能优势

```java
// ✅ Pipeline批量处理方式
ObjectServicePipeline pipeline = ObjectService.pipeline();

// 1. 收集所有查询操作（不立即执行）
List<ResultFuture<BomLine>> bomFutures = new ArrayList<>();
for (Long bomLineId : bomLineIds) {
    ResultFuture<BomLine> bomFuture = pipeline.findById(bomLineId);
    bomFutures.add(bomFuture);
}

// 2. 一次性执行所有查询（1次RPC调用）
pipeline.execute();

// 3. 处理业务逻辑并收集下一批操作
ObjectServicePipeline savePipeline = ObjectService.pipeline();
List<ResultFuture<ProcessedBomLine>> saveFutures = new ArrayList<>();

for (ResultFuture<BomLine> bomFuture : bomFutures) {
    BomLine bomLine = bomFuture.get();  // 从future结果获取，无网络延时
    if (bomLine.needsProcessing()) {
        ProcessedBomLine processed = processBomLine(bomLine);
        ResultFuture<ProcessedBomLine> saveFuture = savePipeline.save(processed);
        saveFutures.add(saveFuture);
    }
}

// 4. 一次性执行所有保存（1次RPC调用）
savePipeline.execute();

// 性能分析：
// - 总RPC调用次数：2次（批量查询 + 批量保存）
// - 总网络延时：2 × 1ms = 2ms
// - 性能提升：3000ms / 2ms = 1500倍！
```

### 1.2 网络延时的累积效应

| 数据量 | 传统方式RPC次数 | 传统方式延时 | Pipeline RPC次数 | Pipeline延时 | 性能提升 |
|--------|----------------|-------------|-----------------|-------------|----------|
| 10条   | 30次           | 30ms        | 2次             | 2ms         | 15倍     |
| 100条  | 300次          | 300ms       | 2次             | 2ms         | 150倍    |
| 1000条 | 3000次         | 3秒         | 2次             | 2ms         | 1500倍   |
| 10000条| 30000次        | 30秒        | 2次             | 2ms         | 15000倍  |

**结论：数据量越大，Pipeline的性能优势越明显！与ORM中的`N+1查询`问题是一样的！**

## 2. Pipeline设计原理

### 2.1 核心思想

Pipeline采用 **收集-批量执行** 模式：

1. **收集阶段**：将多个单独的操作收集到Pipeline中，返回ResultFuture
2. **批量执行阶段**：调用execute()时，将同类型操作合并为少数几个批量RPC调用
3. **结果获取阶段**：通过ResultFuture.get()获取结果（实现JDK Future接口）

### 2.2 操作分组优化

Pipeline将操作按类型自动分组优化：

```java
// 用户添加的操作
ObjectServicePipeline pipeline = ObjectService.pipeline();

ResultFuture<BomLine> bom1 = pipeline.findById(1L);    // 查询操作
ResultFuture<BomLine> bom2 = pipeline.findById(2L);    // 查询操作  
ResultFuture<BomLine> bom3 = pipeline.findById(3L);    // 查询操作

ResultFuture<ProcessedBom> save1 = pipeline.save(obj1);  // 保存操作
ResultFuture<ProcessedBom> save2 = pipeline.save(obj2);  // 保存操作

// Pipeline执行时自动合并
pipeline.execute();  

// 内部执行：
// 1. objectService.findAllById([1L, 2L, 3L])  - 1次RPC调用
// 2. objectService.saveAll([obj1, obj2])      - 1次RPC调用
// 总计：2次RPC调用 vs 原来的5次
```

### 2.3 ResultFuture - 实现JDK Future接口

```java
// ResultFuture实现标准的Future接口
ResultFuture<BomLine> bomFuture = pipeline.findById(1001L);

// 使用标准Future API
System.out.println(bomFuture.isDone());        // false - 未执行
System.out.println(bomFuture.isCancelled());   // false

// 执行Pipeline
pipeline.execute();

// 现在可以获取真实值
System.out.println(bomFuture.isDone());        // true - 已完成
BomLine bomLine = bomFuture.get();              // 获取真实值，同步阻塞
BomLine bomLine2 = bomFuture.get(1, TimeUnit.SECONDS); // 带超时的获取

// 异常处理
try {
    BomLine bomLine = bomFuture.get();
} catch (ExecutionException e) {
    Throwable cause = e.getCause();  // 获取实际异常
    log.error("操作失败", cause);
}
```

### 2.4 跨Service的Pipeline支持

不同的Service都可以提供Pipeline支持：

```java
// ObjectService Pipeline
ObjectServicePipeline objectPipeline = ObjectService.pipeline();
ResultFuture<BomLine> bomFuture = objectPipeline.findById(1001L);

// AssociateRelationService Pipeline  
AssociateRelationServicePipeline relationPipeline = AssociateRelationService.pipeline();
ResultFuture<List<Relation>> relationsFuture = relationPipeline.findRelations(bomObject, RelationType.BOM_CHILD);
ResultFuture<Void> appendFuture = relationPipeline.appendRelation(parent, RelationType.BOM_CHILD, child);

// 可以混合执行不同service的pipeline
objectPipeline.execute();     // 执行对象操作
relationPipeline.execute();   // 执行关系操作

// 获取结果
BomLine bom = bomFuture.get();
List<Relation> relations = relationsFuture.get();
```

## 3. BatchOperatorDSL批量操作DSL

### 3.1 设计理念

BatchOperatorDSL是为了简化BatchOperator的实现而设计的DSL框架，支持：

- **Tuple多参数提取**：支持1-5个参数的类型安全提取
- **类型安全的API**：编译时类型检查，避免运行时类型错误
- **优雅的链式调用**：直观的API设计，代码可读性高
- **分组执行支持**：支持按键分组和按对象类型分组
- **统一的异常处理**：自动处理参数提取和类型转换异常

### 3.2 单参数提取 - 简化版API

```java
// ✅ 单参数提取，直接返回List<T>，不使用Tuple
public class SimpleCheckoutOperator implements BatchOperator<OperationDescriptor, ModelObject> {
    @Override
    public List<ModelObject> executeBatch(List<OperationDescriptor> operations) {
        return BatchOperatorDSL.batch(operations)
                .named("simpleCheckout")
                .extract("modelObject", ModelObject.class)  // 指定参数名和类型
                .andExecute(modelObjects -> {               // 直接获得List<ModelObject>
                    // 批量处理逻辑
                    List<ModelObject> results = new ArrayList<>();
                    for (ModelObject obj : modelObjects) {
                        // 处理单个对象
                        obj.set("status", "checked_out");
                        results.add(obj);
                    }
                    return results;
                });
    }
}
```

### 3.3 多参数提取 - Tuple版API

```java
// ✅ 多参数提取，使用Tuple封装多个参数
public class CheckoutOperator implements BatchOperator<OperationDescriptor, ModelObject> {
    @Override
    public List<ModelObject> executeBatch(List<OperationDescriptor> operations) {
        return BatchOperatorDSL.batch(operations)
                .named("checkout")
                .extract("modelObject", ModelObject.class,     // 第1个参数
                        "comment", String.class,               // 第2个参数
                        "expiryMinutes", Integer.class)        // 第3个参数
                .andExecute(tuples -> {                        // 获得List<Tuple3<ModelObject, String, Integer>>
                    List<ModelObject> results = new ArrayList<>();
                    
                    for (Tuple3<ModelObject, String, Integer> tuple : tuples) {
                        ModelObject modelObject = tuple.first();   // 类型安全的访问
                        String comment = tuple.second();
                        Integer expiryMinutes = tuple.third();
                        
                        // 使用所有参数进行处理
                        CheckoutInfo checkoutInfo = CheckoutInfo.createCheckout(
                                comment, expiryMinutes != null ? expiryMinutes : 0);
                        modelObject.set(Checkoutable.ATTRIBUTE_CHECKOUT, checkoutInfo);
                        results.add(modelObject);
                    }
                    return results;
                });
    }
}
```

### 3.4 支持的Tuple类型

BatchOperatorDSL支持1-5个参数的类型安全提取：

```java
// 双参数 - Tuple2
.extract("param1", Type1.class, "param2", Type2.class)
.andExecute(tuples -> {  // List<Tuple2<Type1, Type2>>
    for (Tuple2<Type1, Type2> tuple : tuples) {
        Type1 first = tuple.first();
        Type2 second = tuple.second();
        // 处理逻辑
    }
    return results;
});

// 三参数 - Tuple3
.extract("param1", Type1.class, "param2", Type2.class, "param3", Type3.class)
.andExecute(tuples -> {  // List<Tuple3<Type1, Type2, Type3>>
    for (Tuple3<Type1, Type2, Type3> tuple : tuples) {
        Type1 first = tuple.first();
        Type2 second = tuple.second();
        Type3 third = tuple.third();
        // 处理逻辑
    }
    return results;
});

// 四参数 - Tuple4
.extract("p1", T1.class, "p2", T2.class, "p3", T3.class, "p4", T4.class)
.andExecute(tuples -> {  // List<Tuple4<T1, T2, T3, T4>>
    // 处理逻辑
});

// 五参数 - Tuple5
.extract("p1", T1.class, "p2", T2.class, "p3", T3.class, "p4", T4.class, "p5", T5.class)
.andExecute(tuples -> {  // List<Tuple5<T1, T2, T3, T4, T5>>
    // 处理逻辑
});
```

### 3.5 分组执行 - 自定义分组键

```java
// ✅ 按自定义键分组处理
public class GroupedCheckoutOperator implements BatchOperator<OperationDescriptor, ModelObject> {
    @Override
    public List<ModelObject> executeBatch(List<OperationDescriptor> operations) {
        return BatchOperatorDSL.batch(operations)
                .named("groupedCheckout")
                .extract("modelObject", ModelObject.class,
                        "priority", String.class)
                .groupBy(tuple -> tuple.second())          // 按优先级(第2个参数)分组
                .andExecute((priority, tuples) -> {        // BiFunction<String, List<Tuple2>, List<R>>
                    log.info("Processing {} objects with priority: {}", tuples.size(), priority);
                    
                    List<ModelObject> results = new ArrayList<>();
                    for (Tuple2<ModelObject, String> tuple : tuples) {
                        ModelObject obj = tuple.first();
                        
                        // 根据优先级设置不同策略
                        int expiryMinutes = "HIGH".equals(priority) ? 120 : 60;
                        CheckoutInfo info = CheckoutInfo.createCheckout("Priority: " + priority, expiryMinutes);
                        obj.set(Checkoutable.ATTRIBUTE_CHECKOUT, info);
                        results.add(obj);
                    }
                    return results;
                });
    }
}
```

## 4. 事务性与执行模式设计

### 4.1 配置选项

#### 关闭事务控制：`.disableTransaction()` 默认`false`

平台也会根据实际的操作判定是否`只读`事务，所以该配置只是期望pipeline中每个操作都是独立提交更改时才需要配置

```java
// ✅ 显式关闭事务
ObjectServicePipeline pipeline = ObjectService.pipeline()
    .disableTransaction();  // 显式关闭事务支持

ResultFuture<BomLine> bomFuture = pipeline.findById(1001L);
ResultFuture<BomLine> saveFuture = pipeline.save(modifiedBom);
ResultFuture<Void> deleteFuture = pipeline.delete(obsoleteBom);

// execute()时：1) 提交到服务器执行  2) 关闭默认的事务支持
pipeline.execute();  // 事务边界：要么全部成功，要么全部回滚
```

#### 确认顺序执行：`.sequential()` 默认 `false`

```java
// ✅ 强制按顺序执行
ObjectServicePipeline pipeline = ObjectService.pipeline()
    .sequential();  // 强制顺序执行，禁用服务器端优化

ResultFuture<BomLine> step1 = pipeline.save(bomLine1);     // 第1步
ResultFuture<BomLine> step2 = pipeline.save(bomLine2);     // 第2步（依赖第1步）
ResultFuture<BomLine> step3 = pipeline.save(bomLine3);     // 第3步（依赖第2步）

pipeline.execute();  // 服务器严格按1→2→3顺序执行，不合并不并发

// ❌ 不使用sequential()的效果：
// 服务器可能会：
// - 将相同操作合并成批量操作
// - 多线程并发执行以提升性能  
// - 执行顺序不可预测
```

#### 配置组合

```java
// 最安全的配置：事务 + 顺序执行
ObjectServicePipeline pipeline = ObjectService.pipeline()
    .sequential();     // 顺序保证

// 高性能批量配置（默认）
ObjectServicePipeline batchPipeline = ObjectService.pipeline();

// 默认：按需的事务 + 服务器端优化（合并+并发）
```

### 4.2 事务边界详解

默认开启事务，是否只读平台会自动根据操作进行判定，可以通过`.disableTransaction()`关闭事务。

```java
// 场景1：需要事务保证的业务操作
ObjectServicePipeline transactionalPipeline = ObjectService.pipeline(); // 默认开启事务

ResultFuture<BomLine> bomFuture = transactionalPipeline.findById(1001L); // 标记需要读写事务
ResultFuture<BomLine> saveFuture = transactionalPipeline.save(modifiedBom); // 标记需要读写事务
ResultFuture<Void> deleteFuture = transactionalPipeline.delete(obsoleteBom); // 标记需要读写事务

transactionalPipeline.execute();  
// ✅ 事务执行：查询→保存→删除，任何一步失败都会回滚
```

### 4.3 执行模式对比

一次`pipeline.execute()`为一次RPC调用，而且为一次事务(如果没有强制禁用事务)

| 配置 | 事务保证 | 执行顺序 | 服务器优化 | 性能 | 适用场景               |
|------|-------|----------|------------|------|--------------------|
| `pipeline()` | ✅ | 不保证 | ✅ 合并+并发 | 🔥🔥 | 批量查询/高吞吐           |
| `pipeline().disableTransaction()` | ❌ | 不保证 | ✅ 合并+并发 | 🔥🔥 | 无需读写事务             |
| `pipeline().sequential()` | ✅ | ✅ 严格 | ❌ 禁用 | 🔥 | 有依赖的操作             |

## 5. 跨Service的CompositePipeline

### 5.1 CompositePipeline设计理念

当业务操作需要调用多个不同的Service时，使用`CompositePipeline`可以将多个服务的Pipeline组合在一起，统一管理事务边界和执行顺序。

### 5.2 基本用法

```java
// 创建多个服务的Pipeline
ObjectServicePipeline objectPipeline = ObjectService.pipeline();
AssociateRelationServicePipeline relationPipeline = AssociateRelationService.pipeline();
MaterialServicePipeline materialPipeline = MaterialService.pipeline();

// 添加各自的操作
ResultFuture<BomLine> bomFuture = objectPipeline.findById(1001L);
ResultFuture<Material> materialFuture = materialPipeline.findById(2001L);
ResultFuture<List<Relation>> relationsFuture = relationPipeline.findRelations(bomObject, RelationType.BOM_CHILD);

// 使用CompositePipeline组合执行
CompositePipeline composite = CompositePipeline.of(objectPipeline, relationPipeline, materialPipeline)
    .execute();

// 获取结果
BomLine bom = bomFuture.get();
Material material = materialFuture.get();
List<Relation> relations = relationsFuture.get();
```

### 5.3 复杂业务场景示例

```java
public void updateBomWithRelationsAndMaterials(BomLine bomLine, List<Material> materials) {
    
    // 第一阶段：查询现有数据
    ObjectServicePipeline queryPipeline = ObjectService.pipeline();
    AssociateRelationServicePipeline relationQueryPipeline = AssociateRelationService.pipeline();
    
    ResultFuture<BomLine> existingBomFuture = queryPipeline.findById(bomLine.getId());
    ResultFuture<List<Relation>> existingRelationsFuture = relationQueryPipeline.findRelations(
        bomLine, RelationType.BOM_MATERIAL);
    
    // 执行查询阶段
    CompositePipeline queryComposite = CompositePipeline.of(queryPipeline, relationQueryPipeline)
        .execute();
    
    // 处理业务逻辑
    BomLine existingBom = existingBomFuture.get();
    List<Relation> existingRelations = existingRelationsFuture.get();
    
    // 第二阶段：执行更新操作
    ObjectServicePipeline updatePipeline = ObjectService.pipeline();
    AssociateRelationServicePipeline relationUpdatePipeline = AssociateRelationService.pipeline();
    MaterialServicePipeline materialPipeline = MaterialService.pipeline();
    
    // 添加更新操作
    ResultFuture<BomLine> saveBomFuture = updatePipeline.save(bomLine);
    
    // 清理旧关系
    ResultFuture<Void> clearRelationsFuture = relationUpdatePipeline.removeRelations(
        bomLine, RelationType.BOM_MATERIAL);
    
    // 创建新材料和关系
    List<ResultFuture<Material>> materialFutures = materials.stream()
        .map(materialPipeline::save)
        .collect(Collectors.toList());
    
    List<ResultFuture<Relation>> relationFutures = materials.stream()
        .map(material -> relationUpdatePipeline.appendRelation(
            bomLine, RelationType.BOM_MATERIAL, material))
        .collect(Collectors.toList());
    
    // 按顺序执行更新：先保存BOM → 清理关系 → 保存材料 → 创建新关系
    CompositePipeline updateComposite = CompositePipeline.of(
            updatePipeline,         // 保存BOM
            relationUpdatePipeline, // 关系操作（清理+创建）
            materialPipeline        // 保存材料
        )
        .sequential()      // 必须按顺序执行
        .execute();
    
    // 验证结果
    BomLine savedBom = saveBomFuture.get();
    materialFutures.forEach(future -> {
        try {
            Material savedMaterial = future.get();
            log.info("材料保存成功: {}", savedMaterial.getId());
        } catch (ExecutionException e) {
            log.error("材料保存失败", e.getCause());
            throw new RuntimeException("材料保存失败", e.getCause());
        }
    });
}
```

### 5.4 CompositePipeline最佳实践

1. **按业务阶段分组**：将相关的操作放在同一个Pipeline中，不同阶段使用不同的CompositePipeline

2. **合理使用sequential()**：只有当Pipeline之间有严格依赖关系时才使用sequential()

3. **事务边界控制**：考虑清楚是否需要全局事务，过大的事务可能影响性能

4. **错误处理**：CompositePipeline的错误会包含所有子Pipeline的错误信息

## 6. ModelObjectList批量属性加载

### 6.1 为什么需要ModelObjectList？

在处理ModelObject集合时，经常遇到**属性N+1查询问题**：

```java
// ❌ 问题：逐个加载属性，产生大量RPC调用
List<ItemRevision> revisions = getItemRevisions();  // 假设100个对象
for (ItemRevision revision : revisions) {
    String spec = revision.get(CADComponent.ATTR_SPECIFICATION);  // 每次都是RPC调用！
    // 100个对象 = 100次RPC调用
}
```

### 6.2 ModelObjectList解决方案

```java
// ✅ 使用ModelObjectList批量加载属性
ModelObjectList.of(needRevisionItems.stream()
    .map(ItemEntity::getItemRevision)
    .toList())
    .batchLoad(CADComponent.ATTR_SPECIFICATION)  // 指定需要加载的属性
    .ensureLoaded();  // 触发批量加载，只产生1-2次RPC调用

// 现在访问属性不再产生RPC调用
for (ItemRevision revision : revisions) {
    String spec = revision.get(CADComponent.ATTR_SPECIFICATION);  // 从本地缓存获取
}
```

### 6.3 支持的属性类型

ModelObjectList自动识别并优化不同类型的属性加载：

1. **关系属性**：通过AssociateRelationService批量加载
2. **Xpath属性**：通过XpathService批量计算
3. **普通属性**：直接批量查询

### 6.4 链式批量加载

```java
// 可以链式指定多个属性
ModelObjectList.of(bomLines)
    .batchLoad(BomLine.ATTR_MATERIAL, BomLine.ATTR_QUANTITY)
    .batchLoad(BomLine.ATTR_UNIT)  // 可以分多次指定
    .ensureLoaded();
```

### 6.5 性能优势

| 对象数量 | 属性数量 | 传统方式RPC调用 | ModelObjectList RPC调用 | 性能提升 |
|---------|---------|---------------|----------------------|----------|
| 100     | 2       | 200次         | 2-4次                | 50-100倍 |
| 1000    | 3       | 3000次        | 3-6次                | 500-1000倍 |


## 7. 最佳实践

### 7.1 Pipeline使用原则

1. **配置选择**：根据业务需求选择正确的配置
   - 有顺序依赖 → 使用 `.sequential()`
   - 禁止事务 → 使用 `.disableTransaction()`
   - 高性能批量 → 使用默认配置（合并+并发）

2. **数据量阈值**：当处理超过5条记录时，优先考虑Pipeline

3. **分阶段执行**：不同类型的操作分不同的Pipeline执行

4. **跨服务协调**：使用CompositePipeline统一管理多个服务的Pipeline

5. **避免嵌套Pipeline**：不要在ResultFuture结果处理中再创建新的RPC调用

6. **合理分批**：单个Pipeline处理的数据量建议不超过1000条

7. **面向对象ResultFuture管理**：利用ModelObject继承KeyValueObject的特性

8. **ModelObjectList属性批量加载**：解决ModelObject属性N+1查询问题

### 7.2 面向对象ResultFuture管理最佳实践

#### 7.2.1 设计理念

由于EMOP中的`ModelObject`支持key-value形式的扩展属性，我们可以直接将`ResultFuture`存储到业务对象中，实现真正面向对象的Pipeline设计。

#### 7.2.2 面向对象方式

```java
pipeline.queryRevision(
        new Revisionable.CriteriaByCodeAndRevId<>(CADComponent.class.getName(),
        item.getItemCode(), item.getRevId())).bindTo(item);

pipeline.execute();

ResultFuture<ItemRevision> future = ResultFuture.extractFrom(item);
```
#### 7.2.3 核心优势

**1. 消除复杂数据结构**：
- ❌ 不需要：`Map<ItemEntity, ResultFuture>`
- ❌ 不需要：`List<ResultFuture>` + 索引管理
- ❌ 不需要：分组处理逻辑
- ✅ 只需要：简单的for循环 + 对象自身属性

**2. 真正的面向对象设计**：
- 数据和操作结果绑定在同一个对象中
- 充分利用继承KeyValueObject的能力
- 代码逻辑更直观，易于理解

**3. 内存效率**：
- 用完即清理，避免长期持有Future
- 无需额外的映射数据结构开销

#### 7.2.4 适用场景

这种模式特别适合以下场景：

1. **批量查询关联数据**：如批量加载ItemRevision、Material、BomLine等
2. **批量更新对象属性**：如批量设置权限、状态等
3. **复杂业务对象处理**：涉及多步Pipeline操作的业务对象

### 7.3 BatchOperatorDSL使用原则

1. **参数提取选择**：
   - 单参数 → 使用简化版API，直接获得`List<T>`
   - 多参数 → 使用Tuple版API，获得`List<TupleN<...>>`

2. **分组策略选择**：
   - 无分组 → 直接使用`.andExecute()`
   - 自定义分组 → 使用`.groupBy(keyExtractor)`
   - 对象类型分组 → 使用`.groupByObjectType()`

3. **类型安全**：
   - 始终明确指定参数类型：`.extract("param", ParamType.class)`
   - 利用编译时类型检查避免运行时错误

4. **操作命名**：
   - 使用`.named("operationName")`提供清晰的日志输出
   - 便于问题定位和性能监控

5. **面向对象Future管理**：优先考虑将Future存储到业务对象中

## 8. 性能基准

### 8.1 测试环境

- 网络延迟：1ms
- 数据库查询时间：平均5ms
- 业务处理时间：平均2ms per record

### 8.2 性能对比结果

| 数据量 | 传统方式总时间 | Pipeline总时间 | 性能提升 | 网络时间节省 |
|--------|---------------|---------------|----------|-------------|
| 10条   | 80ms          | 15ms          | 5.3倍    | 28ms        |
| 100条  | 800ms         | 52ms          | 15.4倍   | 298ms       |
| 1000条 | 8000ms        | 520ms         | 15.4倍   | 2998ms      |
| 5000条 | 40000ms       | 2520ms        | 15.9倍   | 14998ms     |

## 9. 注意事项

### 9.1 使用限制

1. **RPC环境限制**：Pipeline设计专门针对RPC环境，不适用于本地方法调用
2. **同步执行**：ResultFuture是同步执行的，不是异步的
3. **事务边界**：Pipeline内部的操作在同一个事务中
4. **顺序依赖**：如果操作之间有依赖关系，需要分多个Pipeline执行或使用CompositePipeline

### 9.2 常见陷阱

#### 陷阱1：在ResultFuture结果处理中嵌套RPC调用

```java
// ❌ 错误：在结果处理中仍然逐个调用RPC
ObjectServicePipeline pipeline = ObjectService.pipeline();
List<ResultFuture<Item>> futures = ids.stream()
    .map(pipeline::findById)
    .collect(Collectors.toList());
pipeline.execute();

for (ResultFuture<Item> future : futures) {
    Item item = future.get();
    // 这里仍然是逐个RPC调用，没有优化效果！
    RelatedObject related = anotherService.findById(item.getRelatedId());
    processWithRelated(item, related);
}

// ✅ 正确：分阶段批量处理
ObjectServicePipeline itemPipeline = ObjectService.pipeline();
List<ResultFuture<Item>> itemFutures = ids.stream()
    .map(itemPipeline::findById)
    .collect(Collectors.toList());

AnotherServicePipeline relatedPipeline = anotherService.pipeline();

// 使用CompositePipeline执行查询阶段
CompositePipeline queryComposite = CompositePipeline.of(itemPipeline, relatedPipeline)
    .execute();

// 收集关联ID并添加到第二个pipeline
Set<Long> relatedIds = itemFutures.stream()
    .map(ResultFuture::get)
    .map(Item::getRelatedId)
    .collect(Collectors.toSet());

Map<Long, ResultFuture<RelatedObject>> relatedFutures = relatedIds.stream()
    .collect(Collectors.toMap(id -> id, relatedPipeline::findById));

// 处理结果
for (ResultFuture<Item> itemFuture : itemFutures) {
    Item item = itemFuture.get();
    RelatedObject related = relatedFutures.get(item.getRelatedId()).get();
    processWithRelated(item, related);
}
```

#### 陷阱2：配置选择错误

```java
// ❌ 错误：有顺序依赖但未使用sequential()
ObjectServicePipeline pipeline = ObjectService.pipeline();  // 默认并发执行

// 这些操作有严格依赖关系，但服务器可能并发执行！
ResultFuture<BomLine> parentFuture = pipeline.save(parentBom);      // 必须先创建
ResultFuture<Relation> relationFuture = pipeline.appendRelation(    // 依赖parent存在
    parentBom, RelationType.BOM_CHILD, childBom);

pipeline.execute();  // 可能出现：relationFuture在parentFuture之前执行，导致失败

// ✅ 正确：有依赖关系必须使用sequential()或CompositePipeline
ObjectServicePipeline savePipeline = ObjectService.pipeline();
AssociateRelationServicePipeline relationPipeline = AssociateRelationService.pipeline();

ResultFuture<BomLine> parentFuture = savePipeline.save(parentBom);
ResultFuture<Relation> relationFuture = relationPipeline.appendRelation(
    parentBom, RelationType.BOM_CHILD, childBom);

// 使用CompositePipeline确保顺序
CompositePipeline.of(savePipeline, relationPipeline)
    .sequential()    // 先执行savePipeline，再执行relationPipeline
    .execute();
```

#### 陷阱3：忘记调用execute()

```java
// ❌ 错误：忘记调用execute()
ObjectServicePipeline pipeline = ObjectService.pipeline();
ResultFuture<Item> future = pipeline.findById(1001L);
Item item = future.get();  // 抛出异常：结果尚未可用

// ✅ 正确：先execute()再get()
ObjectServicePipeline pipeline = ObjectService.pipeline();
ResultFuture<Item> future = pipeline.findById(1001L);
pipeline.execute();        // 执行Pipeline
Item item = future.get();  // 获取结果
```

## 10. 优化指南

### 10.1 识别优化机会

使用以下检查清单识别需要Pipeline优化的代码：

- [ ] 是否有for循环调用RPC服务？
- [ ] 是否逐个查询对象然后处理？
- [ ] 是否逐个保存处理结果？
- [ ] 是否逐个检查对象存在性？
- [ ] 处理的数据量是否超过5条？
- [ ] 是否可以区分只读和读写操作？
- [ ] 是否涉及多个Service的协调操作？

### 10.2 优化策略

#### 第一步：识别批量处理场景

```java
// 🔍 搜索这些模式
for (Long id : ids) {
    service.findById(id);     // 查找这种模式
    service.save(object);     // 查找这种模式
    service.exists(id);       // 查找这种模式
}

// 🔍 搜索跨服务调用模式
objectService.save(obj);
relationService.append(obj, relation);  // 可能需要CompositePipeline
```

#### 第二步：评估优先级

| 优先级 | 场景描述 | 预期提升 |
|--------|----------|----------|
| 高 | 批量数据处理、BOM展开、跨服务事务 | 100倍+ |
| 中 | 列表页面数据加载、多服务数据聚合 | 10-50倍 |
| 低 | 单条记录操作 | 2-5倍 |

#### 第三步：选择正确的Pipeline配置

```java
// 单Service配置选择决策树
if (纯查询操作) {
    使用 service.pipeline()  // 性能最优
} else if (读写操作) {
    if (操作之间有依赖关系) {
        使用 service.pipeline().sequential()  // 顺序执行
    } else if (禁用事务) {
        使用 service.pipeline().disableTransaction()  // 禁用事务
    } else {
        使用 service.pipeline()  // 默认配置
    }
}
```

### 10.3 配置对照表

| 配置组合 | 使用场景 |
|----------|----------|
| `pipeline()` | 高性能批量处理，开启事务（默认） |
| `pipeline().disableTransaction()` | 关闭事务 |
| `pipeline().sequential()` | 有顺序依赖的操作 |

**记住：在EMOP分布式RPC环境中，Pipeline是解决网络延迟问题的最佳选择！对于跨服务操作，CompositePipeline提供了统一的事务和执行控制！**