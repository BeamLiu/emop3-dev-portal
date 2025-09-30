# 业务领域类 MCP

业务领域类MCP服务专门为EMOP PLM系统的核心业务功能提供API访问能力，涵盖需求管理、文档管理、BOM管理、项目管理和变更管理等关键业务领域。这些服务基于PLM行业最佳实践，为产品全生命周期管理提供专业的业务操作接口。

## 服务详细说明

### Requirement Management MCP Server
**需求管理和追踪服务**

提供完整的需求管理功能，支持需求的创建、更新、追踪和验证。

**核心功能：**
- 需求创建和编辑
- 需求层次结构管理
- 需求追踪和关联
- 需求验证和确认
- 需求变更历史记录

**主要工具：**
- `create_requirement`: 创建新需求
- `update_requirement`: 更新需求信息
- `get_requirements`: 查询需求列表
- `trace_requirements`: 需求追踪分析
- `validate_requirement`: 需求验证
- `link_requirements`: 建立需求关联
- `get_requirement_tree`: 获取需求树结构

**配置示例：**
```json
{
  "emop-requirement-mgmt-mcp": {
    "command": "java",
    "args": ["-jar", "/mnt/d/workspace/emop/emop3/emop-ai/requirement-mgmt-mcp/target/requirement-mgmt-mcp-0.0.1-SNAPSHOT.jar"],
    "disabled": false,
    "autoApprove": []
  }
}
```

### Document Management MCP Server
**文档管理和版本控制服务**

提供技术文档的全生命周期管理，包括上传、下载、版本控制和协作功能。

**核心功能：**
- 文档上传和下载
- 文档版本控制
- 文档检入检出
- 文档夹管理
- 文档移动和重命名

**主要工具：**
- `upload_document`: 上传文档
- `download_document`: 下载文档
- `get_document_info`: 获取文档信息
- `create_folder`: 创建文档夹
- `move_document`: 移动文档
- `get_document_versions`: 获取文档版本历史
- `checkout_document`: 检出文档
- `checkin_document`: 检入文档

**配置示例：**
```json
{
  "emop-doc-mgmt-mcp": {
    "command": "java",
    "args": ["-jar", "/mnt/d/workspace/emop/emop3/emop-ai/doc-mgmt-mcp/target/doc-mgmt-mcp-0.0.1-SNAPSHOT.jar"],
    "disabled": false,
    "autoApprove": []
  }
}
```

### BOM Management MCP Server
**物料清单管理服务**

提供完整的BOM（Bill of Materials）管理功能，支持多层级BOM结构的创建、维护和分析。

**核心功能：**
- BOM结构创建和编辑
- BOM项目添加和删除
- BOM比较和分析
- BOM导出和报告
- BOM有效性验证

**主要工具：**
- `create_bom`: 创建BOM
- `update_bom`: 更新BOM信息
- `get_bom_structure`: 获取BOM结构
- `add_bom_item`: 添加BOM项目
- `remove_bom_item`: 删除BOM项目
- `compare_boms`: BOM比较分析
- `export_bom`: 导出BOM数据
- `validate_bom`: BOM有效性验证

**配置示例：**
```json
{
  "emop-bom-mgmt-mcp": {
    "command": "java",
    "args": ["-jar", "/mnt/d/workspace/emop/emop3/emop-ai/bom-mgmt-mcp/target/bom-mgmt-mcp-0.0.1-SNAPSHOT.jar"],
    "disabled": false,
    "autoApprove": []
  }
}
```

### Project Management MCP Server
**项目管理和协作服务**

提供项目全生命周期管理功能，支持项目规划、执行、监控和团队协作。

**核心功能：**
- 项目创建和配置
- 项目成员管理
- 任务分配和跟踪
- 里程碑管理
- 项目进度监控

**主要工具：**
- `create_project`: 创建项目
- `update_project`: 更新项目信息
- `get_projects`: 获取项目列表
- `add_project_member`: 添加项目成员
- `remove_project_member`: 移除项目成员
- `get_project_tasks`: 获取项目任务
- `create_milestone`: 创建里程碑
- `track_progress`: 跟踪项目进度

**配置示例：**
```json
{
  "emop-project-mgmt-mcp": {
    "command": "java",
    "args": ["-jar", "/mnt/d/workspace/emop/emop3/emop-ai/project-mgmt-mcp/target/project-mgmt-mcp-0.0.1-SNAPSHOT.jar"],
    "disabled": false,
    "autoApprove": []
  }
}
```

### Change Management MCP Server
**变更管理和审批服务**

提供工程变更管理功能，支持变更申请、审批流程、影响分析和变更实施。

**核心功能：**
- 变更请求创建和管理
- 变更审批流程
- 变更影响分析
- 变更历史追踪
- 变更实施管理

**主要工具：**
- `create_change_request`: 创建变更请求
- `update_change_request`: 更新变更请求
- `approve_change`: 审批变更
- `reject_change`: 拒绝变更
- `get_change_history`: 获取变更历史
- `assess_impact`: 评估变更影响
- `implement_change`: 实施变更

**配置示例：**
```json
{
  "emop-change-mgmt-mcp": {
    "command": "java",
    "args": ["-jar", "/mnt/d/workspace/emop/emop3/emop-ai/change-mgmt-mcp/target/change-mgmt-mcp-0.0.1-SNAPSHOT.jar"],
    "disabled": false,
    "autoApprove": []
  }
}
```
