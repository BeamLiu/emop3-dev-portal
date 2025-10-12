# Integration Test

EMOP 平台集成测试项目，包含功能测试和性能测试套件。

## 项目说明

本项目提供了 EMOP 平台的领域模型定义（如 `SampleDocument`、`SampleDataset` 等），用于集成测试。这些领域模型已被 `hello-emop-server/hello-server` 项目依赖，作为测试服务端的数据模型。

## 前置条件

### 1. 环境准备
确保以下服务可用，并配置本地 `hosts` 解析：
```
# 缓存 Redis
192.168.10.103 cache-dev.emop.emopdata.com

# 注册中心（建议使用本地 consul，避免污染公共环境）
127.0.0.1 registry-dev.emop.emopdata.com

# 数据库集群
192.168.10.103 emop-db-master-dev.emop.emopdata.com
```

### 2. 启动测试服务器
集成测试需要连接到运行中的 EMOP 服务器。使用 `hello-emop-server/hello-server` 作为测试服务端：

```bash
# 进入 hello-server 目录
cd dev-portal/hello-emop-server/hello-server

# 方式1：使用 Maven 启动
mvn spring-boot:run

# 方式2：使用 JAR 包启动（需先构建）
cd ..
mvn clean install
cd hello-server
java -jar target/hello-server-1.0.0-SNAPSHOT.jar
```

或者在 IDE 中直接运行 `io.emop.example.HelloServerApplication` 主类。

服务启动后，可以访问：
- Swagger API 文档: http://localhost:8870/webconsole/api
- Consul 注册中心: http://localhost:8500

## 运行测试

### 运行功能集成测试（默认）
```bash
mvn test
```

默认只运行功能集成测试套件，不包含性能测试。

### 运行性能测试套件
性能测试需要单独运行：
```bash
mvn test -Dtest=EmopPerformanceTestSuite
```

### 运行所有测试（包含性能测试）
```bash
mvn test -Dtest=Emop*TestSuite
```

### 在 IDE 中运行
直接运行以下测试类：
- `io.emop.integrationtest.EmopIntegrationTestSuite` - 功能集成测试
- `io.emop.integrationtest.EmopPerformanceTestSuite` - 性能测试

## 测试结构

```
integration-test/
├── src/main/java/
│   └── io/emop/integrationtest/domain/    # 测试领域模型
│       ├── SampleDocument.java
│       ├── SampleDataset.java
│       └── ...
└── src/test/java/
    └── io/emop/integrationtest/
        ├── EmopIntegrationTestSuite.java  # 功能测试套件
        ├── EmopPerformanceTestSuite.java  # 性能测试套件
        ├── usecase/                       # 功能测试用例
        └── performance/                   # 性能测试用例
```

## 测试报告

测试完成后，可以在以下位置查看报告：
- XML 报告: `target/surefire-reports/`
- HTML 报告: `target/surefire-reports/surefire-report.html`

## 注意事项

1. 测试前必须先启动 `hello-server`，否则测试会因无法连接服务而失败
2. 首次启动 `hello-server` 时会自动初始化数据库表结构，启动时间较长（约 1-2 分钟）
3. 测试失败不会中断构建（配置了 `testFailureIgnore=true`）
4. 性能测试可能需要较长时间，建议单独运行
