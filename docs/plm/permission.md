# 对象及属性权限管理配置

## 1. 概述

### 1.1 设计理念

EMOP平台的ABAC（Attribute-Based Access Control）权限控制系统采用**元数据驱动**的设计理念，核心特点：

- **面向业务对象配置**：实施人员直接配置业务对象类型权限，无需关心底层数据库表结构
- **统一权限视图**：所有权限动作（增删改查、下载、打印等）在同一配置文件中统一管理
- **自动技术分发**：平台自动将配置分发到数据库RLS层和应用层，实施人员无需了解技术细节
- **默认允许策略**：未配置的权限动作默认允许，避免因遗漏配置导致系统不可用

### 1.2 核心优势

1. **配置简单**：实施人员只需关心业务逻辑，无需学习数据库技术
2. **性能优化**：结合PostgreSQL RLS实现数据库层高性能过滤
3. **灵活扩展**：支持对象级和属性级双重权限控制
4. **企业集成**：与Keycloak身份认证系统无缝集成

## 2. 权限配置架构

### 2.1 配置层次结构

```
对象类型名称
├── 对象级权限（控制哪些记录可访问）
│   ├── READ（查看权限）
│   ├── UPDATE（修改权限）
│   ├── DELETE（删除权限）
│   ├── CREATE（创建权限）
│   └── 自定义权限（DOWNLOAD、PRINT、SHARE等）
└── 属性级权限（控制字段级访问）
    └──  属性名称（成本、价格、核心关键技术参数等）
         ├──  READ权限设置
         └──  UPDATE权限设置
```

### 2.2 权限执行引擎

| 引擎类型 | 执行位置 | 适用场景 | 性能特点                      |
|---------|---------|---------|---------------------------|
| **SQL引擎** | 数据库RLS层 | 数据查询过滤 | 高性能，自动生效，建议只在对象级READ权限上使用 |
| **Script引擎** | 应用层 | 业务逻辑判断 | 灵活性强，支持复杂逻辑，建议在非对象级READ权限上使用          |

::: warning ⚠️引擎选择限制
- READ权限：为了性能考虑，完全依赖RLS在数据库层过滤，Java应用层暂时不做额外检查，因此即使针对READ配置了`script`逻辑，也不会进行检查
- CREATE/UPDATE/DELETE权限：如果配置了`script`逻辑就Java应用层进行检查和验证，如果配置了 `sql`逻辑就在数据库层面进行检查
- 其他自定义权限：只有`script`逻辑生效，`sql`无法生效
:::
  ::: warning ⚠️性能考虑
  Script引擎的权限条件可以访问完整的对象属性和上下文信息，但这在某些场景下带来了性能考量：

**需要对象实例的Script条件**：
- 包含`object.xxx`访问的条件（如`object._creator == currentUserId`）
- 根据ID进行权限检查时，需要从缓存或数据库中获取完整对象数据
- 建议优先使用已有ModelObject实例的API，避免不必要的数据库查询

**性能优化的Script条件**：
- 仅依赖用户上下文的条件（如`user_is_admin()`, `user_has_role('role-uid')`）
- 平台会进行性能优化：检测表达式，把这类归纳为TYPE_ONLY脚本，然后条件执行时无需加载对象，
- 建议将TYPE_ONLY条件放在权限规则的前面，利用OR逻辑快速通过

**API选择建议**：
```java
Document doc = processedDoc;
// ✅ 推荐：使用ModelObject API，高性能
if (permissionService.checkPermission(doc, PermissionAction.UPDATE)) {
    // 业务处理
}

// ⚠️ 注意性能：ID API在需要对象属性时会触发查询
if (permissionService.checkPermission(docId, PermissionAction.UPDATE)) {
    Document doc = documentService.findById(docId); // 重复查询
    // 业务处理
}

// ✅ TYPE_ONLY权限无性能影响（管理员等角色权限）
if (permissionService.checkPermission(docId, PermissionAction.UPDATE)) {
    // 如果是TYPE_ONLY权限（如user_is_admin()），性能优异
}
```
:::
## 3. 配置文件结构

### 3.1 基本配置模板

```yaml
version: "1.0"
description: "权限配置描述"
createdAt: "2025-06-17"

# 权限配置主体
permissionConfig:
  objects:
    # 对象类型名（如：Document、Project等）
    对象类型名:
      description: "对象权限说明"
      
      # 对象级权限
      permissions:
        READ:
          conditions:
            - sql: "数据库层条件"
              description: "条件说明"
            - script: "应用层脚本条件"
              description: "条件说明"
          logic: "OR"  # 条件之间的逻辑关系
          
      # 属性级权限（可选）
      attributePermissions:
        字段名:
          description: "字段权限说明"
          permissions:
            READ:
              effect: "ALLOW"
              conditions:
                - script: "字段访问条件"
                  description: "条件说明"
```

