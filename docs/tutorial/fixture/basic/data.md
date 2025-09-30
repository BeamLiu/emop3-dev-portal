# 工装夹具库管理系统 - 数据操作开发

本文将指导您完成工装夹具库管理系统的数据操作开发，包括使用DSL和REST API进行数据管理。

## 1. 编码规则配置

首先需要为Category和Fixture设置编码规则。我们使用DSL更新类型定义，DSL执行方式见[这里](../../../business/dsl/guide.md#dsl执行方式)：

```sql
// 更新夹具分类的编码规则
update type Category {
    codeGenPattern: """CAT-${date(pattern="YY")}-${autoIncrease(scope="Rule",start="100",max="999")}"""
}

// 更新夹具的编码规则
update type Fixture {
    codeGenPattern: """${attr(name="category.code")}-${autoIncrease(scope="PrePath",start="2000",max="9999")}"""
}

// 更新产品的编码规则
update type Product {
    codeGenPattern: """PROD-${date(pattern="YY")}-${autoIncrease(scope="Rule",start="100",max="999")}"""
}
```

验证编码规则是否生效：
```bash
//在各自输出的json中应该包含正确的codeGenPattern定义
curl http://localhost:870/webconsole/api/metadata/types/io.emop.model.sample.Category
curl http://localhost:870/webconsole/api/metadata/types/io.emop.model.sample.Fixture
curl http://localhost:870/webconsole/api/metadata/types/io.emop.model.sample.Product
```

## 2. 导入初始数据

### 2.1 准备分类数据
创建 `category.csv` 文件：

```csv
编号,名称,父分类,状态,描述
CAT-25-001,通用夹具,,Active,各类通用工装夹具
CAT-25-002,钻夹具,CAT-25-001,Active,用于钻孔的专用夹具
CAT-25-003,铣夹具,CAT-25-001,Active,用于铣削的专用夹具
CAT-25-004,车夹具,CAT-25-001,Active,用于车削的专用夹具
CAT-25-005,磨夹具,CAT-25-001,Active,用于磨削的专用夹具
CAT-25-006,检具,,Active,各类检验工装
CAT-25-007,尺寸检具,CAT-25-006,Active,用于尺寸检验的工装
CAT-25-008,形位检具,CAT-25-006,Active,用于形位公差检验的工装
```

使用DSL导入分类数据：
```bash
# 创建DSL文件
cat > import_category.dsl << 'EOF'
import tree Category "category.csv" {
    parent "父分类" -> "编号"
    column "编号" -> code
    column "名称" -> name
    column "状态" -> status
    column "描述" -> description
} with {
    existence: update,
    relationType: "children"
}
EOF

# 执行DSL并上传CSV文件
curl -X POST "http://localhost:870/webconsole/api/dsl/execute" \
	 -H 'x-user: {"userId":1,"authorities":["ADMIN"]}' \
     -H "Content-Type: multipart/form-data" \
     -F "files=@category.csv" \
     -F "dsl=@import_category.dsl"
```

:::info Shell脚本支持
需要linux shell支持, windows环境可以在git bash中执行, 或者手工在postman之类的GUI工具中执行
:::

使用DSL查询导入的数据：
```
show object Category(code in ('CAT-25-001', 'CAT-25-006')) as tree with {
    maxDepth: 10,
    attributes: [code, name, description]
}
```
输出可读结构化的数据如下：
```
[Found 2 object(s): [
    1
  ] 
Category (ID: -8621190374644969472, code: "CAT-25-001", name: "通用夹具", description: "各类通用工装夹具")
  └─ Category (ID: -8621190357465100288, code: "CAT-25-002", name: "钻夹具", description: "用于钻孔的专用夹具")
  └─ Category (ID: -8621190340285231104, code: "CAT-25-003", name: "铣夹具", description: "用于铣削的专用夹具")
  └─ Category (ID: -8621190323105361920, code: "CAT-25-004", name: "车夹具", description: "用于车削的专用夹具")
  └─ Category (ID: -8621190305925492736, code: "CAT-25-005", name: "磨夹具", description: "用于磨削的专用夹具")

[
    2
  ] 
Category (ID: -8621190288745623552, code: "CAT-25-006", name: "检具", description: "各类检验工装")
  └─ Category (ID: -8621190271565754368, code: "CAT-25-007", name: "尺寸检具", description: "用于尺寸检验的工装")
  └─ Category (ID: -8621190254385885184, code: "CAT-25-008", name: "形位检具", description: "用于形位公差检验的工装")
]
```

### 2.2 准备产品数据
创建 `product.csv` 文件：

```csv
编号,版本,名称,规格,描述
PROD-25-001,A,活塞,Φ80,发动机活塞
PROD-25-002,A,缸套,Φ82,发动机缸套
PROD-25-003,A,连杆,L200,发动机连杆
PROD-25-004,A,曲轴,L500,发动机曲轴
PROD-25-005,A,凸轮轴,L400,发动机凸轮轴
PROD-25-006,A,气门,Φ30,发动机气门
```

使用DSL导入产品数据：
```bash
# 创建DSL文件
cat > import_product.dsl << 'EOF'
import Product "product.csv" {
    column "编号" -> code
    column "版本" -> revId
    column "名称" -> name
    column "规格" -> specifications
    column "描述" -> description
} with {
    existence: update
}
EOF

# 执行DSL并上传CSV文件
curl -X POST "http://localhost:870/webconsole/api/dsl/execute" \
     -H 'x-user: {"userId":1,"authorities":["ADMIN"]}' \
     -H "Content-Type: multipart/form-data" \
     -F "files=@product.csv" \
     -F "dsl=@import_product.dsl"
```

使用DSL查询导入的数据：
```sql
show object Product(code like 'PROD-25-%')
```
输出可读结构化的数据如下：
```
[Found 6 object(s): [
    1
  ] 
Product (ID: -8519893381991424000)
Type: io.emop.model.sample.Product
Attributes:
  _creationDate: 2025-02-11 22: 18: 44.743
  _creator: 1
  _lastModifiedDate: 2025-02-11 22: 18: 44.743
  _lastModifier: 1
  _properties: 
  _state: "Draft"
  _version: 1
  code: "PROD-25-001"
  description: "发动机活塞"
  id: -8519893381991424000
  name: "活塞"
  revId: "A"
  specifications: "Φ80"
......
]
```

### 2.3 准备夹具数据
创建 `fixture.csv` 文件：

```csv
编号,版本,分类编号,夹具名称,规格,位置,状态,描述
CAT-25-002-1000,A,CAT-25-002,立式钻床专用夹具,Φ10-Φ20,A-01-01,InStock,用于立式钻床的通用夹具
CAT-25-002-1001,A,CAT-25-002,卧式钻床夹具,Φ5-Φ15,A-01-02,InStock,用于卧式钻床的专用夹具
CAT-25-003-1000,A,CAT-25-003,立铣床夹具,200x150x100,A-02-01,InStock,用于立铣床的通用夹具
CAT-25-003-1001,A,CAT-25-003,卧铣床夹具,150x100x80,A-02-02,InUse,用于卧铣床的专用夹具
CAT-25-004-1000,A,CAT-25-004,普通车床夹具,Φ50-Φ200,B-01-01,InStock,用于普通车床的通用夹具
CAT-25-004-1001,A,CAT-25-004,数控车床夹具,Φ20-Φ100,B-01-02,Maintenance,用于数控车床的专用夹具
CAT-25-005-1000,A,CAT-25-005,外圆磨床夹具,Φ10-Φ50,B-02-01,InStock,用于外圆磨床的通用夹具
CAT-25-005-1001,A,CAT-25-005,内圆磨床夹具,Φ5-Φ30,B-02-02,InStock,用于内圆磨床的专用夹具
CAT-25-007-1000,A,CAT-25-007,千分尺检具,0-25mm,C-01-01,InStock,用于精密尺寸测量
CAT-25-007-1001,A,CAT-25-007,卡尺检具,0-150mm,C-01-02,InUse,用于一般尺寸测量
CAT-25-008-1000,A,CAT-25-008,圆度仪检具,Φ5-Φ100,C-02-01,InStock,用于圆度检测
CAT-25-008-1001,A,CAT-25-008,粗糙度仪检具,Ra0.8-6.3,C-02-02,Maintenance,用于表面粗糙度检测
```

使用DSL导入夹具数据：
```sql
# 创建DSL文件
cat > import_fixture.dsl << 'EOF'
import Fixture "fixture.csv" {
    column "编号" -> code
    column "版本" -> revId
    column "分类编号" -> "category/code"
    column "夹具名称" -> name
    column "规格" -> specifications
    column "位置" -> location
    column "状态" -> status
    column "描述" -> description
} with {
    existence: update,
    allowXPathAutoCreate: true
}
EOF

# 执行DSL并上传CSV文件
curl -X POST "http://localhost:870/webconsole/api/dsl/execute" \
     -H 'x-user: {"userId":1,"authorities":["ADMIN"]}' \
     -H "Content-Type: multipart/form-data" \
     -F "files=@fixture.csv" \
     -F "dsl=@import_fixture.dsl"
```
验证导入的数据：
1. 查看所有状态为在库的夹具及其分类信息：
```
show object Fixture(status='InStock') as detail with {
    attributes: ['code', 'name', 'location', 'status', 'category/code', 'category/name']
}
```
输出示例：
```
[Found 8 object(s): [
    1
  ] 
Fixture (ID: -8508662935868727296)
Type: io.emop.model.sample.Fixture
Attributes:
  category/code: "CAT-25-008"
  category/name: "形位检具"
  code: "CAT-25-002-1000"
  location: "A-01-01"
  name: "立式钻床专用夹具"
  status: "InStock"

[
    2
  ] 
Fixture (ID: -8507176413327843328)
Type: io.emop.model.sample.Fixture
Attributes:
  category/code: "CAT-25-008"
  category/name: "形位检具"
  code: "CAT-25-002-1001"
  location: "A-01-02"
  name: "卧式钻床夹具"
  status: "InStock"
......
```
2. 按分类查看夹具树形结构：
```
show object Category(code='CAT-25-001') as tree with {
    maxDepth: 3,
    relations: ['children','fixtures'],
    attributes: ['code', 'name', 'status']
}
```
输出示例：
```
[Found 1 object(s): [
    1
  ] 
Category (ID: -7343462961565712384, code: "CAT-25-001", name: "通用夹具")
  CHILDREN:
    └─ Category (ID: -7343462721047543808, code: "CAT-25-002", name: "钻夹具")
      FIXTURES:
        └─ Fixture (ID: -7342332852593016832, code: "CAT-25-002-1000", name: "立式钻床专用夹具", status: "InStock")
        └─ Fixture (ID: -7342331237685313536, code: "CAT-25-002-1001", name: "卧式钻床夹具", status: "InStock")
    └─ Category (ID: -7343462669507936256, code: "CAT-25-003", name: "铣夹具")
      FIXTURES:
        └─ Fixture (ID: -7342331168965836800, code: "CAT-25-003-1000", name: "立铣床夹具", status: "InStock")
        └─ Fixture (ID: -7342331100246360064, code: "CAT-25-003-1001", name: "卧铣床夹具", status: "InUse")
    └─ Category (ID: -7343462652328067072, code: "CAT-25-004", name: "车夹具")
      FIXTURES:
        └─ Fixture (ID: -7342331031526883328, code: "CAT-25-004-1000", name: "普通车床夹具", status: "InStock")
        └─ Fixture (ID: -7342330979987275776, code: "CAT-25-004-1001", name: "数控车床夹具", status: "Maintenance")
    └─ Category (ID: -7343462600788459520, code: "CAT-25-005", name: "磨夹具")
      FIXTURES:
        └─ Fixture (ID: -7342330894087929856, code: "CAT-25-005-1000", name: "外圆磨床夹具", status: "InStock")
        └─ Fixture (ID: -7342330842548322304, code: "CAT-25-005-1001", name: "内圆磨床夹具", status: "InStock")
]
```


## 3. 数据操作示例

### 3.1 使用DSL操作

#### 查询操作
```sql
// 查看特定状态的夹具
show object Fixture(status = 'InStock')

// 统计每个分类下的夹具数量
execute script """
    def stats = [:]
    def fixtures = Q.objectType('Fixture').noCondition().query()
    fixtures.each { fixture ->
        def catCode = fixture.get('category/code')
        stats[catCode] = (stats[catCode] ?: 0) + 1
    }
    return stats
"""
```

#### 创建和更新操作
```sql
// 创建新分类
create object Category {
    name: "特种夹具",
    status: "Active",
    description: "特殊用途的工装夹具",
}


// 更新夹具状态
update object Fixture where location = 'A-02-02' {
    status: "InStock",
    description: "已归还"
}
```

### 3.2 使用REST API操作

#### 查询操作
```bash
# 获取分类列表
curl -X POST "http://localhost:870/webconsole/api/data/query/Category" \
     -H "Content-Type: application/json" \
     -d '{
           "conditions": [
             {"field": "status", "operator": "=", "value": "Active"}
           ],
           "orderBy": [{"field": "code", "direction": "ASC"}]
         }'

# 获取特定位置的夹具
curl -X POST "http://localhost:870/webconsole/api/data/query/Fixture" \
     -H "Content-Type: application/json" \
     -d '{
           "conditions": [
             {"field": "location", "operator": "like", "value": "A-01%"}
           ]
         }'
```

## 4. 关系管理

### 4.1 夹具与产品关联关系

#### 使用DSL创建关系
```sql
// 创建单个关系
relation Fixture(code='CAT-25-002-1001') -> Product(code='PROD-25-001') as applicableProducts

// 带属性的关系
relation Fixture(code='CAT-25-003-1001') -> Product(code='PROD-25-003') as applicableProducts {
    applicableDate: "2024-02-11",
    remarks: "用于粗加工"
}
```

