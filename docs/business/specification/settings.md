# EMOP配置规范

## 1. 概述

EMOP配置管理系统(`EMOPConfig`)提供了一个统一、灵活且可扩展的配置管理框架，用于处理EMOP平台及其所有组件的配置需求。该系统采用单例模式实现，不依赖任何外部框架，可在Spring容器初始化前提供配置支持，同时兼容Docker容器化部署环境。

### 1.1 系统架构

EMOP配置管理系统由以下核心组件构成：

- **配置加载器**：负责从不同来源加载配置
- **配置缓存**：存储已解析的配置值
- **来源追踪器**：记录每个配置项的来源
- **配置解析器**：处理YAML、Properties格式和占位符
- **YAML配置管理器**：处理专用YAML文件（如lifecycle.yml）
- **诊断工具**：提供配置报告和问题排查功能

## 2. 配置加载机制

### 2.1 基础配置来源及优先级

配置按以下优先级顺序加载（从高到低）：

| 优先级 | 配置来源 | 说明 | 示例 |
|:---:|----------|------|------|
| 1 | 系统属性 | JVM启动参数 | `-Demop.server.port=870` |
| 2 | 环境变量 | 系统环境变量，自动格式转换 | `EMOP_SERVER_PORT=870` |
| 3 | 外部配置文件 | 外部配置目录中的配置文件 | `[配置目录]/emop-server/application-prod.yml` |
| 4 | 内置配置文件 | 应用classpath中的配置文件 | `classpath:application.yml` |

高优先级的配置来源会覆盖低优先级的配置项。

### 2.2 专用YAML配置文件加载顺序

专用YAML配置文件（如`lifecycle.yml`）按照以下优先级顺序加载（从低到高）：

| 优先级 | 配置来源 | 说明 | 示例 |
|:---:|----------|------|------|
| 1 | 类路径基础配置 | 应用内置默认配置 | `classpath:lifecycle.yml` |
| 2 | 类路径Profile配置 | 应用内置环境特定配置 | `classpath:lifecycle-prod.yml` |
| 3 | 外部基础配置 | 安装目录下的共用配置 | `[配置目录]/lifecycle.yml` |
| 4 | 外部Profile配置 | 安装目录下的环境特定配置 | `[配置目录]/lifecycle-prod.yml` |
| 5 | 组件基础配置 | 特定组件的基础配置 | `[配置目录]/emop-server/lifecycle.yml` |
| 6 | 组件Profile配置 | 特定组件的环境特定配置 | `[配置目录]/emop-server/lifecycle-prod.yml` |

专用YAML配置采用深度合并策略，高优先级配置会逐层覆盖低优先级配置，但保留未覆盖的属性。

### 2.3 配置目录解析策略

外部配置目录按以下优先级顺序确定：

1. **API设置目录**：通过`EMOPConfig.setExternalConfigDir()`设置
2. **环境变量目录**：`EMOP_CONFIG_DIR`环境变量
3. **系统属性目录**：`emop.config.dir`系统属性
4. **默认目录**：当前工作目录(`user.dir`) + `/config`

Windows和Unix系统的路径处理会自动适配，确保跨平台兼容性。

### 2.4 Profile支持

配置系统支持Profile概念，用于区分不同环境的配置：

- Profile通过`emop.profiles.active`系统属性或`EMOP_PROFILES_ACTIVE`环境变量激活
- 多个Profile使用逗号分隔，如`prod,ha`
- Profile特定配置文件（如`application-prod.yml`）具有高于基础配置文件的优先级
- 多个激活的Profile按声明顺序应用，后面的覆盖前面的

## 3. 命名规范

### 3.1 环境变量映射规则

环境变量采用大写加下划线格式，自动映射到点号分隔的配置键：

| 环境变量 | 映射的配置键 | 说明 |
|---------|------------|------|
| `EMOP_SERVER_PORT` | `emop.server.port` | 服务器端口 |
| `EMOP_STORAGE_DATA_PATH` | `emop.storage.data.path` | 存储路径 |
| `EMOP_PROFILES_ACTIVE` | - | 激活的profile列表 |
| `EMOP_CONFIG_DIR` | - | 外部配置目录 |

## 4. 配置文件组织

### 4.1 目录结构

推荐的配置目录结构：

```
[配置根目录]/
├── emop-server/
│   ├── application.yml              # 基础配置
│   ├── application-dev.yml          # 开发环境配置
│   ├── application-prod.yml         # 生产环境配置
│   ├── lifecycle.yml                # 服务器生命周期配置
│   └── lifecycle-prod.yml           # 生产环境生命周期配置
├── foundation/
│   ├── application.yml
│   └── application-prod.yml
├── plm/
│   ├── application.yml
│   └── application-prod.yml
├── application.yml                  # 全局配置
├── application-prod.yml             # 全局生产环境配置
├── lifecycle.yml                    # 全局生命周期配置
└── lifecycle-prod.yml               # 全局生产环境生命周期配置
```

