// docs/.vitepress/config.js
import { defineConfig } from 'vitepress'
import { withMermaid } from "vitepress-plugin-mermaid"

export default withMermaid(
 defineConfig({
  base: '/docs/',
  ignoreDeadLinks: true,
  vite: {
    server: {
	  host: '0.0.0.0',
      port: 3000
    }
  },
  // 站点配置
  title: 'EMOP Docs',
  description: 'EMOP 技术文档中心',
  lang: 'zh-CN',
  // 主题配置
  themeConfig: {
	//搜索
	search: {
      provider: 'local'
    },
	outline: {
      level: [2, 3],
    },
    // 导航栏
    nav: [
      { text: '首页', link: '/' },
      { text: '开发指南', link: '/getting-started' },
      { text: '部署', link: '/deployment/requirements' },
	  { text: 'PLM', link: '/plm/functionality' }
    ],

    // 侧边栏
    sidebar: {
      '/': [
        {
          text: '介绍',
          items: [
            { text: '快速开始', link: '/getting-started' }
          ]
        },
        {
		  text: '业务开发',
		  collapsed: false,
		  items: [
			{ text: '开发环境', link: '/business/environment' },
			{ text: '开发指南', link: '/business/backend-development.html' },
			{
			  text: '业务建模指南',
			  collapsed: true,
			  items: [
				{ text: '基本概念', link: '/business/modeling/guide' },
				{ text: '如何定义业务模型', link: '/business/modeling/modeling' },
				{ text: '模型扩展方法', link: '/business/modeling/extension' },
				{ text: '业务分表存储优化', link: '/business/modeling/table-partitioning' },
				{ text: 'Trait(特征)使用手册', link: '/business/modeling/trait' },
				{ text: '业务对象状态管理', link: '/business/modeling/state-management' }
			  ]
			},
			{
              text: '数据操作指南',
              collapsed: true,
              items: [
                { text: '数据操作概述', link: '/business/data/overview' },
                { text: 'Java API 数据操作', link: '/business/data/java-api' },
                { text: 'DSL 数据操作', link: '/business/data/dsl' },
                { text: 'REST API 数据操作', link: '/business/data/rest-api' },
                { text: '前端数据操作', link: '/business/data/frontend' },
                { text: 'XPath数据操作', link: '/business/data/xpath' },
                { text: '全文检索(Fulltext Search)', link: '/business/data/fulltext-search' },
                { text: '图查询(Graph Query)', link: '/business/data/graph-query' }
              ]
            },
			{
              text: 'DSL使用指南',
              collapsed: true,
              items: [
                { text: 'DSL概述', link: '/business/dsl/guide' },
				{ text: 'DSL语法参考', link: '/business/dsl/grammar' }
              ]
            },
			{
              text: '业务能力抽象',
              collapsed: true,
              items: [
                { text: '权限定义', link: '/business/core/auth' },
				{ text: '流程定义', link: '/business/core/workflow' },
				{ text: '业务编码定义', link: '/business/core/codePattern' },
				{ text: '可扩展枚举和级联定义', link: '/business/core/enum' },
				{ text: '前端渲染定义', link: '/business/core/webRender' },
				{ text: '数据集成', link: '/business/core/datahub' },
              ]
            },
			{
              text: 'CAD集成开发',
              collapsed: true,
              items: [
				{ text: '开发概述', link: '/business/xcad/overview' },
				{ text: '服务器端开发', link: '/business/xcad/server' },
                { text: 'Creo客户端开发', link: '/business/xcad/creo' },
				{ text: 'Solidworks客户端开发', link: '/business/xcad/solidworks' },
				{ text: 'Catia客户端开发', link: '/business/xcad/catia' },
				{ text: 'NX客户端开发', link: '/business/xcad/catia' },
				{ text: 'Inventor客户端开发', link: '/business/xcad/inventor' },
				{ text: 'AutoCAD客户端开发', link: '/business/xcad/autocad' },
				{ text: '中望CAD客户端开发', link: '/business/xcad/zwcad' },
				{ text: 'Eplan客户端开发', link: '/business/xcad/eplan' },
				{ text: 'AltiumDesign客户端开发', link: '/business/xcad/ad' },
				{ text: 'Pads客户端开发', link: '/business/xcad/pads' },
              ]
            },
			  {
				  text: '前端开发',
				  collapsed: true,
				  items: [
					  { text: '前端渲染定义', link: '/business/frontend/render' },
				  	  { text: '3D模型预览嵌入', link: '/business/frontend/3d-viewer-embed' }
				  ]
			  },
			{
              text: '开发规范',
              collapsed: true,
              items: [
				  { text: 'AI辅助编程', link: '/business/specification/ai-coding' },
				  { text: '平台开发规范', link: '/business/specification/coding' },
                { text: 'EMOP配置规范', link: '/business/specification/settings' },
				  { text: 'Pipeline批量API', link: '/business/specification/pipeline' },
				  { text: '文件存储规范', link: '/business/specification/file-storage' },
				  {
					  text: '开发陷阱',
					  collapsed: true,
					  items: [
						  { text: '前端开发陷阱集锦', link: '/business/specification/frontend-pitfalls' },
						  { text: '后端开发陷阱集锦', link: '/business/specification/backend-pitfalls' },
					  ]
				  }
              ]
            },
			{ text: '开发者门户指南', link: '/business/developer-portal-guide' }
		  ]
		},
		{
		  text: '实战教程',
		  collapsed: true,
		  items: [
			{
			  text: '基础教程 - 工装夹具库管理',
			  collapsed: true,
			  items: [
				{ text: '教程概述', link: '/tutorial/fixture/overview' },
				{ text: '需求分析与设计', link: '/tutorial/fixture/requirements' },
			    { text: '基础功能构建',
				  collapsed: true,
				  items: [
				    { text: 'Java模型实现', link: '/tutorial/fixture/basic/model' },
				    { text: '数据操作开发', link: '/tutorial/fixture/basic/data' },
				    { text: '前端页面实现(TBD)', link: '/tutorial/fixture/basic/frontend' }
				  ]
			    },
			    { text: '功能迭代优化(TBD)',
				  collapsed: true, 
				  items: [
				    { text: 'DSL模型扩展', link: '/tutorial/fixture/iteration/dsl' },
				    { text: '界面交互优化', link: '/tutorial/fixture/iteration/ui' },
				    { text: '性能调优', link: '/tutorial/fixture/iteration/performance' }
				  ]
			    },
				{ text: '部署(TBD)', link: '/tutorial/fixture/deployment' }
			  ]
			}
		  ]
		},
		{
          text: '平台开发',
		  collapsed: true,
          items: [
			  { text: '元数据及存储设计', link: '/platform/metadata' },
			  { text: '多级缓存设计', link: '/platform/cache' },
			  { text: '认证服务集成', link: '/platform/keycloak' },
			  { text: '对象权限设计', link: '/platform/abac' },
			  { text: '全文检索设计', link: '/platform/fulltext-search' },
			  { text: '图数据检索设计', link: '/platform/graph-search' },
			  { text: 'RPC批量执行设计', link: '/platform/pipeline' },
			  { text: '异地文件服务设计', link: '/platform/distributed-file-service' },
			  { text: '平台组件及服务监控设计', link: '/platform/monitoring' },
          ]
        },
		  {
			  text: 'AI',
			  collapsed: true,
			  items: [
				  { text: '概述', link: '/ai/guide' },
				  { text: 'CAD辅助设计智能体', link: '/ai/cad-design-agent' },
				  { text: '实施智能体', link: '/ai/implementation-agent' },
				  {
					  text: 'MCP服务',
					  collapsed: true,
					  items: [
						  { text: '概述', link: '/ai/overview' },
						  { text: '基础辅助类 MCP', link: '/ai/basic-mcp' },
						  { text: 'EMOP平台操作类 MCP', link: '/ai/platform-mcp' },
						  { text: '业务领域类 MCP', link: '/ai/business-mcp' }
					  ]
				  }
			  ]
		  },
		{
		  text: '开发工具与调优',
		  collapsed: true,
		  items: [
			{ text: 'Arthas查找性能瓶颈', link: '/tools/arthas' },
		  ]
		},
		{
          text: '部署运维',
		  collapsed: true,
          items: [
			{ text: 'Docker镜像打包', link: '/deployment/docker' },
			  { text: 'PG数据库插件构建', link: '/deployment/pg-extension' },
          ]
        },
		{
		  text: 'PLM',
		  collapsed: true,
		  items: [
			{ text: '功能说明',
			  collapsed: true,
			  items: [
				  { text: '工作流实施', link: '/plm/workflow' },
				  { text: '转图服务', link: '/plm/cad-conversion' },
				  { text: '分类管理', link: '/plm/classification' },
				  { text: '权限配置', link: '/plm/permission' },
				  { text: 'BOM设计', link: '/plm/bom' }
			  ]
			},
			{ text: '演示视频', link: '/plm/video' }
		  ]
		}
      ]
    },

    // 页脚
    footer: {
      message: 'EMOP 3.0',
      copyright: 'Copyright © 2025-present EMOP'
    }
  }
})
)
