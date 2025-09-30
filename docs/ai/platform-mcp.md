# EMOP平台操作类 MCP

EMOP平台操作类MCP服务专门为EMOP平台的核心功能提供API访问能力，主要包括元数据管理等平台级操作。

## Metadata MCP Server
专为EMOP平台元数据管理设计的MCP服务，提供完整的元数据CRUD操作和查询功能。

**功能特性：**
- 完整的元数据生命周期管理
- 支持多种元数据类型（实体、属性、关系等）
- 提供RESTful API风格的操作接口
- 集成EMOP平台的权限和安全机制
- 支持元数据版本控制和历史追踪

**核心工具：**
- `get_metadata_by_id` - 根据ID获取元数据详情
- `search_metadata` - 搜索和查询元数据
- `create_metadata` - 创建新的元数据
- `update_metadata` - 更新现有元数据
- `delete_metadata` - 删除元数据
- `list_metadata_types` - 获取支持的元数据类型
- `get_metadata_schema` - 获取元数据结构定义

**快速启动：**

**1. 环境准备**

确保EMOP平台服务正在运行：
```bash
# 检查EMOP平台状态
curl http://localhost:8080/health

# 确认元数据服务可用
curl http://localhost:8080/api/metadata/types
```

**2. 启动MCP服务器**

```bash
# 进入MCP服务器目录
cd /mnt/d/workspace/emop/emop3/emop-ai/metadata-mcp

# 安装依赖（首次运行）
npm install

# 启动MCP服务器
npm start
```

**3. 验证服务状态**

```bash
# 检查MCP服务器状态
curl http://localhost:3001/health

# 测试元数据连接
curl http://localhost:3001/api/test-connection
```

### 配置说明

**环境变量配置：**

在 `emop-ai/metadata-mcp` 目录下创建 `.env` 文件：

```bash
# EMOP平台连接配置
EMOP_API_BASE_URL=http://localhost:8080
EMOP_API_TOKEN=your_api_token_here

# MCP服务器配置
MCP_SERVER_PORT=3001
MCP_SERVER_HOST=localhost

# 日志配置
LOG_LEVEL=info
LOG_FORMAT=json

# 缓存配置
CACHE_TTL=300
CACHE_MAX_SIZE=1000
```

**API令牌获取：**
1. 登录EMOP平台管理界面
2. 进入「系统设置」→「API管理」
3. 创建新的API令牌，选择「元数据管理」权限
4. 复制生成的令牌到配置文件中

**连接配置验证：**
```bash
# 测试EMOP平台连接
curl -H "Authorization: Bearer your_api_token_here" \
     http://localhost:8080/api/metadata/types

# 应该返回支持的元数据类型列表
```

### 测试方法

**使用 mcp-inspector 测试：**

```bash
# 1. 启动 EMOP Metadata MCP Server
cd /mnt/d/workspace/emop/emop3/emop-ai/metadata-mcp
npm start

# 2. 启动 mcp-inspector（在新终端中）
npx @modelcontextprotocol/inspector

# 3. 在浏览器中访问显示的URL，添加MCP服务器：
# Transport Type: Streamable HTTP
# Server URL: http://localhost:3001/mcp
# 测试可用的工具和资源
```

**功能验证测试：**

1. **元数据类型查询**：
   ```bash
   # 通过MCP调用获取元数据类型
   # 在mcp-inspector中调用 list_metadata_types 工具
   ```

2. **元数据搜索**：
   ```bash
   # 搜索特定类型的元数据
   # 在mcp-inspector中调用 search_metadata 工具
   # 参数示例：{"type": "entity", "keyword": "用户"}
   ```

3. **元数据详情获取**：
   ```bash
   # 根据ID获取元数据详情
   # 在mcp-inspector中调用 get_metadata_by_id 工具
   # 参数示例：{"id": "meta_001"}
   ```

4. **元数据创建**：
   ```bash
   # 创建新的元数据
   # 在mcp-inspector中调用 create_metadata 工具
   # 参数示例：见下方工具参数示例
   ```

### 工具参数示例

**搜索元数据：**
```json
{
  "type": "entity",
  "keyword": "用户",
  "page": 1,
  "size": 10,
  "filters": {
    "status": "active",
    "category": "business"
  }
}
```

