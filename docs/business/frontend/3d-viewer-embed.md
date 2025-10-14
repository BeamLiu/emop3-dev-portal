# 3D模型预览嵌入集成

## 概述

EMOP提供了一个轻量化的3D模型预览页面，可以通过iframe嵌入到第三方网站中，实现3D模型的在线预览。

## 特性

- ✅ 轻量化设计，独立运行，不依赖整个系统
- ✅ 公开访问，页面无需登录（权限在模型加载时控制）
- ✅ 支持iframe嵌入
- ✅ 支持多种模型地址格式（相对路径、绝对路径、文件代理）
- ✅ 完整的3D查看功能（旋转、缩放、测量、剖切、爆炸视图等）

## 快速开始

### 访问地址

```
https://dev.emop.emopdata.com/web/public-3d.html?model=<模型地址>
```

### URL参数

| 参数 | 必需 | 说明 |
|------|------|------|
| model | 是 | 模型文件地址，支持相对路径、绝对路径和文件代理 |

### model 参数支持的格式

#### 1. 相对路径
适用于模型文件存储在系统内部的场景。

```
?model=models/as1-oc-214.stp.cdxfb
```

#### 2. 绝对路径（跨域）
适用于模型文件存储在外部CDN或其他域名的场景。

```
?model=https://cdn.example.com/models/demo.cdxfb
```

#### 3. 文件代理（推荐）
适用于需要动态加载模型及其附加文件的场景。

```
?model=/api/file-proxy/123
```

## 使用示例

### 基础嵌入

```html
<!-- 方式1：相对路径 -->
<iframe 
  src="https://dev.emop.emopdata.com/web/public-3d.html?model=models/demo.cdxfb"
  width="100%" 
  height="600px" 
  frameborder="0"
  allowfullscreen>
</iframe>

<!-- 方式2：绝对路径（跨域） -->
<iframe 
  src="https://dev.emop.emopdata.com/web/public-3d.html?model=https://cdn.example.com/models/demo.cdxfb"
  width="100%" 
  height="600px" 
  frameborder="0"
  allowfullscreen>
</iframe>

<!-- 方式3：文件代理（推荐） -->
<iframe 
  src="https://dev.emop.emopdata.com/web/public-3d.html?model=/api/file-proxy/123"
  width="100%" 
  height="600px" 
  frameborder="0"
  allowfullscreen>
</iframe>
```

### 响应式嵌入

```html
<div style="position: relative; padding-bottom: 56.25%; height: 0; overflow: hidden;">
  <iframe 
    src="https://dev.emop.emopdata.com/web/public-3d.html?model=/api/file-proxy/123"
    style="position: absolute; top: 0; left: 0; width: 100%; height: 100%;"
    frameborder="0"
    allowfullscreen>
  </iframe>
</div>
```

### JavaScript动态加载

```javascript
function load3DModel(modelPath) {
  const iframe = document.createElement('iframe');
  iframe.src = `https://dev.emop.emopdata.com/web/public-3d.html?model=${encodeURIComponent(modelPath)}`;
  iframe.width = '100%';
  iframe.height = '600px';
  iframe.frameBorder = '0';
  iframe.allowFullscreen = true;
  
  document.getElementById('viewer-container').appendChild(iframe);
}

// 使用示例
load3DModel('models/my-model.stp.cdxfb');  // 相对路径
load3DModel('https://cdn.example.com/models/demo.cdxfb');  // 绝对路径
load3DModel('/api/file-proxy/123');  // 文件代理
```

## 文件代理方式详解（推荐）

### 什么是文件代理

文件代理是一个通用的文件目录访问服务，允许通过文件ID访问该文件所在目录下的任意文件。

### 为什么推荐使用文件代理

- ✅ **无需知道完整路径**：只需要文件ID即可访问
- ✅ **自动加载附加文件**：3D模型的纹理、材质等附加文件会自动加载
- ✅ **权限控制**：基于文件ID的权限验证
- ✅ **灵活管理**：文件移动或重命名不影响访问
- ✅ **通用设计**：不仅限于3D模型，适用于任何需要访问同目录文件的场景

### 工作原理

1. 系统根据文件ID查询文件记录，获取文件存储路径
2. 解析出文件所在目录
3. 允许访问该目录下的任意文件
4. 3D查看器加载主模型文件时，会自动请求同目录下的其他文件

### API端点

```
GET /api/file-proxy/{fileId}/{filename}  # 获取指定文件
GET /api/file-proxy/{fileId}/list        # 列出目录下所有文件
```

### 文件目录结构示例

假设文件ID为123，对应的存储路径为：
```
cad/demo/cad-integration-client/-1175438643740663548/converted/scenegraph.cdxfb
```

系统会自动支持访问同目录下的所有文件：
```
cad/demo/cad-integration-client/-1175438643740663548/
├── onverted/scenegraph.cdxfb                          # 主模型文件
├── onverted/782d57f2-4ba9-4b38-bf37-0567118abb7b     # 附加文件
├── prt0118_1.prt.20                          # 零件文件
├── prt0118_1.prt.20.jpg                      # 预览图
└── ...                                        # 其他相关文件
```

### 使用示例

```html
<!-- 只需要提供文件ID -->
<iframe 
  src="https://dev.emop.emopdata.com/web/public-3d.html?model=/minioproxy/api/file-proxy/123/converted"
  width="100%" 
  height="600px" 
  frameborder="0"
  allowfullscreen>
