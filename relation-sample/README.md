# EMOP 关系演示项目

这是一个完整的EMOP关系操作演示项目，以项目管理为例，展示了如何使用EMOP平台的各种关系功能，包括结构关系、关联关系、XPath操作、DSL数据操作和Java API。

## 项目概述

### 业务场景
项目管理系统，包含以下核心实体：
- **RSampleProject（项目）**: 项目管理的主体
- **RSampleTask（任务）**: 项目下的任务，支持树形结构
- **File（文件）**: 项目和任务的交付物

### 关系设计

#### 1. 结构关系（StructuralRelation）
- **Project -> Task**: 项目包含任务（一对多）
  - 外键：`RSampleTask.projectId`
  - 用途：表示任务属于哪个项目
  
- **Task -> Task**: 任务树形结构（一对多）
  - 外键：`RSampleTask.parentTaskId`
  - 用途：构建父子任务关系

#### 2. 关联关系（AssociateRelation）
- **Project -> File**: 项目交付物（多对多）
  - 关系名：`deliverables`
  - 用途：项目的输出文件和文档
  
- **Task -> File**: 任务交付物（多对多）
  - 关系名：`attachments`
  - 用途：任务相关的文件和资料

## 项目结构

```
relation-sample/
├── relation-plugin-api/          # 插件API模块 - 定义领域模型和服务接口
├── relation-plugin/              # 插件实现模块 - 服务实现
├── relation-server/              # 服务器模块 - 启动类和配置，内置了EMOP Server启动
├── relation-client/              # 客户端测试模块 - RPC、REST和DSL测试
└── pom.xml                       # 父POM
```

## 功能特性

### 1. 领域模型设计
- **RSampleProject**: 项目实体，继承自`ItemRevision`，支持版本管理
- **RSampleTask**: 任务实体，支持树形结构和项目关联
- 使用`@BusinessKeys`定义业务唯一键
- 支持枚举类型（项目状态、任务状态、优先级）

### 2. 关系操作演示
- **结构关系**: 通过外键实现的固定关系，性能好，适合层次结构
- **关联关系**: 通过关系表实现的动态关系，灵活，支持属性扩展
- **关系查询**: 懒加载机制，按需获取关联数据
- **关系维护**: 自动处理关系的创建、更新和删除

### 3. XPath 路径操作
- **基础路径**: 直接属性访问 `project.get("name")`
- **关系路径**: 关系属性访问 `project.get("tasks")`
- **嵌套路径**: 多层级访问 `task.get("project/projectManager")`
- **路径更新**: 通过路径设置属性值

### 4. DSL 数据操作
- **对象创建**: `create object RSampleProject {...}`
- **关系建立**: `relation Project(...) -> Task(...) as tasks`
- **数据查询**: `query RSampleProject where ...`
- **数据更新**: `update object RSampleProject where ... {...}`
- **数据导入**: `import RSampleProject "file.csv" {...}`

### 5. Java API 操作
- **CRUD操作**: 创建、查询、更新、删除
- **关系操作**: AssociateRelationService 关系管理
- **批量操作**: 提升性能的批量接口
- **事务控制**: `@Transactional` 注解支持

### 6. 数据导入导出
- **表格导入**: Excel/CSV 数据导入
- **树形导入**: 支持父子关系的树形数据导入
- **关系导入**: 批量建立对象间关系
- **类型解析**: 动态决定目标对象类型
- **数据映射**: 自定义数据转换逻辑

## 快速开始

### 1. 确认环境
- 保证以下服务可用, 并设置本地的`hosts`解析
```
# 缓存 Redis
192.168.10.103 cache-dev.emop.emopdata.com
# 文件服务 Minio
192.168.10.103 storage-dev.emop.emopdata.com
# 注册中心(建议使用本地的consul，避免注册到公共环境污染公共环境的服务列表)
127.0.0.1 registry-dev.emop.emopdata.com
# 数据库集群域名映射
192.168.10.103 emop-db-master-dev.emop.emopdata.com
```
- 确认 maven 已经使用了阿里云私仓
- JDK 17+

### 2. 构建项目
```bash
cd relation-sample
mvn clean install
```

### 3. 启动服务器
在IDE中直接运行 `RelationServerApplication.main()` 方法，或者使用命令行：
```bash
cd relation-server
java -jar target/relation-server-1.0.0-SNAPSHOT.jar
```

