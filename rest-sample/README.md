# REST API 测试样例

本目录包含针对 DataManagementController 和 DSLExecutionController 的 REST API 测试样例。

## 文件说明

### 1. data-query-api.http
完整的数据查询 REST API 测试样例，包含：

#### 功能覆盖
- **DSL 创建类型**：使用 DSLExecutionController 创建继承自 ItemRevision 的自定义类型
- **数据创建**：使用 DataManagementController 创建测试数据
- **查询操作**：全面的查询功能测试，特别关注 IN 操作

#### 测试内容

**1. 类型定义（步骤1）**
- 创建 ProductItem 类型，继承自 ItemRevision
- 包含各种属性类型：
  - String: productCode
  - Integer: stockQuantity
  - Double: unitPrice
  - Boolean: isActive
  - Date: manufactureDate
  - Timestamp: lastUpdateTime
  - Object: specifications（复杂对象）
  - Object: categoryInfo（分类信息）

**2. 数据创建（步骤2）**
- 创建5个测试产品
- 包含不同类别：电子产品、家具、服装
- 包含复杂对象属性

**3. 基础查询（步骤3）**
- 查询所有数据（分页/不分页）

**4. EQUALS 查询（步骤4）**
- 字符串字段查询
- 布尔字段查询

**5. IN 操作查询（步骤5 - 重点）**
- 字符串字段 IN 查询
- 整数字段 IN 查询
- 自定义字段 IN 查询
- NOT_IN 查询

**6. 比较操作（步骤6）**
- GREATER_THAN
- LESS_THAN_EQUALS
- BETWEEN

**7. LIKE 模糊查询（步骤7）**

**8. 复合条件查询（步骤8）**
- AND 复合查询
- OR 复合查询
- 嵌套条件查询（AND 中包含 OR）

**9. IN 操作组合（步骤9）**
- IN + AND 组合
- IN + 价格范围组合

**10. 日期和时间戳查询（步骤10）**
- 日期字段查询
- 时间戳字段 BETWEEN 查询

**11. NULL 值查询（步骤11）**
- IS_NULL
- IS_NOT_NULL

**12. 附加属性查询（步骤12）**
- 使用 additionalProps 返回计算属性

**13. GET 方式查询（步骤13）**
- GET 不分页查询
- GET 分页查询

**14. 批量操作（步骤14）**
- 批量更新
- 使用 IN 查询验证更新结果

**15. 边界情况测试（步骤15）**
- 空 IN 列表
- 单个值 IN 查询
- 大量值 IN 查询

**16. 复杂 IN 查询场景（步骤16）**
- 多个 IN 条件 AND 组合
- IN 与 NOT_IN 组合
- IN 与 LIKE 组合

**17. 清理测试数据（步骤17）**
- 批量删除
- 验证删除结果

### 2. bom-api.http
BOM（物料清单）API 测试样例，包含：
- BOM 行的创建、更新、删除
- 批量操作
- 父子关系查询

## 使用方法

### 在 IntelliJ IDEA / WebStorm 中使用

1. 打开 `.http` 文件
2. 确保服务器正在运行（默认 http://localhost:870）
3. 按顺序点击每个请求旁边的 ▶️ 按钮执行
4. 查看响应结果和测试断言

### 修改配置

在文件顶部修改以下变量：
```
@baseUrl = http://localhost:870/webconsole
@userHeader = {"userId":-1,"authorities":["SYSTEM"]}
```

## IN 操作修正说明

本测试样例特别关注 IN 操作的正确性，包括：

1. **正确的 SQL 生成**
   - IN 操作应生成 `field = ANY(?)` 格式
   - 参数列表应正确展开

2. **类型支持**
   - 字符串类型 IN 查询
   - 整数类型 IN 查询
   - 其他数据类型 IN 查询

3. **组合查询**
   - IN 与 AND 组合
   - IN 与 OR 组合
   - 多个 IN 条件组合
   - IN 与其他操作符组合

4. **边界情况**
   - 空列表处理
   - 单个值处理
   - 大量值处理