**创建元数据：**
```json
{
  "type": "entity",
  "name": "用户信息",
  "description": "系统用户基本信息实体",
  "properties": {
    "id": {
      "type": "string",
      "required": true,
      "description": "用户唯一标识"
    },
    "name": {
      "type": "string",
      "required": true,
      "description": "用户姓名"
    },
    "email": {
      "type": "string",
      "required": false,
      "description": "用户邮箱"
    }
  },
  "category": "business",
  "tags": ["用户", "基础数据"]
}
```

**更新元数据：**
```json
{
  "id": "meta_001",
  "name": "用户信息（更新）",
  "description": "系统用户基本信息实体 - 已更新",
  "properties": {
    "id": {
      "type": "string",
      "required": true,
      "description": "用户唯一标识"
    },
    "name": {
      "type": "string",
      "required": true,
      "description": "用户姓名"
    },
    "email": {
      "type": "string",
      "required": false,
      "description": "用户邮箱"
    },
    "phone": {
      "type": "string",
      "required": false,
      "description": "用户手机号"
    }
  },
  "tags": ["用户", "基础数据", "已更新"]
}
```

### 支持的元数据类型

1. **实体（Entity）**：
   - 业务实体定义
   - 数据模型结构
   - 属性和关系定义

2. **属性（Attribute）**：
   - 字段定义
   - 数据类型规范
   - 验证规则

3. **关系（Relationship）**：
   - 实体间关系
   - 关联规则
   - 约束条件

4. **枚举（Enumeration）**：
   - 枚举值定义
   - 选项列表
   - 分类标准

5. **规则（Rule）**：
   - 业务规则
   - 验证逻辑
   - 计算公式

### 权限和安全

**API权限控制：**
- 所有操作需要有效的API令牌
- 支持基于角色的访问控制（RBAC）
- 操作日志记录和审计

**数据安全：**
- 敏感数据加密存储
- 传输过程TLS加密
- 定期安全扫描和更新

**访问限制：**
- API调用频率限制
- 并发连接数控制
- 异常访问检测和阻断

### 性能优化

**缓存策略：**
- 元数据类型定义缓存
- 常用查询结果缓存
- 分层缓存架构

**查询优化：**
- 索引优化
- 分页查询
- 异步处理

**监控指标：**
- API响应时间
- 错误率统计
- 缓存命中率
- 并发连接数

### 调试和监控

**日志配置：**
```bash
# 启用详细日志
export LOG_LEVEL=debug
npm start

# 查看实时日志
tail -f logs/metadata-mcp.log
```

**健康检查：**
```bash
# MCP服务器健康检查
curl http://localhost:3001/health

# EMOP平台连接检查
curl http://localhost:3001/api/test-connection

# 元数据服务状态
curl http://localhost:3001/api/status
```

**性能监控：**
- 访问 `http://localhost:3001/metrics` 查看性能指标
- 使用 Prometheus 和 Grafana 进行监控
- 设置告警规则和通知

### AI工具集成配置

**Claude Desktop配置：**
```json
{
  "mcpServers": {
    "emop-metadata-mcp": {
      "type": "sse",
      "url": "http://localhost:3001/sse",
      "disabled": false,
      "autoApprove": []
    }
  }
}
```

**其他AI工具配置：**
```json
{
  "mcpServers": {
    "emop-metadata-mcp": {
      "command": "node",
      "args": ["/mnt/d/workspace/emop/emop3/emop-ai/metadata-mcp/dist/index.js"],
      "env": {
        "EMOP_API_BASE_URL": "http://localhost:8080",
        "EMOP_API_TOKEN": "your_api_token_here"
      },
      "disabled": false,
      "autoApprove": []
    }
  }
}
```

## Data Management MCP Server
专为EMOP平台数据管理功能设计的MCP服务，提供完整的数据CRUD操作、查询和管理功能。

**功能特性：**
- 完整的数据生命周期管理
- 支持多种数据源和格式
- 提供高性能的数据查询和分析
- 集成数据验证和质量控制
- 支持数据版本控制和历史追踪

**核心工具：**
- `query_data` - 执行数据查询操作
- `create_data` - 创建新的数据记录
- `update_data` - 更新现有数据记录
- `delete_data` - 删除数据记录
- `batch_operations` - 批量数据操作
- `data_validation` - 数据验证和质量检查
- `export_data` - 数据导出功能
- `import_data` - 数据导入功能

**快速启动：**

