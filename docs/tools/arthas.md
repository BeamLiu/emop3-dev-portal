# 使用 Arthas 进行性能分析

## 环境搭建
### Arthas 安装配置

Arthas 提供多种安装方式：

1. 快速安装（推荐）
```bash
curl -O https://arthas.aliyun.com/arthas-boot.jar
java -jar arthas-boot.jar
```

#### 启动 Arthas
1. 找到目标进程
```bash
# 查看所有 Java 进程并 attach 过去
java -jar arthas-boot.jar
```
效果示意
```java
D:\temp\emop3>java -jar arthas-boot.jar
[INFO] JAVA_HOME: D:\jdk-17.0.10
[INFO] arthas-boot version: 4.0.4
[INFO] Found existing java process, please choose one and input the serial number of the process, eg : 1. Then hit ENTER.
* [1]: 114400 org.jetbrains.idea.maven.server.RemoteMavenServer36
  [2]: 82864 org.jetbrains.jps.cmdline.Launcher
  [3]: 113796 io.emop.webconsole.EmopServerWebConsole
  [4]: 16696
  [5]: 83128
  [6]: 100108 org.jetbrains.jps.cmdline.Launcher
3
[INFO] arthas home: C:\Users\86136\.arthas\lib\4.0.4\arthas
[INFO] Try to attach process 113796
Picked up JAVA_TOOL_OPTIONS:
[INFO] Attach process 113796 success.
[INFO] arthas-client connect 127.0.0.1 3658
  ,---.  ,------. ,--------.,--.  ,--.  ,---.   ,---.
 /  O  \ |  .--. ''--.  .--'|  '--'  | /  O  \ '   .-'
|  .-.  ||  '--'.'   |  |   |  .--.  ||  .-.  |`.  `-.
|  | |  ||  |\  \    |  |   |  |  |  ||  | |  |.-'    |
`--' `--'`--' '--'   `--'   `--'  `--'`--' `--'`-----'

wiki       https://arthas.aliyun.com/doc
tutorials  https://arthas.aliyun.com/doc/arthas-tutorials.html
version    4.0.4
main_class
pid        113796
```
2. 打开浏览器访问 `http://localhost:3658`
3. 输入`arthas`监控命令，命令获取可以借助`LLM`或者`IDE`(推荐，插件安装见后续步骤)