### 3.2 权限动作类型

| 动作类型 | 说明 | 典型应用场景 |
|---------|------|-------------|
| **READ** | 查看权限 | 控制哪些记录可以查询 |
| **UPDATE** | 修改权限 | 控制哪些记录可以修改 |
| **DELETE** | 删除权限 | 控制哪些记录可以删除 |
| **CREATE** | 创建权限 | 控制是否可以创建新记录 |
| **DOWNLOAD** | 下载权限 | 控制文件下载 |
| **PRINT** | 打印权限 | 控制文档打印 |
| **SHARE** | 分享权限 | 控制对外分享 |
| **APPROVE** | 审批权限 | 控制工作流审批 |

可定义更多的动作类型
## 4. 内置权限函数

### 4.1 SQL引擎函数（数据库层）

| 函数名 | 说明 | 使用示例 |
|--------|------|----------|
| `auth.get_current_user_id()` | 获取当前用户ID | `_creator = auth.get_current_user_id()` |
| `auth.user_has_role(role_uid)` | 检查用户角色 | `auth.user_has_role('manager-role-uid')` |
| `auth.user_is_admin()` | 检查是否管理员 | `auth.user_is_admin()` |
| `auth.user_is_manager()` | 检查是否经理 | `auth.user_is_manager()` |
| `auth.user_in_department(dept_uid)` | 检查用户部门 | `auth.user_in_department('rd-dept-uid')` |
| `auth.check_creator_permission(creator_id)` | 检查是否创建者 | `auth.check_creator_permission(_creator)` |

### 4.2 Script引擎函数（应用层）

| 函数名 | 说明 | 使用示例 |
|--------|------|----------|
| `user_has_role(role_uid)` | 检查用户角色 | `user_has_role('manager-role-uid')` |
| `user_is_admin()` | 检查是否管理员 | `user_is_admin()` |
| `user_is_manager()` | 检查是否经理 | `user_is_manager()` |
| `user_in_department(dept_uid)` | 检查用户部门 | `user_in_department('rd-dept-uid')` |
| `check_creator_permission(creator_id)` | 检查是否创建者 | `check_creator_permission(object._creator)` |
| `check_object_state(state, allowed_states)` | 检查对象状态 | `check_object_state(object._state, ['Working'])` |

## 5. 实施配置样例

### 5.1 CAD图纸权限控制场景

**业务需求**：
- 研发部门：可查看、修改本部门创建的CAD图纸
- 工艺部门：只能查看已发布的CAD图纸
- 研发经理：可下载CAD图纸
- 财务人员：可查看成本字段

**配置实现**：

```yaml
permissionConfig:
  objects:
    CadDocument:
      description: "CAD图纸权限控制"
      permissions:
        READ:
          conditions:
            # 研发部门可查看所有CAD
            - sql: "auth.user_in_department('rd-dept-uid') AND file_type IN ('dwg', 'prt', 'asm')"
              description: "研发部门查看CAD图纸"
            # 工艺部门只能查看已发布CAD
            - sql: "auth.user_in_department('process-dept-uid') AND file_type IN ('dwg', 'prt', 'asm') AND _state = 'Released'"
              description: "工艺部门查看已发布CAD"
            # 创建者始终可查看
            - sql: "auth.check_creator_permission(_creator)"
              description: "创建者查看自己的CAD"
          logic: "OR"
          
        UPDATE:
          conditions:
            # 研发部门修改未发布的CAD
            - script: "user_in_department('rd-dept-uid') && !(object._state in ['Released', 'Frozen'])"
              description: "研发部门修改未发布CAD"
            # 研发经理可修改已发布CAD
            - script: "user_has_role('rd-manager-role-uid') && object._state == 'Released'"
              description: "研发经理修改已发布CAD"
          logic: "OR"
          
        DOWNLOAD:
          conditions:
            - script: "user_has_role('rd-manager-role-uid') || user_is_manager()"
              description: "经理级别可下载CAD"
              
      # 属性级权限
      attributePermissions:
        cost:
          description: "成本字段权限"
          permissions:
            READ:
              effect: "ALLOW"
              conditions:
                - script: "user_has_role('finance-role-uid') || user_is_admin()"
                  description: "只有财务人员可查看成本"
```

### 5.2 项目文档跨部门协作场景

**业务需求**：
- 项目负责人：完整权限
- 项目成员：查看和参与权限
- 非项目成员：无权限

**配置实现**：

