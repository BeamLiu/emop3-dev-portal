# EMOP平台开发规范

## 0. 基础规范要求

### 0.1 通用Java开发规范

在遵循EMOP平台特有规范之前，开发者必须首先遵循以下基础规范：

**📋 必须遵循的基础规范**：
- **阿里巴巴Java开发手册**：包括命名规范、代码格式、异常处理等基础要求
- **IntelliJ IDEA代码检查**：开启并修复IDE提示的重要警告和错误
- **CheckStyle**：默认 IntelliJ 的前后端style
- **IDE建议**：推荐 IntelliJ 和 Webstorm

**⚠️ 注意事项**：
- 本规范文档专注于EMOP平台特有的开发规范
- 不重复说明通用Java规范，请务必同时遵循基础规范

## 1. 版本控制规范

### 1.1 代码提交要求

**🚨 强制要求**：提交代码和创建Pull Request时**必须做diff检查**

**📝 检查要点**：
- ✅ 确保变更内容清晰可见，避免意外提交不相关文件
- ✅ 检查是否有意外的格式化变更（如整个文件的空格变化）
- ✅ 确认删除的代码是有意为之，而非误操作
- ✅ 验证新增代码符合预期功能需求

**💬 提交信息规范**：
- 提交信息应清晰描述变更内容和原因
- 使用规范的commit message格式
- 重要变更应在PR描述中详细说明影响范围

## 2. REST API设计规范

### 2.1 避免冗余CRUD API

**❌ 禁止创建标准CRUD型REST API**
**❌ 禁止创建标准CRUD型Service API**
```java
// 错误示例 - 不要创建这些API
@GetMapping("/{id}")
public ResponseEntity<DelegationRule> getDelegationRule(@PathVariable Long id) { ... }

@PostMapping
public ResponseEntity<Object> createDelegationRule(@RequestBody CreateRequest request) { ... }

@PutMapping("/{id}")
public ResponseEntity<Object> updateDelegationRule(@PathVariable Long id, @RequestBody UpdateRequest request) { ... }

@DeleteMapping("/{id}")
public ResponseEntity<Object> deleteDelegationRule(@PathVariable Long id) { ... }
```

**✅ 使用平台统一的根据元数据操作的API**
好处非常多:
- 减少模板代码
- [平台API](../data/rest-api)减少了应用层App处理，提升性能
- 平台API显示地处理权限(对象级和字段级)
- 平台API进行了大量的性能优化
- 平台API实现了HATEOAS规范，可以顺着关系进行数据展开，进一步减少代码量
- 平台API配合[xpath](../data/xpath)功能，一次请求可以获取更多的数据，进一步减少代码量和提升性能，xpath在前后端代码中都可以直接使用，例如
```javascript
const productionItems = await Q.from<TopContainer>('TopContainer')
        .field('name').contains('Test')
        .withAdditionalProps({
            'bomLineCount': 'count(bomLines)',   //TopContainer.bomlines下面的数量
            'specification': 'specification.name' //TopContainer.specification对象的名称属性
        })
        .asc('priority')
        .desc('_lastModifiedDate')
        .page(0, 100)
        .query();
```
- 后续的bug及功能增强统一由平台处理
```java
// 正确做法 - 使用平台提供的统一API
// 1. 查询：使用前端Query API (Q.xxx) , 详细用法见 src/query/sample.ts
// 2. 创建：使用 DataManagementController.createData
// 3. 更新：使用 DataManagementController.updateData  
// 4. 删除：使用 DataManagementController.deleteData
```

### 2.2 避免特定查询API

**❌ 避免为特定查询创建专门的REST API**
```java
// 错误示例
@GetMapping("/{id}/tasks")
public ResponseEntity<List<TaskInfo>> getRuleTasks(@PathVariable Long id) { ... }

@GetMapping("/my-delegation-rules")
public ResponseEntity<List<DelegationRule>> getMyRules() { ... }
```

