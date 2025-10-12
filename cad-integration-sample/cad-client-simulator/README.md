# CAD Client Simulator - CAD客户端浏览器模拟器

使用 JBR CEF (JetBrains Runtime with Chromium Embedded Framework) 浏览器访问 CAD 集成前端项目。

## 功能特性

- ✅ 内嵌 Chromium 浏览器（基于 JBR CEF）
- ✅ 完整的浏览器功能（前进、后退、刷新、地址栏）
- ✅ 开发者工具支持（F12 或远程调试端口 9222）
- ✅ 快捷键支持（F5 刷新、F12 开发者工具）
- ✅ 默认访问本地前端项目：`http://localhost:4200/cad-integration2/`

## 快速开始

### 1. 下载 JBR

访问 [JetBrains Runtime 发布页面](https://github.com/JetBrains/JetBrainsRuntime/releases)

下载包含 `jcef` 的 JBR 17 版本，例如：
- Linux: `jbr_jcef-17.0.11-linux-x64-b1312.2.tar.gz`
- macOS: `jbr_jcef-17.0.11-osx-x64-b1312.2.tar.gz`
- Windows: `jbr_jcef-17.0.11-windows-x64-b1312.2.tar.gz`

解压到本地目录，例如：
```bash
mkdir -p ~/tools
tar -xzf jbr_jcef-17.0.11-linux-x64-b1312.2.tar.gz -C ~/tools
```

### 2. 配置 JBR 路径

```bash
cd dev-portal/cad-integration-sample/cad-client-simulator

# 复制配置文件
cp .env.example .env

# 编辑 .env 文件，设置你的 JBR 路径
# 例如: JBR_HOME=/home/user/tools/jbr_jcef-17.0.11-linux-x64-b1312.2
nano .env
```

### 3. 启动前端项目

```bash
cd temp/web
npm start
```

前端会运行在：`http://localhost:4200/cad-integration2/`

### 4. 编译和运行

```bash
cd dev-portal/cad-integration-sample/cad-client-simulator

# 编译（首次运行或代码修改后）
./compile.sh

# 运行
./run.sh
```

就这么简单！脚本会自动从 `.env` 读取 JBR 配置，不会影响系统的 JAVA_HOME。

## 使用说明

### 浏览器功能

- **地址栏**：输入 URL 后按回车访问
- **导航按钮**：
  - ⬅️ 后退
  - ➡️ 前进
  - 🔄 刷新（或按 F5）
  - 🏠 返回主页
  - 🔧 开发者工具（或按 F12）

### 快捷键

- `F5`: 刷新页面
- `F12`: 打开开发者工具

### 开发者工具

两种方式打开开发者工具：

1. **内嵌窗口**：点击工具栏的 🔧 按钮或按 F12
2. **远程调试**：在 Chrome 浏览器中访问 `http://localhost:9222`

## 修改默认 URL

编辑 `src/main/java/io/emop/example/cad/simulator/CadClientSimulator.java`：

```java
private static final String DEFAULT_URL = "http://localhost:4200/cad-integration2/";
```

## 常见问题

### Q1: 提示 "未找到 .env 配置文件"

**解决方案**：
```bash
cp .env.example .env
nano .env  # 编辑并设置 JBR_HOME
```

### Q2: 提示 "JBR 目录不存在"

**解决方案**：检查 `.env` 文件中的 `JBR_HOME` 路径是否正确。

### Q3: 编译失败

**解决方案**：
```bash
# 查看 .env 配置
cat .env

# 重新编译
./compile.sh
```

## 项目结构

```
cad-client-simulator/
├── src/main/java/
│   └── io/emop/example/cad/simulator/
│       └── CadClientSimulator.java    # 主程序
├── .env.example                        # 配置示例
├── .env                                # 你的配置（需创建）
├── compile.sh                          # 编译脚本
├── run.sh                              # 运行脚本
├── pom.xml                             # Maven 配置
└── README.md                           # 本文件
```

## 工作流程

```bash
# 首次使用
cp .env.example .env
nano .env              # 设置 JBR_HOME
./compile.sh           # 编译
./run.sh               # 运行

# 日常使用
./run.sh               # 直接运行（会自动检查是否需要编译）

# 代码修改后
./compile.sh           # 重新编译
./run.sh               # 运行
```

## 技术栈

- **Java**: 17
- **JBR CEF**: JetBrains Runtime with Chromium Embedded Framework
- **Swing**: Java GUI 框架
- **Maven**: 构建工具

## 参考资料

- [JetBrains Runtime 下载](https://github.com/JetBrains/JetBrainsRuntime/releases)
- [JCEF 文档](https://bitbucket.org/chromiumembedded/java-cef/wiki/Home)

## 注意事项

1. **不影响系统配置**：脚本只在运行时临时使用 `.env` 中的 JBR，不会修改系统的 JAVA_HOME
2. **必须使用 JBR**：普通 JDK 不包含 JCEF，无法运行本项目
3. **版本兼容性**：建议使用 JBR 17，与项目 Java 版本一致
4. **跨平台**：JBR CEF 支持 Windows、macOS 和 Linux
