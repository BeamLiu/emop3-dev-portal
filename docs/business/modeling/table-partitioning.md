# 业务分表存储优化

## 概述

在大规模系统中，单表数据量过大会严重影响系统性能。通过业务建模的继承机制，可以实现基于业务维度的分表存储，将不同类型的业务数据存储在不同的物理表中，从而提升查询和写入性能。

这种分表策略的核心思想是：**将传统数据库分区的业务键转化为模型继承的类型体系**，通过面向对象的继承关系自然地实现数据的物理隔离。

## 适用场景

分表存储特别适合以下场景：

- **数据量大**：某一类业务数据大且可从业务上大致进行分布，如CAD BOM数据非常大（Creo设计文件占70%，其他CAD类型占30%），可以将 Creo 相关数据单独存储
- **访问模式差异**：不同类型数据的查询频率和模式存在显著差异
- **性能隔离需求**：希望某类高频访问数据不受其他数据影响
- **定制化需求**：不同类型需要不同的字段或业务逻辑

## 实现原理

### 基本思路

通过DSL或Java定义子类型，为每个子类型指定独立的物理表：

```
父类型（基表）
  ├── 子类型A（表A） - 存储业务类型A的数据
  ├── 子类型B（表B） - 存储业务类型B的数据
  └── 子类型C（表C） - 存储业务类型C的数据
```

### 关键要素

1. **继承关系**：子类型继承父类型的所有属性和行为
2. **独立表名**：通过`table`和`schema`属性指定物理存储位置
3. **类型识别**：系统根据对象类型自动路由到对应的表
4. **关系维护**：子类型需要覆盖父类型的关联关系，确保类型一致性

## 实战案例

### 案例1：CAD BOM数据分表

#### 业务背景

某制造企业使用多种CAD工具进行设计：
- Creo占设计数据的70%
- SolidWorks、CATIA等其他工具占30%

Creo数据访问频繁，且有特殊的定制需求，因此将其单独存储。

#### 模型定义

```dsl
// 定义Creo专用的BOM行类型
create type sample.CreoCADBomLine extends BomLine {
    // 覆盖父类的children关系，确保返回类型为CreoCADBomLine
    -> sample.CreoCADBomLine[] as children {
        foreignKey: parentId
    }
    
    // 覆盖父类的parent关系
    -> sample.CreoCADBomLine as `parent` {
        foreignKey: parentId
    }
    
    multilang {
        name.zh_CN: "CREO集成BOM行"
        name.en_US: "CREO BOM Line"
        description.zh_CN: "CREO集成BOM行，单独存储以达到定制化和分表的目的"
    }
    
    // 指定独立的物理表
    table: CREO_CAD_BomLine
    schema: SAMPLE
} if not exists

// 定义Creo专用的BOM视图类型
create type sample.CreoCADBomView extends BomView {
    // 覆盖topline关系，指向CreoCADBomLine
    -> sample.CreoCADBomLine as topline {
        foreignKey: toplineId
    }
    
    multilang {
        name.zh_CN: "CREO集成BOM视图"
        name.en_US: "CREO BOM View"
        description.zh_CN: "CREO集成BOM视图，单独存储以达到定制化和分表的目的"
    }
    
    table: CREO_CAD_BomView
    schema: SAMPLE
} if not exists
```

#### 业务逻辑实现

通过扩展点动态创建不同类型的对象：

```java
@Component
public class CustomBomStructureProcessor implements CadBomStructureProcessor {
    
    /**
     * 根据CAD类型返回对应的BomView实例
     */
    @Override
    public BomView resolveBomView(ItemEntity rootItem) {
        if ("creo".equalsIgnoreCase(CadContext.currentClient())) {
            // Creo数据存储到CreoCADBomView表
            return new BomView("sample.CreoCADBomView");
        } else {
            // 其他CAD数据存储到默认BomView表
            return new BomView(BomView.class.getName());
        }
    }

    /**
     * 根据CAD类型返回对应的BomLine实例
     */
    @Override
    public BomLine resolveBomLine(ItemEntity entity) {
        if ("creo".equalsIgnoreCase(CadContext.currentClient())) {
            return new BomLine("sample.CreoCADBomLine");
        } else {
            return new BomLine(BomLine.class.getName());
        }
    }
}
```

#### 效果

- Creo的BOM数据存储在`SAMPLE.CREO_CAD_BomLine`和`SAMPLE.CREO_CAD_BomView`表中
- 其他CAD的BOM数据存储在默认的`BOM.BomLine`和`BOM.BomView`表中
- 查询Creo数据时只扫描Creo专用表，性能显著提升
- 可以针对Creo表进行独立的索引优化和维护

