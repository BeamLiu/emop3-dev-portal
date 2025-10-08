# CAD Integration Sample - CAD集成定制化示例

这是一个CAD集成定制化的示例项目，演示如何通过扩展点机制对CAD集成功能进行定制化开发。

## 项目结构

```
cad-integration-server/
├── src/
│   └── main/
│       ├── java/
│       │   └── io/emop/example/cad/
│       │       ├── CustomCadIntegrationStarter.java    # 启动类
│       │       └── extension/                          # 扩展实现
│       │           ├── CustomItemEntityProcessor.java  # ItemEntity处理扩展
│       │           ├── CustomPropertyProcessor.java    # 属性处理扩展
│       │           └── CustomBomStructureProcessor.java # BOM结构处理扩展
│       └── resources/
│           ├── application.yml                         # 应用配置
│           └── cadconfig-custom.yml                    # CAD定制配置
├── pom.xml
└── README.md
```

## 扩展点说明

### 1. CadItemEntityProcessor - ItemEntity处理扩展点

在ItemEntity处理的关键节点提供定制化能力：

- `beforeCompare(entities)` - 对比前处理
- `afterCompare(entities)` - 对比后处理
- `beforeSave(entities)` - 保存前处理
- `afterSave(entities)` - 保存后处理
- `afterLoad(entities)` - 加载后处理

**示例用途：**
- 对比前：补充默认值、预处理数据
- 对比后：统计分析、调整UseType
- 保存前：最后验证、数据清洗
- 保存后：发送通知、触发工作流
- 加载后：补充额外信息（如ERP数据）

### 2. CadPropertyProcessor - 属性处理扩展点

提供属性转换、验证等定制化能力：

- `processProperties(entity, props)` - 属性处理
- `validateProperties(entity, props)` - 属性验证

**示例用途：**
- 单位转换（英寸→毫米）
- 计算派生属性（成本、体积等）
- 设置默认值
- 必填字段验证
- 业务规则验证

### 3. CadBomStructureProcessor - BOM结构处理扩展点

提供BOM结构处理的定制化能力：

- `processBomStructure(root)` - BOM结构处理
- `resolveItemCode(entity)` - ItemCode解析

**示例用途：**
- 过滤虚拟件
- 调整BOM层级
- 自定义ItemCode生成规则

### 4. CadFileProcessor - 文件处理扩展点

提供文件处理的定制化能力：

- `processFiles(entity, files)` - 文件处理
- `resolveFileType(file)` - 文件类型解析

**示例用途：**
- 文件过滤
- 文件类型自定义判断
- 文件转换

### 5. CadValidationProcessor - 验证扩展点

提供自定义验证逻辑：

- `validate(entities)` - 验证ItemEntity列表

**示例用途：**
- 业务规则验证
- 数据完整性检查
- 权限验证

## 使用CadContext获取上下文信息

所有扩展点都可以通过 `CadContext` 获取上下文信息：

```java
// 获取CAD客户端类型
String client = CadContext.currentClient(); // "Creo", "SolidWorks"等

// 获取操作类型
String operation = CadContext.currentOperation(); // "COMPARE", "SAVE", "LOAD"

// 获取当前用户
String username = CadContext.currentUsername();

// 获取/设置自定义属性
CadContext.setAttribute("key", value);
Object value = CadContext.getAttribute("key");

// 获取CAD操作服务
CadOperationService ops = CadContext.operation();
```

## 快速开始

### 1. 环境准备

确保以下服务可用，并设置本地hosts解析：

```
# 缓存 Redis
192.168.10.103 cache-dev.emop.emopdata.com

# 注册中心(本地)
127.0.0.1 registry-dev.emop.emopdata.com

# 数据库
192.168.10.103 emop-db-master-dev.emop.emopdata.com
```

### 2. 构建项目

```bash
cd cad-integration-sample
mvn clean install
```

### 3. 启动应用

在IDE中运行 `CustomCadIntegrationStarter.main()` 方法，或使用命令行：

```bash
mvn spring-boot:run
```

或者打包后运行：

```bash
mvn clean package
java -jar target/cad-integration-server-1.0.0-SNAPSHOT.jar
```

### 4. 访问应用

- Swagger API文档: http://localhost:891/webconsole/api

## 定制化开发指南

### 1. 实现扩展点

创建一个类实现对应的扩展点接口，并添加 `@Component` 注解：

```java
@Slf4j
@Component
public class MyCustomProcessor implements CadItemEntityProcessor {
    
    @Override
    public void beforeCompare(List<ItemEntity> entities) {
        // 你的定制逻辑
        log.info("执行自定义对比前处理");
    }
    
    @Override
    public int getOrder() {
        return 10; // 优先级，数字越小优先级越高
    }
}
```

### 2. 配置文件定制

在 `cadconfig-custom.yml` 中添加或覆盖配置：

```yaml
# 覆盖默认配置
enableCascadingUpgrade: true

# 添加自定义属性
syncProps:
  - display: "自定义属性"
    cad: "default"
    type: "String"
    propName: "customProp"
    strategy: "TWO_WAY"
```

### 3. 扩展点可选

所有扩展点都是**可选的**，不实现也不影响默认功能。你可以：

- 只实现需要的扩展点
- 只覆盖需要的方法（其他方法使用default实现）
- 通过 `getOrder()` 控制多个扩展的执行顺序

### 4. 异常处理

扩展点中抛出的异常会被捕获并记录日志，不会影响其他扩展的执行。如果需要阻止流程继续，可以抛出 `IllegalArgumentException` 等异常。

## 示例场景

### 场景1：单位转换

CAD中使用英寸，EMOP中使用毫米，需要自动转换：

```java
@Component
public class UnitConversionProcessor implements CadPropertyProcessor {
    @Override
    public void processProperties(ItemEntity entity, Map<String, Object> props) {
        if (props.containsKey("length")) {
            double lengthInch = ((Number) props.get("length")).doubleValue();
            props.put("length", lengthInch * 25.4); // 转换为毫米
        }
    }
}
```

### 场景2：自动计算成本

根据材料和重量自动计算成本：

```java
@Component
public class CostCalculationProcessor implements CadPropertyProcessor {
    @Override
    public void processProperties(ItemEntity entity, Map<String, Object> props) {
        String material = (String) props.get("material");
        Number weight = (Number) props.get("weight");
        double cost = costService.calculate(material, weight.doubleValue());
        props.put("estimatedCost", cost);
    }
}
```

### 场景3：保存后发送通知

保存完成后发送邮件或消息通知：

```java
@Component
public class NotificationProcessor implements CadItemEntityProcessor {
    @Override
    public void afterSave(List<ItemEntity> entities) {
        String message = String.format("用户 %s 保存了 %d 个零部件", 
            CadContext.currentUsername(), entities.size());
        notificationService.send(message);
    }
}
```

## 注意事项

1. **线程安全**：CadContext 使用 ThreadLocal，在多线程环境下是安全的
2. **性能考虑**：扩展点会在关键路径上执行，注意性能影响
3. **异常处理**：扩展点中的异常会被捕获，不会中断主流程（除非是验证类异常）
4. **优先级**：多个扩展按 `getOrder()` 返回值排序执行，数字越小优先级越高
