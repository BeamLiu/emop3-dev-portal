# EMOP MCP 服务概述

本文档提供EMOP开发过程中所有MCP服务的概览，按功能分类展示各服务的核心信息。

## MCP 服务总览

| MCP类型 | 用途 | MCP集成配置JSON | 启动方式 | 核心工具 |
|---------|------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------|----------------------------------------------|
| Docs MCP Server | 文档智能查询和检索 | `{"mcpServers": {"docs-mcp-server": {"type": "sse", "url": "http://localhost:6280/sse"}}}` | `npx @arabold/docs-mcp-server@latest mcp --port 6280` | `search_docs`, `get_document`, `index_docs` |
| Files MCP Server | 源代码文件访问 | `{"mcpServers": {"files-mcp-server": {"command": "npx", "args": ["@modelcontextprotocol/server-filesystem", "/home/beam/workspace/emop3/docs", "/home/beam/workspace/emop3/server/platform/platform-api", "/home/beam/workspace/emop3/applications/server-plugins/server-plugin-api"]}}}` | STDIO协议直接启动 | `read_file`, `write_file`, `list_directory`, `create_directory`, `move_file`, `search_files` |
| Metadata MCP Server | 元数据管理和类型定义 | `{"mcpServers": {"emop-metadata-mcp": {"command": "java", "args": ["-jar", "/home/beam/workspace/emop3/emop-ai/metadata-mcp/target/metadata-mcp-0.0.1-SNAPSHOT.jar"]}}}` | STDIO协议直接启动 | `get_metadata_types`, `get_type_definition`, `create_type`, `update_type`, `validate_dsl` |
| Data Management MCP Server | 基于元数据的数据增删改查 | `{"mcpServers": {"emop-data-mgmt-mcp": {"command": "java", "args": ["-jar", "/home/beam/workspace/emop3/emop-ai/data-mgmt-mcp/target/data-mgmt-mcp-0.0.1-SNAPSHOT.jar"]}}}` | STDIO协议直接启动 | `query_objects_page`, `query_objects`, `get_object_by_id`, `get_objects_batch`, `get_object_property`, `create_object`, `update_object`, `delete_object`, `convert_draft`, `resolve_logical_objects`, `resolve_logical_objects_page`, `count_logical_objects` |
| Business Code Management MCP Server | 业务编码规则管理和生成 | `{"mcpServers": {"emop-biz-code-mgmt-mcp": {"command": "java", "args": ["-jar", "/home/beam/workspace/emop3/emop-ai/biz-code-mgmt-mcp/target/biz-code-mgmt-mcp-0.0.1-SNAPSHOT.jar"]}}}` | STDIO协议直接启动 | `generate_code`, `validate_pattern`, `get_code_rules`, `create_code_rule`, `update_code_rule`, `test_pattern`, `get_sequence_status` |
| Lifecycle Management MCP Server | 对象生命周期状态管理 | `{"mcpServers": {"emop-lifecycle-mgmt-mcp": {"command": "java", "args": ["-jar", "/home/beam/workspace/emop3/emop-ai/lifecycle-mgmt-mcp/target/lifecycle-mgmt-mcp-0.0.1-SNAPSHOT.jar"]}}}` | STDIO协议直接启动 | `get_lifecycle_states`, `apply_state_change`, `check_state_transition`, `checkout_object`, `checkin_object`, `create_revision`, `get_revision_history` |
| Graph Query MCP Server | 图数据库查询和关系分析 | `{"mcpServers": {"emop-graph-query-mcp": {"command": "java", "args": ["-jar", "/home/beam/workspace/emop3/emop-ai/graph-query-mcp/target/graph-query-mcp-0.0.1-SNAPSHOT.jar"]}}}` | STDIO协议直接启动 | `execute_cypher`, `search_nodes`, `get_neighbors`, `validate_cypher`, `find_shortest_path`, `analyze_impact`, `query_bom_structure` |
| Fulltext Search MCP Server | 全文检索和智能搜索 | `{"mcpServers": {"emop-fulltext-search-mcp": {"command": "java", "args": ["-jar", "/home/beam/workspace/emop3/emop-ai/fulltext-search-mcp/target/fulltext-search-mcp-0.0.1-SNAPSHOT.jar"]}}}` | STDIO协议直接启动 | `search`, `get_suggestions`, `sync_index`, `get_search_count`, `rebuild_index`, `get_rls_status`, `debug_search` |
| Workflow MCP Server | 工作流程管理和任务处理 | `{"mcpServers": {"emop-workflow-mcp": {"command": "java", "args": ["-jar", "/home/beam/workspace/emop3/emop-ai/workflow-mcp/target/workflow-mcp-0.0.1-SNAPSHOT.jar"]}}}` | STDIO协议直接启动 | `start_process`, `get_process_instances`, `complete_task`, `get_user_tasks`, `deploy_process`, `get_process_definitions`, `cancel_process`, `get_task_history` |
| User Management MCP Server | 用户和权限管理 | `{"mcpServers": {"emop-user-mgmt-mcp": {"command": "java", "args": ["-jar", "/home/beam/workspace/emop3/emop-ai/user-mgmt-mcp/target/user-mgmt-mcp-0.0.1-SNAPSHOT.jar"]}}}` | STDIO协议直接启动 | `get_users`, `create_user`, `update_user`, `delete_user`, `get_user_roles`, `assign_role`, `revoke_role`, `get_permissions` |
| Requirement Management MCP Server | 需求管理和追踪 | `{"mcpServers": {"emop-requirement-mgmt-mcp": {"command": "java", "args": ["-jar", "/home/beam/workspace/emop3/emop-ai/requirement-mgmt-mcp/target/requirement-mgmt-mcp-0.0.1-SNAPSHOT.jar"]}}}` | STDIO协议直接启动 | `create_requirement`, `update_requirement`, `get_requirements`, `trace_requirements`, `validate_requirement`, `link_requirements`, `get_requirement_tree` |
| Document Management MCP Server | 文档管理和版本控制 | `{"mcpServers": {"emop-doc-mgmt-mcp": {"command": "java", "args": ["-jar", "/home/beam/workspace/emop3/emop-ai/doc-mgmt-mcp/target/doc-mgmt-mcp-0.0.1-SNAPSHOT.jar"]}}}` | STDIO协议直接启动 | `upload_document`, `download_document`, `get_document_info`, `create_folder`, `move_document`, `get_document_versions`, `checkout_document`, `checkin_document` |
| BOM Management MCP Server | 物料清单管理 | `{"mcpServers": {"emop-bom-mgmt-mcp": {"command": "java", "args": ["-jar", "/home/beam/workspace/emop3/emop-ai/bom-mgmt-mcp/target/bom-mgmt-mcp-0.0.1-SNAPSHOT.jar"]}}}` | STDIO协议直接启动 | `create_bom`, `update_bom`, `get_bom_structure`, `add_bom_item`, `remove_bom_item`, `compare_boms`, `export_bom`, `validate_bom` |
| Project Management MCP Server | 项目管理和协作 | `{"mcpServers": {"emop-project-mgmt-mcp": {"command": "java", "args": ["-jar", "/home/beam/workspace/emop3/emop-ai/project-mgmt-mcp/target/project-mgmt-mcp-0.0.1-SNAPSHOT.jar"]}}}` | STDIO协议直接启动 | `create_project`, `update_project`, `get_projects`, `add_project_member`, `remove_project_member`, `get_project_tasks`, `create_milestone`, `track_progress` |
| Change Management MCP Server | 变更管理和审批 | `{"mcpServers": {"emop-change-mgmt-mcp": {"command": "java", "args": ["-jar", "/home/beam/workspace/emop3/emop-ai/change-mgmt-mcp/target/change-mgmt-mcp-0.0.1-SNAPSHOT.jar"]}}}` | STDIO协议直接启动 | `create_change_request`, `update_change_request`, `approve_change`, `reject_change`, `get_change_history`, `assess_impact`, `implement_change` |

