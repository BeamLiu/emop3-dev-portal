# CAD Integration Sample - CAD集成示例项目

这是一个完整的CAD集成示例项目，演示如何实现CAD客户端与EMOP平台的集成，包括客户端调用示例、服务端定制化扩展和浏览器模拟器。

## 项目概述

本项目包含三个子模块：

### 1. [cad-integration-client](./cad-integration-client/README.md) - CAD集成客户端示例

模拟CAD客户端（如Creo、SolidWorks等）与EMOP平台的集成，展示完整的数据流转过程。

**核心功能：**
- ✅ **保存到EMOP**：将CAD模型和BOM结构上传到EMOP系统
- ✅ **从EMOP打开**：从EMOP系统下载CAD模型和BOM结构
- ✅ 动态ID适配：自动处理服务器端动态生成的ID
- ✅ ZIP文件重组：根据服务器返回的fileId自动重组文件结构
- ✅ 多站点支持：支持异地卷架构，自动选择最优站点
- ✅ 批量文件操作：使用bulk-upload-zip接口高效上传

**适用场景：**
- CAD插件开发者学习如何调用EMOP API
- 理解CAD集成的完整数据流程
- 测试和验证CAD集成功能

### 2. [cad-integration-server](./cad-integration-server/README.md) - CAD集成服务端定制化示例

演示如何通过扩展点机制对CAD集成功能进行定制化开发。

**核心功能：**
- ✅ **ItemEntity处理扩展**：在对比、保存、加载等关键节点插入自定义逻辑
- ✅ **属性处理扩展**：属性转换、验证、计算派生属性
- ✅ **BOM结构处理扩展**：自定义BOM结构处理和ItemCode生成规则
- ✅ **文件处理扩展**：文件过滤、类型判断、文件转换
- ✅ **验证扩展**：自定义业务规则验证

**适用场景：**
- 企业定制化CAD集成功能
- 实现特定的业务规则和验证逻辑
- 与其他系统（如ERP、MES）集成

### 3. [cad-client-simulator](./cad-client-simulator/README.md) - CAD客户端浏览器模拟器

使用 JBR CEF 内嵌浏览器访问 CAD 集成前端，模拟真实的 CAD 客户端环境。

**核心功能：**
- ✅ **内嵌 Chromium 浏览器**：基于 JetBrains Runtime with JCEF
- ✅ **完整浏览器功能**：前进、后退、刷新、地址栏
- ✅ **开发者工具**：F12 或远程调试（端口 9222）
- ✅ **快捷键支持**：F5 刷新、F12 开发者工具
- ✅ **默认访问前端**：`http://localhost:4200/cad-integration2/`

**适用场景：**
- 测试 CAD 集成前端功能
- 模拟真实 CAD 客户端浏览器环境
- 开发和调试前端界面
- 演示 CAD 集成完整流程

## 快速开始

### 环境准备

确保以下服务可用，并设置本地hosts解析：

```bash
# 注册中心
192.168.10.103 registry-dev.emop.emopdata.com

# CAD Integration服务
192.168.10.103 dev.emop.emopdata.com

# MinIO Proxy服务
192.168.10.103 minioproxy-dev.emop.emopdata.com

# MinIO存储服务
192.168.10.103 storage-dev.emop.emopdata.com

# 缓存 Redis
192.168.10.103 cache-dev.emop.emopdata.com

# 数据库
192.168.10.103 emop-db-master-dev.emop.emopdata.com
```

### 构建项目

```bash
# 在项目根目录执行
cd dev-portal/cad-integration-sample
mvn clean install
```

### 运行示例

#### 1. 启动服务端（必须先启动）

```bash
cd cad-integration-server
mvn spring-boot:run
```

服务启动后访问：http://localhost:870/webconsole/api

#### 2. 运行客户端示例

```bash
cd cad-integration-client

# 运行所有场景
mvn exec:java

# 运行单个场景
mvn exec:java -Dexec.args="save"    # 保存到EMOP场景
mvn exec:java -Dexec.args="open"    # 从EMOP打开场景
```

#### 3. 运行浏览器模拟器（可选）

**前置条件：**
- 下载 JBR (JetBrains Runtime) with JCEF
- 前端项目需要运行在 `http://localhost:4200/cad-integration2/`

```bash
cd cad-client-simulator

# 1. 配置 JBR 路径
cp .env.example .env
nano .env  # 设置 JBR_HOME

# 2. 编译和运行
./compile.sh
./run.sh
```

详细说明请参考：[cad-client-simulator/README.md](./cad-client-simulator/README.md)

## 项目结构