```bash
# 进入数据管理MCP服务器目录
cd /mnt/d/workspace/emop/emop3/emop-ai/data-management-mcp

# 安装依赖
npm install

# 配置环境变量
cp .env.example .env
# 编辑 .env 文件，配置数据库连接等参数

# 启动服务
npm start
```

**配置说明：**

环境变量配置：
```bash
# 数据库连接
DATABASE_URL=postgresql://user:password@localhost:5432/emop_db
REDIS_URL=redis://localhost:6379

# API配置
API_BASE_URL=http://localhost:8080
API_TOKEN=your_api_token

# MCP服务器配置
MCP_PORT=3002
MCP_HOST=localhost
```

**测试方法：**

使用mcp-inspector测试数据查询功能：
```bash
# 启动服务后，在mcp-inspector中测试
# Transport Type: Streamable HTTP
# Server URL: http://localhost:3002/mcp
```

**工具参数示例：**

查询数据：
```json
{
  "table": "users",
  "filters": {
    "status": "active",
    "created_date": {
      "gte": "2024-01-01"
    }
  },
  "limit": 100,
  "offset": 0
}
```

**AI工具集成配置：**
```json
{
  "mcpServers": {
    "emop-data-management": {
      "type": "sse",
      "url": "http://localhost:3002/sse",
      "disabled": false
    }
  }
}
```

## Business Code Management MCP Server
专为EMOP平台业务代码管理设计的MCP服务，提供代码生成、模板管理和代码分析功能。

**功能特性：**
- 智能代码生成和模板管理
- 代码质量分析和优化建议
- 支持多种编程语言和框架
- 集成代码规范检查
- 提供代码重构和优化工具

**核心工具：**
- `generate_code` - 基于模板生成代码
- `analyze_code` - 代码质量分析
- `refactor_code` - 代码重构建议
- `validate_code` - 代码规范检查
- `manage_templates` - 模板管理操作
- `code_metrics` - 代码指标统计

**快速启动：**

```bash
# 进入业务代码管理MCP服务器目录
cd /mnt/d/workspace/emop/emop3/emop-ai/business-code-mcp

# 安装依赖
npm install

# 启动服务
npm start
```

## Lifecycle Management MCP Server
专为EMOP平台生命周期管理设计的MCP服务，提供完整的业务对象生命周期控制和状态管理功能。

**功能特性：**
- 完整的生命周期状态管理
- 支持自定义状态转换规则
- 提供状态变更历史追踪
- 集成业务规则引擎
- 支持批量状态操作

**核心工具：**
- `get_lifecycle_status` - 获取对象生命周期状态
- `transition_status` - 执行状态转换
- `get_available_transitions` - 获取可用的状态转换
- `get_lifecycle_history` - 获取状态变更历史
- `batch_transition` - 批量状态转换
- `validate_transition` - 验证状态转换的有效性
- `get_lifecycle_rules` - 获取生命周期规则
- `copy_with_version` - 版本复制操作

**快速启动：**

```bash
# 进入生命周期管理MCP服务器目录
cd /mnt/d/workspace/emop/emop3/emop-ai/lifecycle-mcp

# 安装依赖
npm install

# 配置环境变量
cp .env.example .env

# 启动服务
npm start
```

**配置说明：**

环境变量配置：
```bash
# EMOP平台连接
EMOP_API_BASE_URL=http://localhost:8080
EMOP_API_TOKEN=your_lifecycle_api_token

# MCP服务器配置
MCP_PORT=3003
MCP_HOST=localhost

# 生命周期配置
LIFECYCLE_CONFIG_PATH=./config/lifecycle-rules.json
STATE_CACHE_TTL=300
```

**测试方法：**

```bash
# 启动mcp-inspector测试
npx @modelcontextprotocol/inspector

# 配置连接：
# Transport Type: Streamable HTTP
# Server URL: http://localhost:3003/mcp
```

**工具参数示例：**

状态转换：
```json
{
  "objectId": "obj_12345",
  "objectType": "document",
  "targetStatus": "published",
  "comment": "文档审核通过，发布上线",
  "metadata": {
    "approver": "admin",
    "approval_date": "2024-01-15"
  }
}
```

**支持的生命周期状态：**

1. **文档生命周期**：
   - draft（草稿）
   - review（审核中）
   - approved（已批准）
   - published（已发布）
   - archived（已归档）

2. **项目生命周期**：
   - planning（规划中）
   - development（开发中）
   - testing（测试中）
   - production（生产环境）
   - maintenance（维护中）
   - retired（已退役）