### 案例2：物料数据分表

#### 业务背景

物料数据量巨大，且不同类型的物料有不同的管理方式：
- 标准件：外购，数据量大，查询频繁
- 外购件：需要供应商管理，中等数据量
- 自制件：需要工艺管理，数据量最大

#### 模型定义

```dsl
// 标准件
create type sample.StandardMaterial extends Material {
    String supplierCode {
        multilang {
            name.zh_CN: "供应商编码"
        }
    }
    
    multilang {
        name.zh_CN: "标准件"
        name.en_US: "Standard Part"
    }
    
    table: STANDARD_MATERIAL
    schema: SAMPLE
} if not exists

// 外购件
create type sample.PurchasedMaterial extends Material {
    String supplierCode {
        multilang {
            name.zh_CN: "供应商编码"
        }
    }
    
    Decimal leadTime {
        multilang {
            name.zh_CN: "采购周期(天)"
        }
    }
    
    multilang {
        name.zh_CN: "外购件"
        name.en_US: "Purchased Part"
    }
    
    table: PURCHASED_MATERIAL
    schema: SAMPLE
} if not exists

// 自制件
create type sample.ManufacturedMaterial extends Material {
    String processRoute {
        multilang {
            name.zh_CN: "工艺路线"
        }
    }
    
    Decimal manufacturingCost {
        multilang {
            name.zh_CN: "制造成本"
        }
    }
    
    multilang {
        name.zh_CN: "自制件"
        name.en_US: "Manufactured Part"
    }
    
    table: MANUFACTURED_MATERIAL
    schema: SAMPLE
} if not exists
```

#### 业务逻辑

```java
public Material createMaterial(String materialType, Map<String, Object> properties) {
    Material material = switch (materialType) {
        case "STANDARD" -> new Material("sample.StandardMaterial");
        case "PURCHASED" -> new Material("sample.PurchasedMaterial");
        case "MANUFACTURED" -> new Material("sample.ManufacturedMaterial");
        default -> new Material(Material.class.getName());
    };
    
    // 设置属性
    material.setProperties(properties);
    
    return material;
}
```

## 性能优化效果

### 优势

1. **查询性能提升**
   - 单表数据量减少，索引更高效
   - 减少无关数据扫描
   - 提高缓存命中率

2. **写入性能提升**
   - 减少表锁竞争
   - 降低索引维护开销
   - 提高并发写入能力

3. **维护便利性**
   - 可以针对不同表制定不同的维护策略
   - 数据归档和清理更灵活
   - 问题隔离更容易

4. **扩展性增强**
   - 不同类型可以有不同的字段
   - 便于后续添加新的业务类型
   - 支持独立的性能调优

## 注意事项与权衡

### 跨表查询的性能损失

当需要根据业务键（如编码）查询所有类型的数据时，需要跨多个表查询并合并结果：

```java
// 需要查询所有表并合并
List<Material> allMaterials = new ArrayList<>();
allMaterials.addAll(queryByCode("sample.StandardMaterial", code));
allMaterials.addAll(queryByCode("sample.PurchasedMaterial", code));
allMaterials.addAll(queryByCode("sample.ManufacturedMaterial", code));
```

这种场景下会有性能损失，需要权衡：
- 如果按类型查询是主要场景（80%以上），分表收益大
- 如果跨类型查询频繁，需要考虑其他方案（如增加类型索引字段）

### 关系维护的复杂性

子类型必须覆盖父类型的所有关联关系，确保类型一致性：

```dsl
// 错误示例：未覆盖关系，会导致类型不匹配
create type sample.CreoCADBomLine extends BomLine {
    // 缺少children和parent的覆盖定义
    table: CREO_CAD_BomLine
}

// 正确示例：完整覆盖关系
create type sample.CreoCADBomLine extends BomLine {
    -> sample.CreoCADBomLine[] as children {
        foreignKey: parentId
    }
    -> sample.CreoCADBomLine as `parent` {
        foreignKey: parentId
    }
    table: CREO_CAD_BomLine
}
```

### 数据迁移成本

从单表迁移到分表需要：
1. 数据迁移脚本
2. 历史数据处理
3. 应用代码适配
4. 充分的测试验证

建议在系统设计初期就考虑分表策略，避免后期迁移成本。

## 总结

业务分表存储是一种有效的性能优化手段，通过模型继承机制可以优雅地实现数据的物理隔离。关键是要根据实际业务场景权衡收益和成本，选择合适的分表策略。

对于数据量大、访问模式差异明显的场景，分表存储可以带来显著的性能提升；但对于跨类型查询频繁的场景，需要谨慎评估或采用其他优化方案。