更多安装选项和详细配置请参考 [Arthas 官方安装文档](https://arthas.aliyun.com/doc/install-detail.html)

### Intellij IDE 插件安装与使用(可选，方便获取arthas命令)

1. 打开 IDE 插件市场（Settings/Preferences → Plugins）
2. 搜索并安装 "Arthas Idea" 最新版插件
3. 重启 IDE 完成安装

### IDE 集成功能

IDE 中可以通过右键菜单访问 Arthas 功能：
- 在代码编辑器中右键 → Show Context Actions (Ctrl + .) → Arthas Command
- 常用命令快速访问：
  - trace：方法调用路径追踪
  - stack：方法调用栈分析
  - monitor：方法执行监控
  - watch：方法执行数据观察
  - logger：日志级别设置
  - decompile：即时反编译
  - ognl：运行时表达式执行

## 常用性能分析命令

### trace 命令

trace 命令用于追踪方法执行时间和调用关系。示例分析 `checkBusinessKey` 方法：

```bash
trace io.emop.service.query.ObjectServiceImpl checkBusinessKey -n 5 --skipJDKMethod false
```
示例输出如下:
```
Press Q or Ctrl+C to abort.
Affect(class count: 1 , method count: 1) cost in 351 ms, listenerId: 1
`---ts=2025-01-20 14:18:56.150;thread_name=svc-#225;id=548;is_daemon=false;priority=5;TCCL=org.apache.ignite.internal.managers.deployment.GridDeploymentClassLoader@1eb885ab
    `---[11.6749ms] io.emop.service.query.ObjectServiceImpl:checkBusinessKey()
        +---[0.29% 0.033399ms ] io.emop.model.metadata.TypeDefinition:toPersistentInfo() #442
        +---[0.13% 0.0151ms ] io.emop.model.metadata.TypeDefinition:getBusinessUniqueKeys() #444
        +---[0.15% 0.0173ms ] java.util.List:isEmpty() #445
        +---[0.36% 0.0419ms ] io.emop.model.common.PersistentInfo:ensureCacheName() #450
        +---[0.37% 0.042699ms ] io.emop.service.tx.TransactionCacheManager:getOrCreateCache() #450
        +---[0.12% 0.0138ms ] java.util.List:iterator() #452
        +---[0.12% min=0.005301ms,max=0.0085ms,total=0.013801ms,count=2] java.util.Iterator:hasNext() #452
        +---[0.08% 0.0088ms ] java.util.Iterator:next() #452
        +---[0.11% 0.0126ms ] io.emop.model.common.BusinessUniqueKeyAware$BusinessUniqueKey:getAttributes() #454
        +---[0.09% 0.0101ms ] java.util.List:isEmpty() #455
        +---[0.10% 0.011301ms ] java.util.HashSet:<init>() #459
        +---[0.08% 0.008901ms ] java.util.List:iterator() #460
        +---[0.18% min=0.0067ms,max=0.014099ms,total=0.020799ms,count=2] java.util.Iterator:hasNext() #460
        +---[0.10% 0.011901ms ] java.util.Iterator:next() #460
        +---[0.07% 0.0087ms ] io.emop.model.common.ModelObject:getId() #461
        +---[0.17% 0.019401ms ] io.emop.model.common.ModelObject:isDirectiveUpsertForSave() #461
        +---[0.32% 0.0379ms ] java.util.LinkedHashMap:<init>() #464
        +---[0.26% 0.0301ms ] java.util.List:stream() #466
        +---[0.42% 0.0488ms ] java.util.stream.Stream:map() #467
        +---[0.35% 0.041301ms ] java.util.stream.Collectors:joining() #474
        +---[2.49% 0.2905ms ] java.util.stream.Stream:collect() #474
        +---[0.17% 0.0198ms ] java.util.Set:contains() #475
        +---[0.23% 0.026399ms ] io.emop.model.query.InTransactionDataHandler$TransactionCache:getBusinessKeyIndex() #479
        +---[0.33% 0.0381ms ] io.emop.model.query.InTransactionDataHandler$BusinessKeyIndex:findMatch() #479
        +---[0.20% 0.0232ms ] java.util.Optional:isPresent() #480
        +---[0.20% 0.0231ms ] java.util.Map:keySet() #492
        +---[0.24% 0.0286ms ] java.util.Set:stream() #492
        +---[0.16% 0.018401ms ] java.util.stream.Stream:map() #493
        +---[0.14% 0.016ms ] java.util.stream.Collectors:joining() #494
        +---[2.91% 0.3403ms ] java.util.stream.Stream:collect() #494
        +---[0.11% 0.012499ms ] io.emop.model.common.ModelObject:getId() #495
        +---[0.10% 0.0112ms ] io.emop.model.metadata.TypeDefinition:getName() #501
        +---[0.17% 0.019901ms ] io.emop.model.query.Q:objectType() #501
        +---[0.09% 0.010999ms ] io.emop.model.common.PersistentInfo:toSchemaDotTable() #502
        +---[2.28% 0.265999ms ] io.emop.model.query.Q$InitialQueryBuilder:sql() #502
        +---[42.77% 4.9929ms ] io.emop.model.query.Q$SqlQueryBuilder:queryRaw() #503
        +---[0.30% 0.0347ms ] java.util.List:stream() #506
        +---[0.15% 0.017699ms ] java.util.stream.Stream:map() #507
        +---[0.52% 0.0609ms ] java.util.stream.Stream:anyMatch() #508
        `---[0.26% 0.0306ms ] java.util.Set:add() #516
```

参数说明：
- `-n`：记录追踪次数
- `--skipJDKMethod`：是否跳过 JDK 方法调用

从示例输出分析：
- 方法总执行时间：11.67ms
- 性能瓶颈：
  - `queryRaw()`：占比 42.77%（4.99ms）
  - 流式操作：合计约占 5%
  - 数据库操作：SQL 执行耗时明显

### monitor 命令

monitor 命令用于监控方法执行统计：

```bash
monitor io.emop.service.query.ObjectServiceImpl * -n 10 --cycle 10
```
示例输出如下：
```bash
Press Q or Ctrl+C to abort.
Affect(class count: 1 , method count: 62) cost in 216 ms, listenerId: 2
 timestamp                       class                                            method                                           total           success         fail             avg-rt(ms)      fail-rate      
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 2025-01-20 14:19:39.175         io.emop.service.query.ObjectServiceImpl      lambda$execute$14                                358             358             0                0.24            0.00%          
 2025-01-20 14:19:39.174         io.emop.service.query.ObjectServiceImpl      lambda$checkBusinessKey$3                        716             716             0                0.02            0.00%          
 2025-01-20 14:19:39.174         io.emop.service.query.ObjectServiceImpl      lambda$execute$13                                358             358             0                0.00            0.00%          
 2025-01-20 14:19:39.175         io.emop.service.query.ObjectServiceImpl      execute                                          718             718             0                0.45            0.00%          
 2025-01-20 14:19:36.714         io.emop.service.query.ObjectServiceImpl      first                                            2               2               0                3.24            0.00%          
 2025-01-20 14:19:36.714         io.emop.service.query.ObjectServiceImpl      applyTransactionHandler                          2               2               0                0.47            0.00%          
 2025-01-20 14:19:39.175         io.emop.service.query.ObjectServiceImpl      save                                             358             358             0                1.47            0.00%          
 2025-01-20 14:19:39.175         io.emop.service.query.ObjectServiceImpl      setFieldWithType                                 4296            4296            0                0.00            0.00%          
 2025-01-20 14:19:39.175         io.emop.service.query.ObjectServiceImpl      handleUpsertIfNeeded                             358             358             0                0.01            0.00%          
 2025-01-20 14:19:39.174         io.emop.service.query.ObjectServiceImpl      extractParams                                    360             360             0                0.00            0.00%          
 2025-01-20 14:19:39.174         io.emop.service.query.ObjectServiceImpl      extractSql                                       360             360             0                0.08            0.00%          
 2025-01-20 14:19:36.714         io.emop.service.query.ObjectServiceImpl      lambda$applyTransactionHandler$19                2               2               0                0.04            0.00%          
 2025-01-20 14:19:36.714         io.emop.service.query.ObjectServiceImpl      lambda$first$16                                  2               2               0                1.38            0.00%          
 2025-01-20 14:19:36.713         io.emop.service.query.ObjectServiceImpl      lambda$first$15                                  2               2               0                0.06            0.00%          
 2025-01-20 14:19:36.713         io.emop.service.query.ObjectServiceImpl      buildSqlFromCondition                            2               2               0                0.36            0.00%          
 2025-01-20 14:19:39.175         io.emop.service.query.ObjectServiceImpl      checkBusinessKey                                 358             358             0                0.75            0.00%          
 2025-01-20 14:19:39.175         io.emop.service.query.ObjectServiceImpl      checkIdAndVersion                                358             358             0                0.01            0.00%          
 2025-01-20 14:19:39.175         io.emop.service.query.ObjectServiceImpl      binaryObject                                     358             358             0                0.23            0.00%          
 2025-01-20 14:19:39.176         io.emop.service.query.ObjectServiceImpl      interfaceClz                                     1436            1436            0                0.00            0.00%          
 2025-01-20 14:19:39.175         io.emop.service.query.ObjectServiceImpl      saveAll                                          358             358             0                1.45            0.00%          
 2025-01-20 14:19:39.174         io.emop.service.query.ObjectServiceImpl      lambda$checkBusinessKey$4                        716             716             0                0.01            0.00%  
```
参数说明：
- `*`：监控所有方法
- `-n`：显示记录数
- `--cycle`：统计周期（秒）

监控数据分析：
- 高频方法：
  - `setFieldWithType`：4,296 次调用
  - `lambda$checkBusinessKey$3`：716 次调用
  - `save`：358 次调用
- 性能指标：
  - `first()`：平均响应时间 3.24ms
  - `save()`：平均响应时间 1.47ms
  - `checkBusinessKey()`：平均响应时间 0.75ms

## 最佳实践

1. 使用 monitor 进行宏观监控，识别高频调用方法
2. 使用 trace 深入分析高延迟方法
3. 重点关注：
   - 调用频率高的方法
   - 平均响应时间长的方法
   - 出现失败率的方法
4. 将结果及相关代码发给LLM输出优化建议

## 补充资源

- [Arthas 官方文档](https://arthas.aliyun.com/doc/)
- [Arthas 在线教程](https://arthas.aliyun.com/doc/arthas-tutorials.html)