**✅ 使用前端Query API**
- 前端可以获取当前登录用户的id、uid等信息
- 平台提供防篡改机制保障前后端通信安全
- 前端使用统一的`Q`查询接口，避免API膨胀

### 2.3 自定义DTO类限制

**❌ 避免创建专门的用于CRUD的DTO类**
```java
// 错误示例
public class CreateDelegationRuleRequest {
    private String ruleName;
    private String fromAssignee;
    // ... 其他字段
}
```

**✅ 使用元数据驱动**
- 按照元数据定义直接创建对象
- 利用平台的自动类型转换能力
- 减少样板代码，提高开发效率
- `ModelObject`可以非常安全地作为DTO进行数据传递，减少各种Bean之间的属性拷贝

## 3. HTTP响应规范

### 3.1 响应状态码优先

**❌ 避免在响应体中包含状态信息**
```java
// 错误示例
return ResponseEntity.ok(Map.of(
        "success", true,
        "message", "操作成功",
        "data", result
        ));
```

**✅ 直接使用HTTP状态码**
```java
// 正确示例
return ResponseEntity.ok(result);  // 成功
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "参数错误");  // 失败
```

## 4. 异常处理规范

### 4.1 依赖平台异常处理

**❌ 避免过度的异常捕获**
```java
// 错误示例 - 不必要的异常包装
try {
    return userTaskService.completeTask(taskId, formVariables);
} catch (Exception e) {
    log.error("完成任务失败", e);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Map.of("success", false, "message", e.getMessage()));
}
```

**✅ 依赖平台异常处理**
```java
// 正确示例 - 让平台处理异常
return userTaskService.completeTask(taskId, formVariables);

// 或者只在需要添加有用信息时才捕获
try {
    return userTaskService.completeTask(taskId, formVariables);
} catch (Exception e) {
    throw new RuntimeException(String.format("完成任务失败，任务ID: %s, 用户: %s", taskId, getCurrentUser()), e);
}
```

**说明**：EMOP已有统一的异常处理Filter，代码会更精简优雅

## 5. 数据类型转换规范

### 5.1 使用平台自动转换

**❌ 避免手动类型转换**
```java
// 错误示例
public Timestamp parseDate(String dateStr) {
    try {
        Date date = DATE_FORMAT.parse(dateStr);
        return new Timestamp(date.getTime());
    } catch (ParseException e) {
        throw new IllegalArgumentException("日期格式错误: " + dateStr);
    }
}
```

**✅ 依赖平台使用元数据自动转换**
```java
// 正确示例1 - 平台自动转换（推荐）
modelObject.set("effectiveDate", dateStr);  // 平台自动根据元数据转换为Timestamp

// 正确示例2 - 必须手动转换时
Timestamp timestamp = Types.of(Timestamp.class).convert(dateStr);
```

### 5.2 使用平台对象更新机制

**❌ 避免手动字段映射**
```java
// 错误示例
private void applyUpdatesToRule(DelegationRule rule, Map<String, Object> updates) {
    updates.forEach((key, value) -> {
        switch (key) {
            case "ruleName":
            rule.setRuleName((String) value);
            break;
        case "status":
            rule.setStatus((String) value);
            break;
        // ... 更多字段
        }
    });
}
```

**✅ 使用平台批量更新**
```java
// 正确示例
rule.set(updates);  // 平台根据元数据自动处理ModelObject所有映射关系和类型转换
```

## 6. 代码质量规范

### 6.1 注释管理

**❌ 清理注释代码**
```java
// 错误示例 - 注释掉的代码应该删除
// List<UserTask> userTasks = Q.<UserTask>objectType(UserTask.class.getName())
//     .where("taskKey IN ANY(?)", taskKeys.toArray())
//     .query();
```

**✅ 删除无用注释**
- 删除注释掉的代码，通过Git历史查看变更
- 保留有意义的文档注释
- 避免代码库中的"死代码"

