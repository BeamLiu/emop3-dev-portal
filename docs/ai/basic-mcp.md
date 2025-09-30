# 基础辅助类 MCP

基础辅助类MCP服务为EMOP开发提供基础的辅助功能，主要包括文档智能查询和源代码文件访问等核心能力。

## Docs MCP Server
基于 [docs-mcp-server](https://github.com/arabold/docs-mcp-server) 的文档服务MCP，为EMOP项目文档提供智能查询和检索功能。

**功能特性：**
- 智能文档内容查询和检索
- 语义分块处理，保持文档结构完整性
- 支持本地Markdown文档索引
- 向量相似度搜索结合全文检索
- 版本感知的文档管理

**快速启动：**

**1. API密钥配置**
   
   在项目根目录创建 `.env` 文件：
   ```bash
   DASHSCOPE_API_KEY=your_api_key_here
   OPENAI_API_KEY=your_api_key_here
   OPENAI_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
   ```
   
   **获取 API 密钥**：访问 [Dashscope 控制台](https://dashscope.console.aliyun.com/) 创建 API 密钥
   
**2. 使用 npx 启动**

```bash
# 1. 启动 MCP 服务器
npx @arabold/docs-mcp-server@latest mcp --port 6280

# 2. 在新终端中索引本地文档
npx @arabold/docs-mcp-server@latest scrape emop-docs file://$(pwd)/docs
```

**参数说明：**
- `mcp --port 6280`：启动MCP服务器在6280端口
- `scrape emop-docs`：创建名为emop-docs的文档库
- `file://$(pwd)/docs`：索引当前目录下的docs文件夹

### 配置说明

1. **基础配置**
   - 文档目录：项目根目录下的 `./docs` 文件夹
   - 支持热重载：文档更新后重启服务即可重新索引
   - 默认端口：6280

2. **使用 Dashscope Qwen 模型**
   
   docs-mcp-server 支持 OpenAI 兼容的 API，可以配置使用 Dashscope 的 Qwen 模型进行文档嵌入和搜索。

### 索引EMOP文档

**索引本地文档：**
```bash
# 索引docs目录下的所有文档
npx @arabold/docs-mcp-server@latest scrape emop-docs file://$(pwd)/docs

# 支持的文件格式：.md, .mdx, .txt
# 索引完成后可通过MCP协议查询文档内容
```

**重新索引：**

::: warning ⚠️编译后的文件不要加入索引
先清理 `docs/.vitepress` 目录下的内容，然后再进行索引
:::

```bash
# 删除现有文档库并重新索引
npx @arabold/docs-mcp-server@latest remove emop-docs
npx @arabold/docs-mcp-server@latest scrape emop-docs file://$(pwd)/docs
```

**文档分块配置：**
- 对于结构良好的Markdown文档，docs-mcp-server会自动按照标题层级进行智能分块
- 无需特殊配置，系统会保持文档的原有段落结构

**测试方法：**

**使用 mcp-inspector 测试：**

```bash
# 1. 启动 docs-mcp-server
npx @arabold/docs-mcp-server@latest mcp --port 6280

# 2. 索引文档（在新终端中）
npx @arabold/docs-mcp-server@latest scrape emop-docs file://$(pwd)/docs

# 3. 启动 mcp-inspector（在新终端中）
npx @modelcontextprotocol/inspector

# 4. 在浏览器中访问显示的URL，添加MCP服务器：
# Transport Tpe: Streamable HTTP
# Server URL: http://localhost:6280/mcp
# 测试可用的工具和资源
```

**验证功能：**
- 文档搜索：搜索EMOP相关文档内容
- 语义查询：基于语义理解的文档检索
- 内容提取：获取特定文档的详细内容

**AI工具集成配置：**
```json
{
  "mcpServers": {
    "docs-mcp-server": {
      "type": "sse",
      "url": "http://localhost:6280/sse",
      "disabled": false,
      "autoApprove": []
    }
  }
}
```

**Web管理界面：**

启动服务后访问 `http://localhost:6280` 可以：
- 查看已索引的文档列表
- 测试文档搜索功能
- 查看服务状态和统计信息
- 管理文档索引

## Files MCP Server
基于 [filesystem MCP server](https://github.com/modelcontextprotocol/servers/tree/main/src/filesystem) 的文件系统服务MCP，为EMOP项目源代码提供文件访问功能。

**功能特性：**
- 允许LLM访问指定目录下的源代码文件
- 支持文件读取、写入和目录浏览
- 提供安全的文件系统访问控制
- 支持多种文件格式的内容读取

**快速启动：**

**1. 安装和启动**

```bash
# Files MCP Server 使用 STDIO 协议，无需单独启动服务器
# 直接通过 AI 工具的 MCP 配置启动
npx @modelcontextprotocol/server-filesystem \
  --allowed-directory /mnt/d/workspace/emop/emop3/docs \
  --allowed-directory /mnt/d/workspace/emop/emop3/emop-platform/emop-platform-api \
  --allowed-directory /mnt/d/workspace/emop/emop3/applications/server-plugins/server-plugin-api
```

**参数说明：**
- `--allowed-directory`：指定允许访问的目录路径
- 使用 STDIO 协议，无需指定端口
- 可以指定多个 `--allowed-directory` 参数来允许访问多个目录

**配置的访问目录：**
1. **文档目录**：`/mnt/d/workspace/emop/emop3/docs` - 项目文档
2. **平台API**：`/mnt/d/workspace/emop/emop3/server/platform-api` - 平台API源代码
3. **插件API**：`/mnt/d/workspace/emop/emop3/applications/server-plugins/server-plugin-api` - 服务器插件API源代码

**测试方法：**

**使用 mcp-inspector 测试：**

```bash
# 启动 mcp-inspector 并测试 STDIO 协议
npx @modelcontextprotocol/inspector

# 在浏览器中访问显示的URL，添加MCP服务器：
# Transport Type: STDIO
# Command: npx
# Args: @modelcontextprotocol/server-filesystem --allowed-directory /mnt/d/workspace/emop/emop3/docs --allowed-directory /mnt/d/workspace/emop/emop3/emop-platform/emop-platform-api --allowed-directory /mnt/d/workspace/emop/emop3/applications/server-plugins/server-plugin-api
```

**验证功能：**
- 文件读取：读取指定目录下的源代码文件
- 目录浏览：查看目录结构和文件列表
- 文件写入：在允许的目录下创建或修改文件
- 权限控制：验证只能访问指定的允许目录

**AI工具集成配置：**
```json
{
  "mcpServers": {
    "files-mcp-server": {
      "command": "npx",
      "args": [
        "@modelcontextprotocol/server-filesystem",
        "--allowed-directory", "/mnt/d/workspace/emop/emop3/docs",
        "--allowed-directory", "/mnt/d/workspace/emop/emop3/emop-platform/emop-platform-api",
        "--allowed-directory", "/mnt/d/workspace/emop/emop3/applications/server-plugins/server-plugin-api"
      ],
      "disabled": false,
      "autoApprove": []
    }
  }
}
```