**转换事件和规则：**

- 状态转换需要满足预定义的业务规则
- 支持条件检查和权限验证
- 自动触发相关的业务流程
- 记录完整的操作审计日志

**版本复制规则：**

```json
{
  "sourceVersion": "v1.0",
  "targetVersion": "v1.1",
  "copyOptions": {
    "includeMetadata": true,
    "includeRelations": true,
    "resetStatus": "draft",
    "updateTimestamp": true
  }
}
```

**AI工具集成配置：**
```json
{
  "mcpServers": {
    "emop-lifecycle": {
      "type": "sse",
      "url": "http://localhost:3003/sse",
      "disabled": false
    }
  }
}
```

## Graph Query MCP Server
专为EMOP平台图数据查询设计的MCP服务，提供强大的图数据库查询和分析功能。

**功能特性：**
- 高性能图数据查询
- 支持复杂的关系分析
- 提供图算法和路径分析
- 集成可视化数据输出
- 支持实时图数据更新

**核心工具：**
- `execute_cypher_query` - 执行Cypher查询语句
- `find_shortest_path` - 查找最短路径
- `get_node_relationships` - 获取节点关系
- `analyze_graph_structure` - 图结构分析
- `detect_communities` - 社区检测算法
- `calculate_centrality` - 中心性计算
- `export_subgraph` - 子图导出

**快速启动：**

```bash
# 进入图查询MCP服务器目录
cd /mnt/d/workspace/emop/emop3/emop-ai/graph-query-mcp

# 安装依赖
npm install

# 启动Neo4j数据库（如果未启动）
# docker run -d --name neo4j -p 7474:7474 -p 7687:7687 neo4j:latest

# 启动服务
npm start
```

**配置说明：**

环境变量配置：
```bash
# Neo4j连接配置
NEO4J_URI=bolt://localhost:7687
NEO4J_USER=neo4j
NEO4J_PASSWORD=your_password

# MCP服务器配置
MCP_PORT=3004
MCP_HOST=localhost

# 查询优化配置
QUERY_TIMEOUT=30000
MAX_RESULT_SIZE=10000
CACHE_ENABLED=true
```

**测试方法：**

```bash
# 测试Neo4j连接
curl http://localhost:7474

# 启动mcp-inspector测试
npx @modelcontextprotocol/inspector
# Server URL: http://localhost:3004/mcp
```

**工具参数示例：**

Cypher查询：
```json
{
  "query": "MATCH (n:User)-[r:FOLLOWS]->(m:User) WHERE n.name = $name RETURN m.name, r.since",
  "parameters": {
    "name": "张三"
  },
  "limit": 100
}
```

**AI工具集成配置：**
```json
{
  "mcpServers": {
    "emop-graph-query": {
      "type": "sse",
      "url": "http://localhost:3004/sse",
      "disabled": false
    }
  }
}
```

## Fulltext Search MCP Server
专为EMOP平台全文搜索设计的MCP服务，提供强大的文本搜索、分析和索引管理功能。

**功能特性：**
- 高性能全文搜索引擎
- 支持多语言和复杂查询
- 提供搜索结果排序和过滤
- 集成搜索分析和统计
- 支持实时索引更新

**核心工具：**
- `search_documents` - 执行全文搜索
- `advanced_search` - 高级搜索功能
- `suggest_terms` - 搜索建议和自动完成
- `analyze_text` - 文本分析和处理
- `manage_index` - 索引管理操作
- `get_search_stats` - 搜索统计信息
- `bulk_index` - 批量索引操作

**快速启动：**

```bash
# 进入全文搜索MCP服务器目录
cd /mnt/d/workspace/emop/emop3/emop-ai/fulltext-search-mcp

# 安装依赖
npm install

# 启动Elasticsearch（如果未启动）
# docker run -d --name elasticsearch -p 9200:9200 -e "discovery.type=single-node" elasticsearch:8.11.0

# 启动服务
npm start
```

**配置说明：**

环境变量配置：
```bash
# Elasticsearch连接配置
ELASTICSEARCH_URL=http://localhost:9200
ELASTICSEARCH_USERNAME=elastic
ELASTICSEARCH_PASSWORD=your_password

# MCP服务器配置
MCP_PORT=3005
MCP_HOST=localhost

# 搜索配置
DEFAULT_INDEX=emop_documents
MAX_SEARCH_RESULTS=1000
SEARCH_TIMEOUT=5000
```

