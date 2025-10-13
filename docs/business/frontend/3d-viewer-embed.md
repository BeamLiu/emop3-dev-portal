# 3D模型预览嵌入集成

## 概述

EMOP提供了一个轻量化的3D模型预览页面，可以通过iframe嵌入到第三方网站中，实现3D模型的在线预览。

## 特性

- ✅ 轻量化设计，独立运行，不依赖整个系统
- ✅ 公开访问，页面无需登录（权限在模型加载时控制）
- ✅ 支持iframe嵌入
- ✅ 完整的3D查看功能（旋转、缩放、测量、剖切、爆炸视图等）

## 快速开始

### 访问地址

```
https://dev.emop.emopdata.com/web/public-3d.html?model=<模型路径>
```

### 基础嵌入

```html
<iframe 
  src="https://dev.emop.emopdata.com/web/public-3d.html?model=models/your-model.stp.cdxfb"
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
    src="https://dev.emop.emopdata.com/web/public-3d.html?model=models/your-model.stp.cdxfb"
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
load3DModel('models/my-model.stp.cdxfb');
```

## URL参数

| 参数 | 必需 | 说明 | 示例 |
|------|------|------|------|
| model | 是 | 模型文件路径 | models/as1-oc-214.stp.cdxfb |

## 权限控制

- **页面访问**：公开，无需登录
- **模型加载**：在后端API层面控制权限
  - 有权限：正常加载模型
  - 无权限：返回401/403错误，显示错误提示

## 支持的功能

- 🔄 基础操作：旋转、缩放、平移、适应视图
- 📏 测量工具：距离、角度、面积测量
- ✂️ 剖切功能：X/Y/Z轴剖切、自定义剖切平面
- 💥 爆炸视图：可调节爆炸程度
- 📝 标注功能：添加和管理标注
- 📸 截图功能：当前视图截图和图片库
- 🖥️ 全屏模式：支持全屏查看

## 完整示例

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>3D模型预览</title>
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
