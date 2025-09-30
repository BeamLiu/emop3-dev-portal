# 前端开发陷阱集锦

💡 **文档目的**：记录开发过程中遇到的重大问题，特别是花费大量时间才解决的陷阱，通常这些问题比较隐秘，AI也无法快速识别，帮助团队避免重复踩坑。

## ref追踪复杂对象导致的浏览器冻结

#### 问题现象

引入第三方组件时，工作异常，浏览器经常卡住，页面有些功能不正常

#### 根本原因

第三方复杂对象被`ref`追踪，特别是复杂的第三方组件对象，例如图的`graph`, 流程图的`workflow`
等对象。由于第三方对象是个黑盒子，很容易被忽略，另外在复杂页面被拆解组件化之后，这些对象也很容易被AI使用`ref`
来包装，用于确保在各个hooks等`ts`中能被响应式地使用。

#### 解决方案

移除`ref`包裝，使用`function`用于获取对象引用, 例如

```javascript
// 主页面
let graph;
const toolbar = useToolbar(() => graph);
onMounted(() => {
    graph = initGraph();
});

//useToolbar.ts
export function useToolbar(getGraph: () => Graph) {
}
```
---

**💡 记住**：每个陷阱都是宝贵的学习机会，记录和分享能让整个团队受益！