**测试方法：**

```bash
# 测试Elasticsearch连接
curl http://localhost:9200/_cluster/health

# 启动mcp-inspector测试
npx @modelcontextprotocol/inspector
# Server URL: http://localhost:3005/mcp
```

**工具参数示例：**

全文搜索：
```json
{
  "query": "EMOP平台 用户管理",
  "index": "emop_documents",
  "filters": {
    "document_type": "manual",
    "status": "published",
    "created_date": {
      "gte": "2024-01-01"
    }
  },
  "sort": [
    {"_score": {"order": "desc"}},
    {"created_date": {"order": "desc"}}
  ],
  "highlight": {
    "fields": {
      "title": {},
      "content": {}
    }
  },
  "size": 20,
  "from": 0
}
```

**支持的搜索字段类型：**

1. **文档字段**：
   - title（标题）
   - content（内容）
   - summary（摘要）
   - tags（标签）
   - category（分类）

2. **元数据字段**：
   - author（作者）
   - created_date（创建日期）
   - modified_date（修改日期）
   - status（状态）
   - version（版本）

**搜索结果结构：**

```json
{
  "total": 150,
  "max_score": 2.5,
  "hits": [
    {
      "_id": "doc_001",
      "_score": 2.5,
      "_source": {
        "title": "EMOP平台用户管理指南",
        "content": "详细介绍EMOP平台的用户管理功能...",
        "author": "张三",
        "created_date": "2024-01-15"
      },
      "highlight": {
        "title": ["<em>EMOP平台</em><em>用户管理</em>指南"],
        "content": ["详细介绍<em>EMOP平台</em>的<em>用户管理</em>功能..."]
      }
    }
  ],
  "aggregations": {
    "by_category": {
      "buckets": [
        {"key": "manual", "doc_count": 80},
        {"key": "api", "doc_count": 45},
        {"key": "tutorial", "doc_count": 25}
      ]
    }
  }
}
```

**索引同步机制：**

- 实时索引更新
- 批量索引优化
- 增量同步支持
- 索引健康监控

**权限和安全：**

- 基于角色的搜索权限
- 敏感内容过滤
- 搜索日志审计
- 访问频率限制

**性能优化：**

- 搜索结果缓存
- 查询优化建议
- 索引分片策略
- 异步处理机制

**调试和监控：**

```bash
# 查看索引状态
curl http://localhost:3005/api/index/status

# 搜索性能统计
curl http://localhost:3005/api/search/stats

# 健康检查
curl http://localhost:3005/health
```

**AI工具集成配置：**
```json
{
  "mcpServers": {
    "emop-fulltext-search": {
      "type": "sse",
      "url": "http://localhost:3005/sse",
      "disabled": false
    }
  }
}
```

## User Management MCP Server
专为EMOP平台用户管理设计的MCP服务，提供完整的用户账户、角色和权限管理功能。

**功能特性：**
- 完整的用户生命周期管理
- 基于角色的访问控制（RBAC）
- 细粒度权限管理
- 用户组织架构管理
- 集成单点登录（SSO）支持
- 用户行为审计和监控

**核心工具：**
- `get_users` - 获取用户列表
- `create_user` - 创建新用户
- `update_user` - 更新用户信息
- `delete_user` - 删除用户
- `get_user_roles` - 获取用户角色
- `assign_role` - 分配角色给用户
- `revoke_role` - 撤销用户角色
- `get_permissions` - 获取权限列表
- `check_permission` - 检查用户权限
- `get_user_groups` - 获取用户组
- `manage_user_groups` - 管理用户组
- `reset_password` - 重置用户密码
- `get_user_sessions` - 获取用户会话信息
- `audit_user_actions` - 用户行为审计

**快速启动：**

```bash
# 进入用户管理MCP服务器目录
cd /mnt/d/workspace/emop/emop3/emop-ai/user-mgmt-mcp

# 安装依赖
npm install

# 配置环境变量
cp .env.example .env

# 启动服务
npm start
```

**配置说明：**

环境变量配置：
```bash
# EMOP平台连接配置
EMOP_API_BASE_URL=http://localhost:8080
EMOP_API_TOKEN=your_user_mgmt_token

# MCP服务器配置
MCP_PORT=3007
MCP_HOST=localhost

# 用户管理配置
USER_SESSION_TIMEOUT=3600
PASSWORD_POLICY_ENABLED=true
AUDIT_LOG_ENABLED=true
SSO_ENABLED=false

# 权限缓存配置
PERMISSION_CACHE_TTL=300
ROLE_CACHE_TTL=600
```