### 4.2 YAML配置风格指南

```yaml
# 顶级组件命名空间
emop:
  # 存储组件配置
  storage:
    data:
      # 使用环境变量占位符，支持默认值
      path: ${EMOP_DATA_DIR:/data/emop}
    wal:
      path: ${EMOP_DATA_DIR:/data/emop}/wal
```

## 5. API参考

### 5.1 核心API

| 方法 | 返回值 | 说明 |
|------|--------|------|
| `EMOPConfig.getInstance()` | EMOPConfig | 获取配置系统单例实例 |
| `EMOPConfig.setExternalConfigDir(String)` | void | 设置外部配置目录 |
| `EMOPConfig.getExternalConfigDir()` | String | 获取当前外部配置目录 |
| `getString(String, String)` | String | 获取字符串配置值 |
| `getInt(String, int)` | int | 获取整数配置值 |
| `getLong(String, long)` | long | 获取长整型配置值 |
| `getDouble(String, double)` | double | 获取浮点数配置值 |
| `getBoolean(String, boolean)` | boolean | 获取布尔值配置值 |
| `getPropertiesWithPrefix(String)` | Properties | 获取指定前缀的所有配置 |
| `getConfigSource(String)` | String | 获取配置项的来源 |
| `getActiveProfiles()` | Set\<String\> | 获取所有激活的profile |
| `getConfigurationReport()` | String | 获取配置摘要报告 |
| `reload()` | void | 重新加载所有配置 |

### 5.2 专用YAML配置API

| 方法 | 返回值 | 说明 |
|------|--------|------|
| `getYamlConfig(String)` | Map\<String, Object\> | 获取YAML配置文件内容 |
| `getYamlConfigAsStream(String)` | InputStream | 获取YAML配置为输入流 |
| `getYamlConfigSource(String)` | String | 获取YAML配置文件的来源 |

### 5.3 测试与诊断API

| 方法 | 返回值 | 说明 |
|------|--------|------|
| `setProperty(String, String, String)` | void | 手动设置配置（测试用） |
| `clearAll()` | void | 清除所有配置（测试用） |
| `setActiveProfiles(String...)` | void | 手动设置激活的profile |

## 6. 使用示例

### 6.1 基本配置访问

```java
// 获取EMOPConfig实例
EMOPConfig config = EMOPConfig.getInstance();

// 访问配置（提供默认值）
String dataPath = config.getString("emop.storage.data.path", "/default/path");
int port = config.getInt("emop.server.port", 870);
boolean workflowEnabled = config.getBoolean("emop.lifecycle.workflow.enabled", true);

// 获取配置来源（用于调试）
String source = config.getConfigSource("emop.server.port");
System.out.println(source);  // 输出: "系统属性" 或 "环境变量(EMOP_SERVER_PORT)"
```

### 6.2 设置外部配置目录

```java
// 在应用启动早期设置配置目录
EMOPConfig.setExternalConfigDir("/etc/emop/config");

// 获取配置实例并使用
EMOPConfig config = EMOPConfig.getInstance();

// 检查当前使用的配置目录
String configDir = EMOPConfig.getExternalConfigDir();
System.out.println("当前配置目录: " + configDir);
```

### 6.3 配置诊断

```java
// 使用rest API去获取
// http://localhost:870/webconsole/api/admin/config/report

// 同时也可用通过Java或Groovy获取并打印配置报告
EMOPConfig config = EMOPConfig.getInstance();
String report = config.getConfigurationReport();
System.out.println(report);

// 输出示例:
// === EMOP配置报告 ===
// 
// 激活的Profiles: [prod]
// 
// 配置项列表：
// emop.server.port                    = 870                 [来源: 系统属性]
// emop.storage.data.path              = /data/emop          [来源: 环境变量(EMOP_STORAGE_DATA_PATH)]
// emop.database.password              = ******              [来源: 外部配置(emop-server/application.yml)]
```

### 6.4 获取专用YAML配置

```java
// 获取完整的YAML配置
EMOPConfig config = EMOPConfig.getInstance();
//会自动处理文件位置及优先级
Map<String, Object> lifecycleConfig = config.getYamlConfig("lifecycle.yml"); 

// 获取lifecycle.yml的来源
String source = config.getYamlConfigSource("lifecycle.yml");
System.out.println("配置来源: " + source);
```

### 6.5 动态重载配置

```java
// 更改配置目录
EMOPConfig.setExternalConfigDir("/new/config/path");

// 重新加载配置
EMOPConfig.getInstance().reload();

// 验证更新后的配置
System.out.println(EMOPConfig.getInstance().getConfigurationReport());
```

## 7. 部署场景配置

### 7.1 开发环境

