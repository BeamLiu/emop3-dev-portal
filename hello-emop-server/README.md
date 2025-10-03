# Hello EMOP Server Full

这是一个完整的EMOP插件开发示例项目，演示了如何创建自定义模型(ModelObject)及模型处理的业务逻辑(Service)，并在IDE中直接启动和调试。项目使用EMOP WebConsole内嵌(Embedding)方式启动，并包含客户端测试模块。

## 系统架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        Client 客户端                            │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐              ┌─────────────────┐           │
│  │   RPC Client    │              │  HTTP Client    │           │
│  └─────────────────┘              └─────────────────┘           │
└─────────────────────────────────────────────────────────────────┘
           │                                  │
           │ RPC                              │ HTTP REST
           │                                  │
           ▼                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                     EMOP Server                                 │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │ hello-plugin-api│  │   hello-plugin  │  │   DSL Engine    │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Infrastructure                               │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐              ┌─────────────────┐           │
│  │     Redis       │              │    Database     │           │
│  └─────────────────┘              └─────────────────┘           │
└─────────────────────────────────────────────────────────────────┘
```

### 架构说明

- **Client 客户端**: 支持RPC和HTTP REST两种调用方式
- **EMOP Server**: 本次演示涉及三个核心组件
  - hello-plugin-api：插件API定义
  - hello-plugin：插件业务实现
  - DSL Engine：数据查询引擎，支持HTTP和RPC两种调用方式
- **Infrastructure**: 基础设施层，包含Redis和Database

## 项目结构

```
hello-emop-server-full/
├── hello-plugin-api/          # 插件API模块 - 定义领域模型和服务接口
├── hello-plugin/              # 插件实现模块 - 服务实现
├── hello-server/              # 服务器模块 - 启动类和配置，内置了EMOP Server启动
├── hello-client/              # 客户端测试模块 - RPC、REST和DSL测试
└── pom.xml                    # 父POM
```

## 功能特性

### 1. HelloTask 领域模型
- 继承自 `ItemRevision`
- 使用 `@PersistentEntity` 注解标记为持久化实体
- 包含任务管理的核心属性：标题、描述、状态、优先级、截止时间等
- 支持任务状态和优先级枚举
- 使用 `@QuerySqlField` 注解支持数据库查询

### 2. HelloTaskService 服务
- 使用 `@Remote` 注解支持RPC调用
- 提供 `sayHello` 方法演示RPC服务调用
- 通过 `ObjectService` 访问数据库中的任务对象

### 3. 客户端测试模块
- 提供完整的REST API测试功能
- 支持RPC服务调用测试
- 支持DSL数据操作测试
- 演示创建、查询、更新、删除操作

### 4. REST API 支持
通过EMOP平台的基于`元数据`的数据对象处理，自动提供以下REST API：

- `GET /api/data/query/HelloTask` - 查询所有任务
- `GET /api/data/query/HelloTask/page` - 分页查询任务
- `POST /api/data/HelloTask` - 创建新任务
- `PUT /api/data/{id}` - 更新任务
- `DELETE /api/data/{id}` - 删除任务

## 快速开始

### 1. 确认环境
- 保证以下服务可用, 并设置本地的`hosts`解析
```
# 缓存 Redis
192.168.10.103 cache-dev.emop.emopdata.com
# 注册中心(建议使用本地的consul，避免注册到公共环境污染公共环境的服务列表)
127.0.0.1 registry-dev.emop.emopdata.com
# 数据库集群域名映射
192.168.10.103 emop-db-master-dev.emop.emopdata.com
```
- 确认 maven 已经使用了阿里云私仓
- JDK 17+

### 2. 构建项目
```bash
cd hello-emop-server
mvn clean install
```

### 2. 启动服务器
在IDE中直接运行 `HelloServerApplication.main()` 方法，或者使用命令行：
```bash
cd hello-server
java -jar target/hello-server-1.0.0-SNAPSHOT.jar
```

使用`hello-server/src/resources/application.yml`中的数据库连接信息，检查对应的`SAMPLE.hello_task`已经创建，并且数据库表与`HelloTask.java`中的信息一致。

🔔启动性能:

EMOP Server启动的时候会从java class定义映射到元数据，然后再将元数据映射到数据库schema，因此启动过程比较长，第一次启动完成后，初始化了对应的数据库的schema后，可以添加三个启动参数为`true`，以加快启动速度：
- skipClass2MetadataSyncChecking：跳过class定义到元数据的映射
- skipMetadata2TableSyncChecking：跳过元数据到数据库的映射

当修改了`hello-plugin-api`中的存储class定义，可以把上面几个参数设置为`false`，重启后自动更新对应的元数据和表结构。
```bash
java -jar target/hello-server-1.0.0-SNAPSHOT.jar -DskipClass2MetadataSyncChecking=true -DskipMetadata2TableSyncChecking=true
```

### 3. 访问应用
查看swagger地址: http://localhost:870/webconsole/api
  
### 4. 运行客户端测试
在IDE中直接运行 `HelloClientApplication.main()`