**测试方法：**

```bash
# 启动mcp-inspector测试
npx @modelcontextprotocol/inspector
# Server URL: http://localhost:3007/mcp
```

**工具参数示例：**

创建用户：
```json
{
  "username": "zhangsan",
  "email": "zhangsan@example.com",
  "fullName": "张三",
  "department": "研发部",
  "position": "高级工程师",
  "phone": "13800138000",
  "roles": ["developer", "reviewer"],
  "groups": ["rd_team"],
  "metadata": {
    "employee_id": "EMP001",
    "hire_date": "2024-01-15"
  }
}
```

分配角色：
```json
{
  "userId": "user_12345",
  "roleId": "role_admin",
  "scope": "project",
  "scopeId": "proj_001",
  "expiryDate": "2024-12-31",
  "assignedBy": "admin_user"
}
```

权限检查：
```json
{
  "userId": "user_12345",
  "resource": "document",
  "action": "read",
  "context": {
    "project_id": "proj_001",
    "document_type": "specification"
  }
}
```

**支持的用户角色：**

1. **系统管理员（System Admin）**：
   - 完整的系统管理权限
   - 用户和角色管理
   - 系统配置管理

2. **项目管理员（Project Admin）**：
   - 项目范围内的管理权限
   - 项目成员管理
   - 项目资源分配

3. **开发人员（Developer）**：
   - 代码开发和提交权限
   - 技术文档编写
   - 测试环境访问

4. **审核员（Reviewer）**：
   - 代码审核权限
   - 文档审批权限
   - 质量检查权限

5. **普通用户（User）**：
   - 基础的查看权限
   - 个人信息管理
   - 基础功能使用

**权限管理模型：**

```json
{
  "permission": {
    "id": "perm_doc_read",
    "name": "文档读取",
    "resource": "document",
    "action": "read",
    "conditions": {
      "owner_only": false,
      "project_member": true,
      "status_published": true
    }
  },
  "role": {
    "id": "role_developer",
    "name": "开发人员",
    "permissions": [
      "perm_doc_read",
      "perm_doc_write",
      "perm_code_commit"
    ],
    "inheritance": ["role_user"]
  }
}
```

**用户组织架构：**

- 支持多层级组织结构
- 部门和团队管理
- 职位和汇报关系
- 组织权限继承

**安全特性：**

- 密码策略强制执行
- 多因素认证支持
- 会话管理和超时控制
- 登录失败锁定机制
- 敏感操作审计日志

**集成功能：**

- LDAP/AD集成
- OAuth2/OIDC支持
- SAML单点登录
- 第三方身份提供商集成

**监控和审计：**

```bash
# 查看用户活动统计
curl http://localhost:3007/api/users/activity-stats

# 获取权限变更日志
curl http://localhost:3007/api/audit/permission-changes

# 用户会话监控
curl http://localhost:3007/api/users/active-sessions
```

**AI工具集成配置：**
```json
{
  "mcpServers": {
    "emop-user-mgmt": {
      "type": "sse",
      "url": "http://localhost:3007/sse",
      "disabled": false,
      "autoApprove": []
    }
  }
}
```

## Workflow MCP Server
专为EMOP平台工作流管理设计的MCP服务，提供完整的工作流定义、执行和监控功能。

**功能特性：**
- 可视化工作流设计和管理
- 支持复杂的业务流程自动化
- 提供任务分配和状态跟踪
- 集成事件驱动的流程控制
- 支持流程版本管理和回滚

**核心工具：**
- `create_workflow` - 创建新的工作流
- `start_workflow_instance` - 启动工作流实例
- `get_workflow_status` - 获取工作流状态
- `complete_task` - 完成工作流任务
- `assign_task` - 分配任务给用户
- `get_pending_tasks` - 获取待处理任务
- `cancel_workflow` - 取消工作流实例
- `get_workflow_history` - 获取工作流历史
- `update_workflow_variables` - 更新流程变量
- `trigger_workflow_event` - 触发工作流事件

**快速启动：**

```bash
# 进入工作流MCP服务器目录
cd /mnt/d/workspace/emop/emop3/emop-ai/workflow-mcp

# 安装依赖
npm install

# 配置环境变量
cp .env.example .env

# 启动服务
npm start
```