</iframe>
```

3D查看器会自动加载：
- `/api/file-proxy/123/converted` （主模型）
- `/api/file-proxy/123/converted/782d57f2-4ba9-4b38-bf37-0567118abb7b` （附加文件）
- `/api/file-proxy/123/prt0118_1.prt.20.jpg` （预览图）
- 等等...

## 权限控制

- **页面访问**：公开，无需登录
- **文件加载**：在后端API层面控制权限
  - 有权限：正常加载文件
  - 无权限：返回401/403错误，显示错误提示

## 支持的功能

- 🔄 **基础操作**：旋转、缩放、平移、适应视图
- 📏 **测量工具**：距离、角度、面积测量
- ✂️ **剖切功能**：X/Y/Z轴剖切、自定义剖切平面
- 💥 **爆炸视图**：可调节爆炸程度
- 📝 **标注功能**：添加和管理标注
- 📸 **截图功能**：当前视图截图和图片库
- 🖥️ **全屏模式**：支持全屏查看

## 完整示例

### 示例1：静态路径方式

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>3D模型预览 - 静态路径</title>
    <style>
        body {
            margin: 0;
            padding: 20px;
            font-family: Arial, sans-serif;
        }
        .container {
            max-width: 1200px;
            margin: 0 auto;
        }
        h1 {
            color: #333;
        }
        .viewer-container {
            width: 100%;
            height: 600px;
            border: 1px solid #ddd;
            border-radius: 4px;
            overflow: hidden;
        }
        iframe {
            width: 100%;
            height: 100%;
            border: none;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>3D模型预览示例</h1>
        <div class="viewer-container">
            <iframe 
                src="https://dev.emop.emopdata.com/web/public-3d.html?model=models/as1-oc-214.stp.cdxfb"
                allowfullscreen>
            </iframe>
        </div>
    </div>
</body>
</html>
```

### 示例2：文件代理方式（推荐）

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>3D模型预览 - 文件代理</title>
    <style>
        body {
            margin: 0;
            padding: 20px;
            font-family: Arial, sans-serif;
        }
        .container {
            max-width: 1200px;
            margin: 0 auto;
        }
        h1 {
            color: #333;
        }
        .viewer-container {
            width: 100%;
            height: 600px;
            border: 1px solid #ddd;
            border-radius: 4px;
            overflow: hidden;
            margin-bottom: 20px;
        }
        iframe {
            width: 100%;
            height: 100%;
            border: none;
        }
        .controls {
            margin-bottom: 20px;
        }
        input {
            padding: 8px;
            width: 200px;
            margin-right: 10px;
        }
        button {
            padding: 8px 16px;
            background: #409eff;
            color: white;
            border: none;
            border-radius: 4px;
            cursor: pointer;
        }
        button:hover {
            background: #66b1ff;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>3D模型预览示例 - 动态加载</h1>
        
        <div class="controls">
            <input type="text" id="modelInput" placeholder="输入模型地址" value="/minioproxy/api/file-proxy/123/converted">
            <button onclick="loadModel()">加载模型</button>
        </div>
        
        <div class="viewer-container">
            <iframe 
                id="modelViewer"
                src="https://dev.emop.emopdata.com/web/public-3d.html?model=/minioproxy/api/file-proxy/123"
                allowfullscreen>
            </iframe>
        </div>
    </div>
    
    <script>
        function loadModel() {
            const modelPath = document.getElementById('modelInput').value;
            if (modelPath) {
                const iframe = document.getElementById('modelViewer');
                iframe.src = `https://dev.emop.emopdata.com/web/public-3d.html?model=${encodeURIComponent(modelPath)}`;
            } else {
                alert('请输入模型地址');
            }
        }
    </script>
</body>
</html>
```

### 示例3：跨域绝对路径

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>3D模型预览 - 跨域</title>
    <style>
        body {
            margin: 0;
            padding: 20px;
            font-family: Arial, sans-serif;
        }
        .container {
            max-width: 1200px;
            margin: 0 auto;
        }
        .viewer-container {
            width: 100%;
            height: 600px;
            border: 1px solid #ddd;
            border-radius: 4px;
            overflow: hidden;
        }
        iframe {
            width: 100%;
            height: 100%;
            border: none;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>3D模型预览 - 外部CDN</h1>
        <div class="viewer-container">
            <iframe 
                src="https://dev.emop.emopdata.com/web/public-3d.html?model=https://cdn.example.com/models/demo.cdxfb"
                allowfullscreen>
            </iframe>
        </div>
    </div>
</body>
</html>
```

## 测试页面

系统提供了一个测试页面，方便快速测试不同的模型地址：

```
https://dev.emop.emopdata.com/web/public-3d-test.html
```

在测试页面中，你可以：
- 输入任意格式的模型地址
- 实时预览加载效果
- 查看使用示例

## 常见问题

### Q: 如何选择使用哪种方式？

**A:** 推荐优先级：
1. **文件代理** - 适用于系统内部文件，需要自动加载附加文件
2. **绝对路径** - 适用于外部CDN或跨域场景
3. **相对路径** - 适用于简单的静态文件场景

### Q: 文件代理方式如何知道主模型文件名？

**A:** 对于3D模型，主文件通常命名为 `scenegraph.cdxfb`。如果你的主文件名不同，需要在URL中指定完整的文件名。

### Q: 跨域访问有限制吗？

**A:** 使用绝对路径时，需要确保目标服务器配置了正确的CORS头，允许跨域访问。

### Q: 文件代理方式的安全性如何？

**A:** 文件代理基于文件ID进行权限验证，只能访问有权限的文件所在目录，且不能跨目录访问，安全性较高。

## 相关资源

- **测试页面**: `/web/public-3d-test.html`
- **API文档**: 参考 `FileDirectoryProxyController` 接口文档