:::warning ⚠️本地路径
注意修改对应的文件夹和文件的实际路径
:::
## 服务分类说明

### 基础辅助类 MCP
这类MCP服务提供基础的开发辅助功能，主要包括：
- **文档服务**：智能文档查询和检索
- **文件系统服务**：源代码文件访问和管理

详细信息请参考：[基础辅助类 MCP](./basic-mcp.md)

### EMOP平台操作类 MCP
这类MCP服务提供EMOP平台核心功能的操作接口，主要包括：
- **元数据管理**：类型定义、验证和元数据操作
- **数据管理**：基于元数据的数据增删改查
- **业务编码管理**：编码规则定义和自动生成
- **生命周期管理**：对象状态管理和版本控制
- **图查询**：图数据库查询和关系分析
- **全文检索**：智能搜索和索引管理
- **工作流管理**：流程定义和任务处理
- **用户管理**：用户账户和权限管理

详细信息请参考：[EMOP平台操作类 MCP](./platform-mcp.md)

### 业务领域类 MCP
这类MCP服务提供具体业务领域的操作功能，主要包括：
- **需求管理**：需求定义、追踪和验证
- **文档管理**：技术文档的版本控制和协作
- **BOM管理**：物料清单的创建、维护和分析
- **项目管理**：项目规划、执行和监控
- **变更管理**：工程变更的申请、审批和实施

详细信息请参考：[业务领域类 MCP](./business-mcp.md)