🔔启动性能:

EMOP Server启动的时候会从java class定义映射到元数据，然后再将元数据映射到数据库schema，因此启动过程比较长，第一次启动完成后，初始化了对应的数据库的schema后，可以添加三个启动参数为`true`，以加快启动速度：
- skipClass2MetadataSyncChecking：跳过class定义到元数据的映射
- skipMetadata2TableSyncChecking：跳过元数据到数据库的映射

当修改了`relation-plugin-api`中的存储class定义，可以把上面几个参数设置为`false`，重启后自动更新对应的元数据和表结构。
```bash
java -jar target/relation-server-1.0.0-SNAPSHOT.jar -DskipClass2MetadataSyncChecking=true -DskipMetadata2TableSyncChecking=true
```

### 4. 访问应用
查看swagger地址: http://localhost:870/webconsole/api

### 5. 运行客户端演示
在IDE中直接运行 `RelationClientApplication.main()`

## 演示内容

### 1. Java API 演示 (JavaApiDemo)
- **RPC服务调用**: 通过`RSampleProjectManagementService`创建项目和任务树
- **结构关系操作**: 项目-任务关系（外键），任务树形结构（父子关系）
- **关联关系操作**: 任务-文件关联（多对多关系）
- **查询操作**: 条件查询、关联查询、统计查询、存在性检查

### 2. REST API 演示 (RestApiDemo)
- HTTP方式创建项目和任务
- RESTful查询操作
- 数据更新操作

### 3. XPath 演示 (XPathDemo)
- **传统代码 vs JXPath对比**: 展示逐层访问和XPath表达式的差异
- **高级XPath表达式**: 条件过滤、嵌套查询、多条件查询、函数使用
- **平台内置DSL**: 使用`show object ... as tree`展示树结构
- **路径语法**: `tasks[*]`、`tasks[1]/name`、`tasks[@assignee='架构师B']`等

### 4. DSL 演示 (DslDemo)
- **对象创建**: `create object RSampleProject {...}`
- **关系建立**: `relation RSampleProject(...) -> RSampleTask(...) as tasks`
- **数据查询**: `show object RSampleProject(name like '%DSL%')`
- **数据更新**: `update object RSampleProject where ... {...}`
- **删除操作**: `remove relation between ...`

### 5. 数据导入导出演示 (DataImportExportDemo)
- 表格数据批量导入
- 树形结构导入
- DSL方式导入
- 关系数据导入

## 核心概念说明

### 结构关系 vs 关联关系

| 特性 | 结构关系 | 关联关系 |
|------|----------|----------|
| 存储方式 | 外键字段 | 关系表 |
| 性能 | 高 | 中等 |
| 灵活性 | 低 | 高 |
| 关系属性 | 不支持 | 支持 |
| 适用场景 | 固定层次结构 | 动态多对多关系 |

### XPath 路径语法
基于代码中的实际使用：
- 获取所有任务：`project.get("tasks[*]")`
- 获取第一个任务名称：`project.get("tasks[1]/name")`
- 条件查询：`project.get("tasks[@assignee='架构师B']")`
- 嵌套查询：`project.get("tasks[*]/subTasks[@assignee='前端工程师']")`
- 多条件：`project.get("tasks[@status='TODO' and @priority='MEDIUM']")`
- 字符串函数：`project.get("tasks[contains(name, '设计')]")`
- 统计函数：`project.get("count(tasks[*])")`

注意：XPath查询总是返回List，即使只有一个结果

### DSL 语法要点
基于代码中的实际使用：
```sql
// 创建对象
create object RSampleProject {
    code: "PROJ-DSL-001",
    revId: "A",
    name: "DSL演示项目",
    status: "PLANNING"
}

// 建立关系
relation RSampleProject(code='PROJ-DSL-001' and revId='A') {
    -> RSampleTask(code='TASK-DSL-001' and revId='A') as tasks
}

// 查询对象
show object RSampleProject(name like '%DSL%')

// 树形展示
show object RSampleProject(id=123) as tree with {
    maxDepth: 5, relations: [tasks, subTasks, attachments]
}
       
// 更新对象
update object RSampleProject where code = 'PROJ-DSL-001' {
    status: "IN_PROGRESS"
}
```

这个示例为EMOP平台的关系功能提供了全面的演示。