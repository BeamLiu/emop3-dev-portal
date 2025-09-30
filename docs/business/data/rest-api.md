
# Rest API 数据操作
EMOP平台提供一系列的内置Rest API用于元数据、数据实例的更新及查询工作。所有数据对象的操作都基于元数据定义。

## API 访问说明

-   Swagger 文档访问地址：[http://localhost:870/webconsole/swagger-ui/index.html](http://localhost:870/webconsole/swagger-ui/index.html)
-   API 基础路径：/webconsole/api

ℹ️Rest 服务启动见 [后台开发环境搭建](../environment)中的EMOP Server部分

## REST API 设计规范 (HATEOAS)

EMOP平台的REST API遵循`HATEOAS` (Hypermedia as the Engine of Application State) 规范，这是REST架构的一个约束，使客户端能够通过服务器在响应中提供的超链接动态地与API交互。

### HATEOAS关键特性

1. **自描述性链接**：每个API响应都包含有关可用操作的链接。
2. **简化客户端实现**：客户端不需要硬编码URL路径，可以从响应发现可用操作。
3. **支持API演变**：随着API的发展，客户端可以适应变化而无需更新。
4. **统一的链接结构**：所有资源都使用标准化的链接格式。

### 链接结构示例

API响应中的`_links`对象包含所有可用的操作链接：

```json
{
  "id": 123,
  "name": "测试文档",
  "_links": {
    "self": {
      "href": "/webconsole/api/data/123",
      "method": "GET"
    },
    "update": {
      "href": "/webconsole/api/data/123",
      "method": "PUT"
    },
    "delete": {
      "href": "/webconsole/api/data/123",
      "method": "DELETE"
    },
    "children": {
      "href": "/webconsole/api/data/123/children",
      "method": "GET"
    }
  }
}
```

### 集合资源链接

分页集合结果包括导航链接：

```json
{
  "content": [...],
  "pageSize": 20,
  "pageNumber": 0,
  "totalElements": 100,
  "_links": {
    "self": {
      "href": "/webconsole/api/data/query/Document/page?page=0&size=20&filters=%5B%7B%22field%22%3A%22name%22%2C%22operator%22%3A%22EQUALS%22%2C%22value%22%3A%22Test%22%7D%5D",
      "method": "GET",
      "title": "Current page"
    },
    "next": {
      "href": "/webconsole/api/data/query/Document/page?page=1&size=20&filters=%5B%7B%22field%22%3A%22name%22%2C%22operator%22%3A%22EQUALS%22%2C%22value%22%3A%22Test%22%7D%5D",
      "method": "GET",
      "title": "Next page"
    },
    "prev": {
      "href": "/webconsole/api/data/query/Document/page?page=0&size=20&filters=%5B%7B%22field%22%3A%22name%22%2C%22operator%22%3A%22EQUALS%22%2C%22value%22%3A%22Test%22%7D%5D",
      "method": "GET",
      "title": "Previous page"
    }
  }
}
```

### HATEOAS优势

- **探索性**：API变得可探索，客户端可以发现新功能
- **松耦合**：客户端与服务器的耦合度降低
- **前端适应性**：前端应用能够更容易地适应后端变化
- **API文档**：链接本身就是API的一种文档形式
## 核心接口

### 数据查询接口

-   分页查询：POST `/webconsole/api/data/query/{typeName}/page`
-   不分页查询：POST `/webconsole/api/data/query/{typeName}`
-   单对象查询：GET `/webconsole/api/data/{id}/{propertyName}`
-   根据ID对象查询：GET `/webconsole/api/data/{id}`
-   根据ID对象批量查询：GET `/webconsole/api/data/batch`

### 全文检索接口

-   关键字检索：POST `/webconsole/api/fulltext-search`
-   建议词：GET `/webconsole/api/fulltext-search/suggestions`
-   根据类型重新同步：POST `/webconsole/api/fulltext-search/sync/type/{typeName}`
-   根据ID重新同步：POST `/webconsole/api/fulltext-search/sync/ids}`

### 引用关系查询接口

-   查找所有引用者：GET `/webconsole/api/references/{id}?isPrecise=[true|false]`
-   按关系类型查找引用者：GET `/webconsole/api/references/{id}/byTypes?relationTypes=ASSOCIATE,STRUCTURAL&isPrecise=[true|false]`

### 图搜索接口

-   执行图查询并返回节点列表：POST `/webconsole/api/graph/query`
-   执行图查询并返回图结构：POST `/webconsole/api/query/graph`
-   从指定节点导航到关联对象：POST `/webconsole/api/{id}/navigate/{relationName}`
-   获取对象的引用者：POST `/webconsole/api/{id}/referencers}`

### 数据操作接口

-   创建：POST `/webconsole/api/data/{typeName}`
-   更新：PUT `/webconsole/api/data/{id}`
-   删除：DELETE `/webconsole/api/data/{id}`
-   将draft对象(DraftModelObject)转换为正式对象：POST `/draft/{id}/convert`

### 关系操作接口

-   移动对象：POST `/webconsole/api/relation/move`

### 元数据接口

-   类型定义查询：GET `/webconsole/api/metadata/types`
-   枚举值查询：GET `/webconsole/api/values/{typeName}/{attributeName}`

### DSL执行接口

-   执行DSL：POST `/webconsole/api/dsl/execute`

### 版本管理接口

- 创建新修订版本：POST `/webconsole/api/revision/{id}`
- 批量创建新修订版本：POST `/webconsole/api/revision/batch`
- 查询特定版本：GET `/webconsole/api/revision/query`
- 查询最新发布版本：GET `/webconsole/api/revision/latest-released`
- 批量查询最新发布版本：GET `/webconsole/api/revision/latest-released/batch`
- 查询最新版本：GET `/webconsole/api/revision/latest`
- 批量查询最新版本：POST `/webconsole/api/revision/latest/batch`
- 批量查询特定版本：POST `/webconsole/api/revision/query/batch`
- 获取版本历史：GET `/webconsole/api/revision/history/{objectType}/{code}`
- 检查对象状态是否能修订：GET `/webconsole/api/revision/state/{id}`