```bash
# 通过系统属性设置
java -Demop.profiles.active=dev -Demop.config.dir=./config -jar emop-server.jar
```

### 7.2 测试环境

```java
// 在测试前设置测试配置
@Before
public void setUp() {
    // 设置测试特定的配置
    EMOPConfig.setExternalConfigDir("./src/test/resources/config");
    EMOPConfig.getInstance().setActiveProfiles("test");
    
    // 手动设置某些配置项
    EMOPConfig.getInstance().setProperty("emop.test.mode", "true", "测试初始化");
}
```

### 7.3 生产环境（Docker）

```dockerfile
FROM openjdk:17

# 设置环境变量
ENV EMOP_PROFILES_ACTIVE=prod
ENV EMOP_SERVER_PORT=870
ENV EMOP_DATA_DIR=/data/emop
ENV EMOP_CONFIG_DIR=/etc/emop/config

# 创建目录
RUN mkdir -p /data/emop /etc/emop/config

# 复制配置
COPY ./configs/emop-server /etc/emop/config/emop-server
COPY ./configs/lifecycle.yml /etc/emop/config/lifecycle.yml

VOLUME ["/data/emop", "/etc/emop/config"]

EXPOSE 870

CMD ["java", "-jar", "/app/emop-server.jar"]
```

### 7.4 云原生环境（Kubernetes）

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: emop-server
spec:
  template:
    spec:
      containers:
      - name: emop-server
        image: emop/emop-server:latest
        env:
        - name: EMOP_PROFILES_ACTIVE
          value: "prod,k8s"
        - name: EMOP_SERVER_PORT
          value: "870"
        - name: EMOP_CONFIG_DIR
          value: "/etc/emop/config"
        volumeMounts:
        - name: emop-config
          mountPath: /etc/emop/config
      volumes:
      - name: emop-config
        configMap:
          name: emop-server-config
```

## 8. 最佳实践

### 8.1. 配置值层次规划

按照以下层次规划配置值：

1. **默认值**：在代码中提供合理的默认值
2. **应用配置**：在内置配置文件中设置通用值
3. **环境特定配置**：在Profile配置文件中设置环境特定值
4. **部署配置**：通过环境变量设置部署特定值
5. **运行时配置**：通过系统属性设置临时覆盖值

### 8.2. 敏感信息处理

- 敏感信息（密码、密钥等）不应硬编码在配置文件中
- 推荐使用环境变量注入敏感信息
- 使用引用表达式`${ENV_VAR}`而非直接值
- 配置报告和日志中自动遮蔽敏感信息

### 8.3. 配置文档化

- 所有配置项应在团队文档中清晰记录
- 记录配置项的用途、默认值、可选项和影响
- 使用配置报告功能生成当前环境的配置清单
- 维护配置历史变更日志

### 8.4. 专用YAML配置管理

- 使用单一配置文件，实现配置聚合简化管理
- 利用Profile区分环境配置，如`lifecycle-prod.yml`
- 组件级配置优先于全局配置
- 使用深度合并策略确保配置完整性

## 9. 故障排除

### 9.1. 配置未生效

当配置未按预期生效时，请检查：

1. **配置优先级**：高优先级的配置可能覆盖了您的设置
2. **配置路径**：确认外部配置目录路径正确
3. **配置键名**：检查环境变量名称的大小写和格式转换
4. **配置文件格式**：确认YAML格式正确，无语法错误

使用配置报告功能诊断：

```java
// 打印配置报告
System.out.println(EMOPConfig.getInstance().getConfigurationReport());

// 检查特定配置项
String key = "emop.server.port";
System.out.println("配置键: " + key);
System.out.println("值: " + EMOPConfig.getInstance().getString(key, null));
System.out.println("来源: " + EMOPConfig.getInstance().getConfigSource(key));
```

### 9.2. 专用YAML配置问题

当专用YAML配置（如lifecycle.yml）出现问题时：

1. **检查配置来源**：使用`getYamlConfigSource()`确认实际加载的文件
2. **验证配置内容**：使用`getYamlConfig()`查看合并后的完整配置
3. **检查配置合并**：确认配置合并行为符合预期
4. **临时输出配置**：调试时可以将完整配置输出到日志

示例：
```java
EMOPConfig config = EMOPConfig.getInstance();
Map<String, Object> lifecycleConfig = config.getYamlConfig("lifecycle.yml");
Yaml yaml = new Yaml();
log.debug("加载的生命周期配置:\n{}", yaml.dump(lifecycleConfig));
```

### 9.3. 路径问题

Windows和Linux环境下的路径问题：

- **Windows路径**：使用正斜杠`/`或双反斜杠`\\`
- **Linux路径**：使用正斜杠`/`
- **路径检查**：确认配置目录存在且可读
- **相对路径**：相对路径基于工作目录解析
- **Docker路径**：在容器中确认挂载点与配置路径一致