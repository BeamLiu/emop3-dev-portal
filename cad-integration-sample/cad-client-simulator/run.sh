#!/bin/bash

# CAD Client Simulator 启动脚本
# 从 .env 文件读取 JBR 配置

set -e  # 遇到错误立即退出

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "==================================="
echo "CAD Client Simulator - 启动"
echo "==================================="
echo ""

# 加载 .env 配置
if [ ! -f ".env" ]; then
    echo "❌ 错误: 未找到 .env 配置文件"
    echo ""
    echo "请执行以下步骤："
    echo "  1. 复制配置文件: cp .env.example .env"
    echo "  2. 编辑 .env 文件，设置你的 JBR_HOME 路径"
    echo "  3. 重新运行此脚本"
    echo ""
    exit 1
fi

# 读取 .env 文件
echo "📄 读取配置文件: .env"
source .env

# 检查 JBR_HOME
if [ -z "$JBR_HOME" ]; then
    echo "❌ 错误: .env 文件中未设置 JBR_HOME"
    echo ""
    echo "请编辑 .env 文件，设置 JBR_HOME，例如："
    echo "  JBR_HOME=/home/user/jbr/jbr_jcef-17.0.10-linux-x64-b1087.23"
    echo ""
    exit 1
fi

# 检查 JBR 目录是否存在
if [ ! -d "$JBR_HOME" ]; then
    echo "❌ 错误: JBR 目录不存在"
    echo "路径: $JBR_HOME"
    echo ""
    echo "请检查 .env 文件中的 JBR_HOME 路径是否正确"
    exit 1
fi

# 检查 java 可执行文件
JAVA_BIN="$JBR_HOME/bin/java"
if [ ! -f "$JAVA_BIN" ]; then
    echo "❌ 错误: 未找到 java 可执行文件"
    echo "路径: $JAVA_BIN"
    echo ""
    echo "请确认 JBR_HOME 路径是否正确"
    exit 1
fi

echo "✅ 使用 JBR: $JBR_HOME"
echo ""

# 显示 Java 版本
echo "📋 Java 版本信息:"
"$JAVA_BIN" -version 2>&1 | head -3
echo ""

# 检查是否是 JBR
if ! "$JAVA_BIN" -version 2>&1 | grep -q "JBR"; then
    echo "⚠️  警告: 当前 Java 可能不是 JetBrains Runtime"
    echo ""
fi


# 检查是否已编译
if [ ! -d "target/classes" ]; then
    echo "⚠️  警告: 未找到编译输出，正在自动编译..."
    echo ""
    ./compile.sh
    echo ""
fi

# 启动应用
echo "🚀 启动 CAD Client Simulator..."
echo ""
echo "提示: 按 Ctrl+C 停止应用"
echo ""
echo "-----------------------------------"
echo ""

# 使用指定的 JBR 运行
JAVA_HOME="$JBR_HOME" mvn exec:java