```yaml
permissionConfig:
  objects:
    ProjectDocument:
      description: "项目文档跨部门协作权限"
      permissions:
        READ:
          conditions:
            # 项目负责人查看
            - sql: "project_leader = auth.get_current_user_id()"
              description: "项目负责人查看项目文档"
            # 项目成员查看（使用JSON数组包含检查）
            - sql: "project_members::jsonb ? auth.get_current_user_id()::text"
              description: "项目成员查看项目文档"
            # 项目经理查看所有项目
            - sql: "auth.user_has_role('project-manager-role-uid')"
              description: "项目经理查看所有项目"
          logic: "OR"
          
        UPDATE:
          conditions:
            # 项目负责人可修改
            - script: "object.project_leader == currentUserId"
              description: "项目负责人修改项目文档"
            # 项目经理可修改
            - script: "user_has_role('project-manager-role-uid')"
              description: "项目经理修改项目文档"
          logic: "OR"
```

### 5.3 敏感字段权限场景

**业务需求**：
- 成本字段：财务人员可见
- 技术参数：研发人员可见
- 审计字段：管理员可修改

**配置实现**：

```yaml
permissionConfig:
  objects:
    Product:
      attributePermissions:
        # 财务敏感字段
        cost:
          permissions:
            READ:
              effect: "ALLOW"
              conditions:
                - script: "user_has_role('finance-role-uid') || user_is_admin()"
            UPDATE:
              effect: "ALLOW"
              conditions:
                - script: "user_has_role('finance-role-uid')"
                
        # 技术机密字段
        tech_parameter:
          permissions:
            READ:
              effect: "ALLOW"
              conditions:
                - script: "user_has_role('rd-role-uid') || user_is_admin()"
            UPDATE:
              effect: "ALLOW"
              conditions:
                - script: "user_has_role('rd-role-uid')"
                
        # 审计字段
        _creator:
          permissions:
            READ:
              effect: "ALLOW"
              conditions:
                - script: "true"  # 所有人可查看
            UPDATE:
              effect: "ALLOW"
              conditions:
                - script: "user_is_admin()"  # 只有管理员可修改
```

## 6. 实施步骤

### 6.1 准备工作

1. **收集业务需求**
    - 明确各部门职责和数据访问需求
    - 识别敏感字段和保密等级
    - 梳理业务流程和状态变化

2. **获取Keycloak信息**
    - 收集角色UID列表
    - 收集部门UID列表
    - 确认用户组织架构

3. **了解对象模型**
    - 确认需要权限控制的对象类型
    - 了解对象的状态流转
    - 识别关键业务字段

### 6.2 配置实施

1. **创建配置文件**
    - 在classpath下创建YAML配置文件
    - 命名建议：`{项目}-permission-config.yml`

2. **配置对象权限**
    - 从最基础的READ权限开始
    - 逐步添加UPDATE、DELETE等权限
    - 使用SQL引擎处理简单条件

3. **配置属性权限**
    - 识别敏感字段
    - 配置字段级访问控制
    - 测试字段过滤效果

4. **注册配置文件**
    - 在`application.yml`中添加配置文件路径：
   ```yaml
   emop:
     permission:
       config-files:
         - "custom-permission-config.yml"
   ```

### 6.3 测试验证

1. **权限同步**
    - 调用权限同步接口更新用户权限数据
    - 全量同步：`/api/auth/permissions/sync/all`
    - 单用户同步：`/api/auth/permissions/sync/user/{userUid}`

2. **功能测试**
    - 使用不同角色用户登录测试
    - 验证查询结果是否正确过滤
    - 测试操作权限是否生效

3. **性能测试**
    - 检查查询性能是否受影响
    - 监控数据库RLS策略执行效率

## 7. 常见问题与解决方案

### 7.1 配置问题

**Q: 配置部署后不生效？**
A: 检查以下方面：
- 确认配置文件已注册到`application.yml`
- 检查YAML语法是否正确
- 验证Keycloak角色/部门UID是否正确
- 确认权限同步是否执行

**Q: RLS查询结果为空？**
A: 可能原因：
- 用户上下文未正确设置
- RLS条件过于严格
- 权限数据未同步
- 建议先测试简单条件，逐步增加复杂度

### 7.2 性能问题

**Q: 查询性能下降明显？**
A: 优化建议：
- 优先使用SQL引擎而非Script引擎
- 为权限相关字段添加数据库索引
- 简化RLS条件逻辑

**Q: 批量操作很慢？**
A: 建议使用：
- 批量权限检查API
- 权限过滤API筛选有权限的对象
- 避免在循环中单独检查权限

### 7.3 业务场景问题

**Q: 复杂的跨部门协作如何配置？**
A: 解决方案：
- 使用项目成员字段存储参与人员
- 利用JSON数组包含查询
- 结合角色和项目成员双重检查

**Q: 动态权限如何实现？**
A: 建议方案：
- 利用对象状态字段控制权限变化
- 使用Script引擎实现复杂业务逻辑