### 6.2 API文档规范

**❌ 缺少Swagger注解**
```java
// 错误示例 - 缺少文档注解
@PostMapping("/variables/{processInstanceKey}")
public ResponseEntity<Object> assigneesToProcessInstance(@PathVariable long processInstanceKey) { ... }
```

**✅ 完整的API文档**
```java
// 正确示例
@Operation(summary = "分配流程执行人", description = "为指定流程实例的节点分配执行人")
@ApiResponse(responseCode = "200", description = "分配成功")
@PostMapping("/variables/{processInstanceKey}")
public ResponseEntity<Object> assigneesToProcessInstance(
@Parameter(description = "流程实例键") @PathVariable long processInstanceKey) { ... }
```

## 7. 查询优化规范

### 7.1 使用IN ANY查询

**❌ 避免动态构建IN子句**
```java
// 错误示例 - 性能较差
StringBuilder whereClause = new StringBuilder();
whereClause.append("taskKey IN (");
for (int i = 0; i < taskKeys.size(); i++) {
    if (i > 0) whereClause.append(",");
    whereClause.append("?");
    params.add(taskKeys.get(i));
}
whereClause.append(")");
```

**✅ 使用ANY语法**
```java
// 正确示例 - 性能更好
Q.<UserTask>objectType(UserTask.class.getName())
    .where("taskKey = ANY(?) AND status = ANY(?)",
    taskKeys.toArray(),
    new String[]{"CREATED", "ASSIGNED"})
    .query();
```

**优势**：
- 避免SQL过长，减少解析时间
- 更好地利用PreparedStatement缓存
- 提升查询性能

## 8. 对象构建规范

### 8.1 封装对象创建逻辑

**❌ 在调用方进行复杂赋值**
```java
// 错误示例 - 在Controller中构建复杂对象
private DelegationTaskInfo buildTaskInfo(UserTask task, Long delegationRuleId) {
    DelegationTaskInfo taskInfo = new DelegationTaskInfo();
    taskInfo.setId(task.getTaskKey().toString());
    taskInfo.setName(task.getElementName() != null ? task.getElementName() : "未知任务");
    taskInfo.setProcessName(task.getProcessName() != null ? task.getProcessName() : "未知流程");
    // ... 更多赋值
    return taskInfo;
}
```

**✅ 将构建逻辑移到目标类**
```java
// 正确示例 - 在Entity/DTO类中提供构建方法
public class DelegationTaskInfo {
    public static DelegationTaskInfo fromUserTask(UserTask task, Long delegationRuleId) {
        DelegationTaskInfo taskInfo = new DelegationTaskInfo();
        taskInfo.setId(task.getTaskKey().toString());
        // ... 赋值逻辑
        return taskInfo;
    }
}

// 调用方简化为：
DelegationTaskInfo taskInfo = DelegationTaskInfo.fromUserTask(task, delegationRuleId);
```

## 9. 最佳实践总结

### 9.1 核心原则
1. **减少重复**：充分利用平台提供的统一API和服务
2. **简化代码**：依赖平台的自动化能力，减少样板代码
3. **统一标准**：遵循平台约定，保持API设计一致性
4. **性能优先**：使用平台优化的查询和数据处理方式

### 9.2 开发检查清单
- [ ] 是否创建了冗余的CRUD API？
- [ ] 是否使用了平台的Query API而非自定义查询接口？
- [ ] 是否避免了不必要的DTO类？
- [ ] 是否使用HTTP状态码而非响应体状态字段？
- [ ] 是否依赖平台异常处理而非过度捕获？
- [ ] 是否使用平台自动类型转换？
- [ ] 是否清理了注释代码？
- [ ] 是否添加了完整的Swagger文档？
- [ ] 是否使用了ANY语法优化查询？
- [ ] 是否将对象构建逻辑封装到合适的类中？
