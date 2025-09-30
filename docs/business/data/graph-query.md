# 图查询(Graph Query) API

## 1. 概述

图查询API是一套基于Cypher查询语言的图数据库查询接口，它使开发人员能够直接通过Cypher语句查询、遍历和分析对象之间的复杂关系。该API专注于对象之间的"连接"或"关系"，适合处理如BOM结构、文档引用关系、产品配置等复杂网络结构。

⚠️ **注意**：用户数据更新后，图查询会存在一定的同步延迟（1秒到1分钟），实时查询请使用[引用关系查询API](frontend#引用关系查询)

主要优势：

- **直接使用Cypher**：支持完整的Cypher查询语言，功能强大
- **性能优化**：针对关系查询进行了优化，支持复杂图遍历
- **权限安全**：自动进行权限过滤，只返回用户有权限的节点和边
- **统一接口**：前后端使用统一的数据结构

## 2. 核心概念

### 2.1 基本术语

- **节点(Node)**：系统中的业务对象，如零件、文档等
- **边(Edge)**：对象之间的关系，如"包含"、"引用"等
- **图(Graph)**：由节点和边组成的网络结构
- **Cypher**：图数据库查询语言，类似SQL但专门用于图数据

### 2.2 数据同步

图查询基于Apache AGE图数据库，数据从业务系统异步同步，存在1秒到1分钟的延迟。

## 3. API使用指南

### 3.1 核心API结构

图查询API围绕`GQ`类构建，提供简洁的调用方式：

```typescript
// 执行Cypher查询
const result = await GQ.cypher('MATCH (n) RETURN n LIMIT 10');

// 搜索节点
const searchResult = await GQ.search('零件名称');

// 获取邻居节点
const neighbors = await GQ.neighbors(nodeId, 2, 50);
```
:::warning Cypher查询性能
使用cypher进行图检索非常灵活，需要注意性能，特别是要命中索引，EMOP内置的索引见[这里](../../platform/graph-search#_5-3-索引管理策略)
:::

### 3.2 基本查询语法

#### 3.2.1 查询所有节点
```cypher
MATCH (n) RETURN n LIMIT 50
```

#### 3.2.2 查询特定类型节点
```cypher
MATCH (n) WHERE n._objectType = 'Part' RETURN n LIMIT 50
```

#### 3.2.3 查询节点关系
```cypher
MATCH (n)-[r]-(m) RETURN n, r, m LIMIT 30
```

### 3.3 TypeScript/JavaScript 使用示例

```typescript
import { GQ, CypherTemplates } from '@platform/query';

// 基本查询
async function queryAllParts() {
    const result = await GQ.cypher(`
        MATCH (n) 
        WHERE n._objectType = 'Part' 
        RETURN n LIMIT 50
    `);
    
    console.log(`找到 ${result.nodes.length} 个零件`);
    return result.nodes;
}

// 带参数查询
async function findRelatedDocuments(partId: number) {
    const result = await GQ.cypher(`
        MATCH (part {id: $partId})-[:REFERENCE]-(doc)
        WHERE doc._objectType = 'Document'
        RETURN part, doc
    `, { partId });
    
    return result;
}

// 使用模板查询
async function searchNodes(keyword: string) {
    const result = await GQ.cypher(CypherTemplates.searchWithRelations, { keyword });
    return result;
}

// 获取邻居节点
async function getNeighbors(nodeId: number) {
    const result = await GQ.neighbors(nodeId, 2, 100);
    return result;
}
```

### 3.4 Java使用示例

```java
import io.emop.service.api.data.GraphQueryService;
import io.emop.service.S;

public class GraphQueryExample {
    
    // 基本查询
    public GraphQueryService.GraphQueryResult queryAllParts() {
        String cypher = "MATCH (n) WHERE n._objectType = 'Part' RETURN n LIMIT 50";
        return S.service(GraphQueryService.class).executeGraphQuery(cypher, Collections.emptyMap());
    }
    
    // 带参数查询
    public GraphQueryService.GraphQueryResult findRelatedDocuments(Long partId) {
        String cypher = """
            MATCH (part {id: $partId})-[:REFERENCE]-(doc)
            WHERE doc._objectType = 'Document'
            RETURN part, doc
        """;
        
        Map<String, Object> params = Map.of("partId", partId);
        return S.service(GraphQueryService.class).executeGraphQuery(cypher, params);
    }
}
```

## 4. 常见使用场景

### 4.1 BOM结构查询

```cypher
-- 查询BOM层级结构
MATCH (parent)-[:childTasks*1..5]->(child)
WHERE parent.id = 123456
RETURN parent, child
```

### 4.2 影响分析

```cypher
-- 查询修改某个零件会影响哪些对象
MATCH (part {id: 123456})<-[:REFERENCE*1..3]-(affected)
RETURN part, affected
LIMIT 100
```

### 4.3 文档关联分析

```cypher
-- 查询与零件相关的所有文档
MATCH (part {id: 123456})-[:REFERENCE]-(doc)
WHERE doc._objectType = 'Document'
RETURN part, doc
```

### 4.4 路径查询

```cypher
-- 查询两个对象之间的最短路径
MATCH (start {id: 123456}), (end {id: 789012})
MATCH p = shortestPath((start)-[*1..5]-(end))
RETURN p
```

## 5. 数据结构

### 5.1 查询结果格式

```typescript
interface GraphQueryResult {
    nodes: GraphNode[];           // 节点列表
    edges: GraphEdge[];           // 边列表
    summary?: QuerySummary;       // 查询摘要
    error?: string;               // 错误信息
}

interface GraphNode {
    id: string;                   // 节点ID
    label?: string;               // 显示标签
    type?: string;                // 节点类型
    properties?: Record<string, any>; // 节点属性
    style?: NodeStyle;            // 样式信息
}

interface GraphEdge {
    id: string;                   // 边ID
    source: string;               // 源节点ID
    target: string;               // 目标节点ID
    label?: string;               // 边标签
    type?: string;                // 边类型
    properties?: Record<string, any>; // 边属性
}
```

## 6. REST API接口

### 6.1 执行Cypher查询

```http
POST /api/graph/cypher
Content-Type: application/json

{
  "query": "MATCH (n) RETURN n LIMIT 10",
  "parameters": {
    "nodeId": 123
  }
}
```

### 6.2 获取节点邻居

```http
GET /api/graph/{nodeId}/neighbors?depth=2&limit=50
```

### 6.3 搜索节点

```http
GET /api/graph/search?keyword=零件&limit=20
```

### 6.4 验证查询语法

```http
POST /api/graph/cypher/validate
Content-Type: application/json

{
  "query": "MATCH (n) RETURN n"
}
```

## 7. 权限和安全

### 7.1 自动权限过滤

图查询API会自动进行权限检查：
- 只返回用户有读权限的节点
- 只有两端节点都有权限时才返回边
- 无需额外的权限检查代码

### 7.2 查询限制

为了系统性能和安全，查询有以下限制：
- 单次查询最多返回1000个节点
- 关系遍历深度最大为10层
- 查询超时时间为30秒

## 8. 性能优化建议

### 8.1 查询优化

- **使用LIMIT限制结果集**：避免返回过多数据
- **指定节点类型**：WHERE n._objectType = 'Type' 可以提高查询效率
- **避免深度遍历**：关系路径不要超过5层
- **使用索引字段**：id、_objectType等字段有索引支持

### 8.2 最佳实践

```cypher
-- ✅ 推荐：明确类型和限制
MATCH (n:Part) 
WHERE n._objectType = 'Part' 
RETURN n LIMIT 50

-- ❌ 不推荐：无限制查询
MATCH (n)-[*]-(m) RETURN n, m

-- ✅ 推荐：有限深度
MATCH (n {id: 123})-[*1..3]-(m) 
RETURN n, m LIMIT 100
```

## 9. 引用关系查询 VS 图查询

| 特性 | 引用关系查询 | 图查询 |
|------|------------|-------|
| 数据实时性 | 高（实时） | 中（有延迟） |
| 查询复杂度 | 低（仅支持直接关系） | 高（支持复杂模式） |
| 性能表现（简单查询） | 高 | 高 |
| 性能表现（复杂查询） | 低 | 高 |
| 查询语言 | REST API | Cypher查询语言 |
| 适用场景 | OLTP事务操作 | OLAP分析操作 |

### 使用建议

- **实时性要求高**：使用引用关系查询API
- **复杂分析需求**：使用图查询API
- **多层级关系**：优先使用图查询API
- **删除检查等事务操作**：使用引用关系查询API