```
cad-integration-sample/
├── cad-integration-client/          # 客户端示例
│   ├── src/main/java/               # Java源码
│   │   └── io/emop/example/cad/
│   │       ├── CadIntegrationClientDemo.java  # 主入口
│   │       ├── model/               # 数据模型
│   │       ├── service/             # 核心服务
│   │       ├── scenario/            # 场景实现
│   │       └── util/                # 工具类
│   ├── src/main/resources/
│   │   └── test-data/               # 测试数据
│   ├── pom.xml
│   └── README.md                    # 详细文档
│
├── cad-integration-server/          # 服务端定制化示例
│   ├── src/main/java/               # Java源码
│   │   └── io/emop/example/cad/
│   │       ├── CustomCadIntegrationStarter.java  # 启动类
│   │       └── extension/           # 扩展实现
│   ├── src/main/resources/
│   │   ├── application.yml          # 应用配置
│   │   └── cadconfig-custom.yml     # CAD定制配置
│   ├── pom.xml
│   └── README.md                    # 详细文档
│
├── cad-client-simulator/            # 浏览器模拟器
│   ├── src/main/java/               # Java源码
│   │   └── io/emop/example/cad/simulator/
│   │       └── CadClientSimulator.java  # 主程序
│   ├── pom.xml
│   ├── README.md                    # 详细文档
│   ├── run.sh                       # 启动脚本
│   └── .env.example                 # 环境配置示例
│
├── config/                          # 共享配置
├── pom.xml                          # 父POM
└── README.md                        # 本文件
```

## 核心流程

### 保存到EMOP流程

```
CAD客户端                          EMOP平台
    │                                  │
    │  1. Compare BOM数据              │
    ├──────────────────────────────────>│
    │  <─ 返回带临时ID的数据            │
    │                                  │
    │  2. Post BOM结构                 │
    ├──────────────────────────────────>│
    │  <─ 创建Item/Component/File      │
    │     返回最终fileId                │
    │                                  │
    │  3. 重组ZIP文件                   │
    │     (按最终fileId组织)            │
    │                                  │
    │  4. 批量上传ZIP                   │
    ├──────────────────────────────────>│
    │  <─ 更新File对象                  │
    │                                  │
```

### 从EMOP打开流程

```
CAD客户端                          EMOP平台
    │                                  │
    │  1. 获取BOM结构                   │
    ├──────────────────────────────────>│
    │  <─ 返回完整BOM树                 │
    │                                  │
    │  2. 批量下载文件                   │
    │     (传递fileId列表)              │
    ├──────────────────────────────────>│
    │  <─ 返回ZIP文件包                 │
    │                                  │
    │  3. 解压到本地                     │
    │                                  │
```

## 扩展点说明

服务端提供了多个扩展点，可以在关键节点插入自定义逻辑：

| 扩展点 | 说明 | 典型用途 |
|--------|------|----------|
| CadItemEntityProcessor | ItemEntity处理 | 对比前后处理、保存前后处理、加载后处理 |
| CadPropertyProcessor | 属性处理 | 单位转换、计算派生属性、属性验证 |
| CadBomStructureProcessor | BOM结构处理 | 过滤虚拟件、调整层级、自定义ItemCode |
| CadFileProcessor | 文件处理 | 文件过滤、类型判断、文件转换 |
| CadValidationProcessor | 验证 | 业务规则验证、数据完整性检查 |

详细说明请参考 [服务端文档](./cad-integration-server/README.md)。

## 技术栈

- **Java**: 17
- **Spring Boot**: 3.1.0
- **Maven**: 3.x
- **HTTP Client**: Unirest
- **JSON**: Jackson
- **日志**: SLF4J + Logback

## 常见问题

### Q1: 测试数据从哪里来？

A: 测试数据是从真实的CAD集成场景中录制的，包含了完整的BOM结构和CAD文件。位于 `cad-integration-client/src/main/resources/test-data/`。

### Q2: 如何添加自己的定制化逻辑？

A: 在 `cad-integration-server/src/main/java/io/emop/example/cad/extension/` 目录下创建新的扩展类，实现对应的扩展点接口即可。

### Q3: 如何在多站点环境下使用？

A: 调用 `/api/site-selection/select` API，系统会根据用户属性（如组织、CAD类型）自动选择最优站点。详见 [客户端文档](./cad-integration-client/README.md#多站点支持multisite)。

## 相关文档

- [客户端详细文档](./cad-integration-client/README.md)
- [服务端详细文档](./cad-integration-server/README.md)
- [浏览器模拟器文档](./cad-client-simulator/README.md)
- [EMOP平台开发指南](../docs/index.md)

## 注意事项

1. **环境配置**：确保所有服务的hosts解析正确配置
2. **JBR 要求**：运行 cad-client-simulator 需要使用 JetBrains Runtime (JBR) with JCEF，普通 JDK 不支持
3. **前端项目**：浏览器模拟器默认访问 `http://localhost:4200/cad-integration2/`，需要确保前端项目已启动
