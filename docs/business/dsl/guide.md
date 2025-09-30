# DSL使用指南

## 概述

DSL（领域特定语言）是EMOP平台提供的一种强大的声明式语言，用于执行数据操作和模型定义。关于DSL的基本概念和作用，请参考 [DSL语法参考](/business/dsl/grammar)。

## DSL执行方式

### REST API方式

DSL可以通过REST API执行，主要端点如下：

```http
POST /webconsole/api/dsl/execute
Content-Type: application/json

"你的DSL语句"

```

示例：
```bash
curl -X POST "http://localhost:870/webconsole/api/dsl/execute" \
     -H "Content-Type: application/json" \
     -d 'create object Customer {
               name: \"John Doe\",
               email: \"john@example.com\",
               status: \"Active\"
           }'
```

### Java API方式

使用Java API执行DSL需要注入DSLExecutionService：

```java
// 获取DSL执行服务
DSLExecutionService dslService = S.service(DSLExecutionService.class);

// 执行DSL
String dsl = """
    create object Customer {
        name: "John Doe",
        email: "john@example.com",
        status: "Active"
    }
    """;
Object result = dslService.execute(dsl);
```

## 主要操作

EMOP提供的DSL主要操作包含以下内容：
- 元数据操作 
- 对象增删改查操作
- 关系增删改查操作
- 数据导入导出操作
- 命令(工作流，状态变更，脚本执行等)执行操作

ℹ️说明
 详细语法请查阅 [DSL语法参考](/business/dsl/grammar)

## 错误处理和调试

1. **执行前验证**
   - 使用DSLExecutionService的validate方法进行语法检查：
   ```java
   ValidationResult result = dslService.validate(dsl);
   if (!result.isValid()) {
       System.out.println("验证错误：" + result.getErrors());
   }
   ```

2. **常见错误与解决方案**
   - 语法错误：检查括号匹配、引号闭合
   - 对象不存在：确认对象ID或查询条件正确
   - 类型不匹配：检查属性值类型是否符合定义
   - 关系冲突：检查关系定义和约束条件

3. **调试建议**
   - 使用show命令查看对象状态
   - 分步执行复杂操作
   - 记录日志输出结果

## 最佳实践

1. **设计原则**
   - 保持DSL语句简洁明确
   - 一个DSL语句完成一个独立的业务操作
   - 避免过于复杂的条件判断

2. **性能优化**
   - 批量操作优于单条操作
   - 合理使用条件过滤
   - 避免不必要的深层关系查询

3. **安全考虑**
   - 验证输入数据
   - 控制操作权限
   - 记录重要操作日志

4. **维护性**
   - 添加适当的注释
   - 模块化复杂操作
   - 保持代码格式一致

## 注意事项

1. DSL操作不具备幂等性，重复执行更新类操作会得到不同结果
2. 删除操作会自动断开当前节点与外部对象的引用关系，但是当前节点被外部引用时无法删除
3. 支持树形数据的递归操作(增删改查)