**配置说明：**

环境变量配置：
```bash
# 工作流引擎配置
WORKFLOW_ENGINE_URL=http://localhost:8080/workflow
WORKFLOW_API_TOKEN=your_workflow_token

# MCP服务器配置
MCP_PORT=3006
MCP_HOST=localhost

# 任务配置
TASK_TIMEOUT=3600000
MAX_CONCURRENT_TASKS=100
TASK_RETRY_ATTEMPTS=3
```

**测试方法：**

```bash
# 启动mcp-inspector测试
npx @modelcontextprotocol/inspector
# Server URL: http://localhost:3006/mcp
```

**工具参数示例：**

创建工作流：
```json
{
  "name": "文档审批流程",
  "description": "文档创建、审核、发布的完整流程",
  "version": "1.0",
  "definition": {
    "startEvent": "start",
    "endEvent": "end",
    "tasks": [
      {
        "id": "create_document",
        "name": "创建文档",
        "type": "userTask",
        "assignee": "${initiator}"
      },
      {
        "id": "review_document",
        "name": "审核文档",
        "type": "userTask",
        "candidateGroups": ["reviewers"]
      },
      {
        "id": "publish_document",
        "name": "发布文档",
        "type": "serviceTask",
        "implementation": "publishService"
      }
    ],
    "flows": [
      {"from": "start", "to": "create_document"},
      {"from": "create_document", "to": "review_document"},
      {"from": "review_document", "to": "publish_document", "condition": "${approved == true}"},
      {"from": "review_document", "to": "create_document", "condition": "${approved == false}"},
      {"from": "publish_document", "to": "end"}
    ]
  },
  "variables": {
    "approved": {"type": "boolean", "default": false},
    "document_id": {"type": "string"},
    "reviewer_comments": {"type": "string"}
  }
}
```

启动工作流实例：
```json
{
  "workflowId": "document_approval_v1",
  "businessKey": "doc_12345",
  "variables": {
    "document_id": "doc_12345",
    "initiator": "user123",
    "title": "EMOP平台使用指南"
  },
  "metadata": {
    "priority": "high",
    "department": "技术部"
  }
}
```

**支持的流程类型：**

1. **审批流程**：
   - 文档审批
   - 项目审批
   - 预算审批
   - 人员审批

2. **业务流程**：
   - 订单处理
   - 客户服务
   - 数据处理
   - 系统集成

3. **管理流程**：
   - 变更管理
   - 事件处理
   - 监控告警
   - 备份恢复

**任务类型定义：**

1. **用户任务（UserTask）**：
   - 需要人工处理的任务
   - 支持任务分配和委派
   - 提供任务表单和数据收集

2. **服务任务（ServiceTask）**：
   - 自动执行的系统任务
   - 调用外部服务或API
   - 支持同步和异步执行

3. **脚本任务（ScriptTask）**：
   - 执行自定义脚本逻辑
   - 支持多种脚本语言
   - 提供变量访问和修改

**流程状态管理：**

- **运行中（Running）**：流程正在执行
- **挂起（Suspended）**：流程暂时停止
- **完成（Completed）**：流程正常结束
- **取消（Cancelled）**：流程被手动取消
- **异常（Error）**：流程执行出错

**任务分配策略：**

1. **直接分配**：指定具体用户
2. **组分配**：分配给用户组
3. **角色分配**：基于角色分配
4. **负载均衡**：自动分配给空闲用户
5. **技能匹配**：根据技能要求分配

**流程变量管理：**

```json
{
  "variables": {
    "document_id": "doc_12345",
    "approved": true,
    "reviewer": "admin",
    "review_date": "2024-01-15T10:30:00Z",
    "comments": "文档内容完整，批准发布"
  }
}
```

**事件处理机制：**

1. **定时器事件**：基于时间触发
2. **消息事件**：接收外部消息
3. **信号事件**：广播信号处理
4. **错误事件**：异常情况处理
5. **补偿事件**：事务回滚处理

**流程监控和统计：**

```bash
# 获取流程统计
curl http://localhost:3006/api/workflow/stats

# 监控活跃实例
curl http://localhost:3006/api/workflow/active

# 性能分析
curl http://localhost:3006/api/workflow/performance
```

**AI工具集成配置：**
```json
{
  "mcpServers": {
    "emop-workflow": {
      "type": "sse",
      "url": "http://localhost:3006/sse",
      "disabled": false
    }
  }
}
```
