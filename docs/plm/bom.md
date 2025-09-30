# BOM设计

## 1. 核心概念

### 1.1 BOM数据模型

物料清单(Bill of Materials, BOM)是描述产品结构的基础数据模型。EMOP中的BOM系统基于几个核心对象构建：

1. **BOM视图 (BOM View)**
   - BOM的容器对象
   - 定义BOM的类型和属性
   - 管理整体配置规则

2. **BOM行 (BOM Line)**
   - BOM的节点对象
   - 维护父子层级关系
   - 记录用量和属性信息

3. **目标对象 (Target Object)**
   - 被BOM行引用的业务对象
   - 可以是零件、文档等
   - 具有独立的版本管理

```mermaid
graph TD
    BV[BomView<br>视图定义] --> |topline<br>顶层节点关系| BL1[BomLine<br>顶层节点]
    BL1 --> |hierarchical<br>层级关系| BL2[BomLine<br>子节点1]
    BL1 --> |hierarchical<br>层级关系| BL3[BomLine<br>子节点2]
    BL1 --> |target<br>目标对象关系| T1[ModelObject<br>目标对象1]
    BL2 --> |target| T2[ModelObject<br>目标对象2]
    BL3 --> |target| T3[ModelObject<br>目标对象3]
    
    style BV fill:#2C3E50,stroke:#34495E,color:#FFFFFF
    style BL1 fill:#34495E,stroke:#2C3E50,color:#FFFFFF
    style BL2 fill:#34495E,stroke:#2C3E50,color:#FFFFFF
    style BL3 fill:#34495E,stroke:#2C3E50,color:#FFFFFF
    style T1 fill:#3498DB,stroke:#2980B9,color:#FFFFFF
    style T2 fill:#3498DB,stroke:#2980B9,color:#FFFFFF
    style T3 fill:#3498DB,stroke:#2980B9,color:#FFFFFF
```
### 1.2 版本管理基础

#### 1.2.1 版本状态流转

产品数据在生命周期中会经历不同的状态，见[对象状态管理](../business/modeling/state-management#系统默认状态说明)

#### 1.2.2 版本有效性

版本有效性管理确保系统在任何时间点都能找到正确的数据版本：

1. **时间有效性**
   - 生效时间: 版本开始可用的时间点
   - 失效时间: 版本停止使用的时间点
   - 过渡期: 新旧版本共存的时间段

2. **状态有效性**
   - 依赖版本状态确定是否可用
   - 不同环境可采用不同的有效性规则
   - 支持版本平滑过渡

```mermaid
%%{init: { 'gantt': {'timeAxis': 'top', 'barHeight': 40, 'sectionHeight': 50, 'topPadding': 30, 'bottomPadding': 30, 'leftPadding': 100, 'rightPadding': 100, 'width': 1200 } } }%%
gantt
    title 产品版本生命周期管理
    dateFormat YYYY-MM-DD
    axisFormat %Y-%m-%d
    
    section 版本A
    开发阶段     :a1, 2024-01-01, 30d
    评审阶段     :a2, after a1, 15d
    正式使用     :a3, after a2, 60d
    
    section 版本B 
    开发阶段     :b1, 2024-02-15, 30d
    评审阶段     :b2, after b1, 15d
    正式使用     :b3, after b2, 90d
    
    section 版本C
    开发阶段     :c1, 2024-04-01, 30d
    评审阶段     :c2, after c1, 15d
    正式使用     :c3, after c2, 60d

    section 里程碑
    A版本发布    :milestone, after a2, 0d
    B版本发布    :milestone, after b2, 0d
    C版本发布    :milestone, after c2, 0d
```
### 1.3 BOM精确性管理

#### 1.3.1 精确与非精确BOM

BOM的精确性描述了其引用目标对象的方式，这直接影响了BOM的使用场景和行为：

1. **非精确BOM**
   - 动态引用目标对象，不指定具体版本
   - 版本可以根据版本规则动态解析

2. **精确BOM**
   - 精确引用特定版本的目标对象
   - 版本关系固定不变

```mermaid
graph TB
    subgraph 非精确BOM
        NP[产品] --> NA[组件A]
        NP --> NB[组件B]
        style NP fill:#f9f,stroke:#333
        style NA fill:#bbf,stroke:#333
        style NB fill:#bbf,stroke:#333
    end
    
    subgraph 精确BOM
        PP[产品/A.1] --> PA[组件A/B.2]
        PP --> PB[组件B/C.1]
        style PP fill:#f9f,stroke:#333
        style PA fill:#bbf,stroke:#333
        style PB fill:#bbf,stroke:#333
    end
    
    注意1[根据版本规则动态解析最新版本]
    注意2[固定具体版本号]
    
    NP -.-> 注意1
    PP -.-> 注意2
```
#### 1.3.2 版本规则

版本规则定义了如何解析目标对象的具体版本，是BOM系统的核心配置之一：

1. **基本规则类型**
   - 最新版本规则：始终使用最新的工作版本
   - 发布版本规则：使用最新的已发布版本
   - 固定版本规则：使用指定的版本号
   - 时间点规则：使用特定时间点的有效版本

2. **规则应用场景**
   - 开发环境：通常使用最新版本规则，方便快速迭代
   - 生产环境：使用发布版本规则，确保稳定性
   - 历史追溯：使用固定版本规则，保证可追溯性
   - 时间回溯：使用时间点规则，查看历史配置

```mermaid
flowchart TD
    subgraph 版本解析规则
        VR{规则类型?}
        VR -->|最新版本| Latest[获取最新工作版本]
        VR -->|发布版本| Released[获取最新发布版本]
        VR -->|固定版本| Fixed[获取指定版本]
        VR -->|时间点| TimePoint[获取时间点版本]
        
        Latest --> Check1{版本状态检查}
        Released --> Check2{发布状态检查}
        Fixed --> Check3{版本存在检查}
        TimePoint --> Check4{时间有效性检查}
    end
    
    style VR fill:#f9f,stroke:#333
    style Latest fill:#bbf,stroke:#333
    style Released fill:#bbf,stroke:#333
    style Fixed fill:#bbf,stroke:#333
    style TimePoint fill:#bbf,stroke:#333
    
    subgraph 应用环境
        Dev[开发环境] -.-> Latest
        Prod[生产环境] -.-> Released
        History[历史追溯] -.-> Fixed
        Trace[时间回溯] -.-> TimePoint
    end
```

### 1.4 替代料与互换性管理

BOM系统支持灵活的替代料和互换性管理，确保生产过程中的物料灵活性：

1. **替代料管理**
   - 定义：可在特定条件下替换主要物料的备选物料
   - 替代关系类型：完全替代、有条件替代、部分替代
   - 优先级控制：定义多个替代料之间的使用优先顺序

2. **互换性定义**
   - 双向互换：两个物料完全等效，可双向替换
   - 单向互换：替代料可替换主料，但主料不一定能替换替代料
   - 属性互换：基于特定属性集合的互换性判断

3. **替代规则**
   - 时间有效性：替代关系的生效和失效时间控制
   - 批次限制：特定批次的替代规则
   - 场景应用：设计、采购、生产等不同场景的替代策略

```mermaid
graph TD
    subgraph 替代料管理
        MP[主物料] --- |主要用料| BL[BOM行]
        MP --- |替代关系| S1[替代料1<br>优先级:1]
        MP --- |替代关系| S2[替代料2<br>优先级:2]
        MP --- |替代关系| S3[替代料3<br>优先级:3]
        
        S1 --- |条件| C1[适用条件<br>生效期/场景]
        S2 --- |条件| C2[适用条件<br>生效期/场景]
        S3 --- |条件| C3[适用条件<br>生效期/场景]
    end
    
    style MP fill:#f9f,stroke:#333
    style BL fill:#bbf,stroke:#333
    style S1 fill:#bfb,stroke:#333
    style S2 fill:#bfb,stroke:#333
    style S3 fill:#bfb,stroke:#333
```

4. **应用场景**
   - 物料短缺时的替代方案管理
   - 成本优化和供应链弹性增强
   - 设计变更过渡期的兼容管理

## 2. 内置BOM类型分析
### 2.1 研发设计阶段
#### 2.1.1 CAD BOM

CAD BOM是产品设计阶段的基础数据，直接反映了产品的三维设计结构：

1. **特点**
   - 完全对应CAD模型结构
   - 保持与CAD系统同步
   - 支持自动导入导出
   - 包含设计参数和约束

2. **应用场景**
   - 3D设计数据管理
   - 2D工程图管理
   - 设计变更管理
   - 干涉检查与仿真

3. **对象类型**
   - **BomView (type=CAD_BOM)**: CAD BOM的容器对象
   - **BomLine**: CAD BOM的节点对象
   - **CADComponent**: 装配体对象，零件对象都用CADComponent表达

```mermaid
graph TD
    subgraph CAD系统
        A[总装配体<br>Assembly] --> B[子装配体1<br>SubAssembly]
        A --> C[子装配体2<br>SubAssembly]
        B --> D[零件1<br>Component]
        B --> E[零件2<br>Component]
        C --> F[零件3<br>Component]
        C --> G[零件4<br>Component]
    end
    
    subgraph EMOP系统
        CAD_BV[BomView<br>type=CAD_BOM] --> CAD_BL1[BomLine]
        CAD_BL1 --> CAD_BL2[BomLine]
        CAD_BL1 --> CAD_BL3[BomLine]
        CAD_BL2 --> CAD_BL4[BomLine]
        CAD_BL2 --> CAD_BL5[BomLine]
        CAD_BL3 --> CAD_BL6[BomLine]
        CAD_BL3 --> CAD_BL7[BomLine]
        
        CAD_BL1 --> AsmObj[CADComponent<br>总装配体]
        CAD_BL2 --> SubAsm1[CADComponent<br>子装配体1]
        CAD_BL3 --> SubAsm2[CADComponent<br>子装配体2]
        CAD_BL4 --> Comp1[CADComponent<br>零件1]
        CAD_BL5 --> Comp2[CADComponent<br>零件2]
        CAD_BL6 --> Comp3[CADComponent<br>零件3]
        CAD_BL7 --> Comp4[CADComponent<br>零件4]
    end
    
    A -.同步.-> AsmObj
    B -.同步.-> SubAsm1
    C -.同步.-> SubAsm2
    D -.同步.-> Comp1
    E -.同步.-> Comp2
    F -.同步.-> Comp3
    G -.同步.-> Comp4
    
    style A fill:#f9f,stroke:#333
    style CAD_BV fill:#bbf,stroke:#333
    style AsmObj fill:#bfb,stroke:#333
    style SubAsm1 fill:#bfb,stroke:#333
    style SubAsm2 fill:#bfb,stroke:#333
    style Comp1 fill:#fbb,stroke:#333
    style Comp2 fill:#fbb,stroke:#333
    style Comp3 fill:#fbb,stroke:#333
    style Comp4 fill:#fbb,stroke:#333
```

#### 2.1.2 CAD BOM到EBOM的转换

CAD BOM到工程BOM(EBOM)的转换是产品设计过程中的关键步骤，实现设计数据到产品定义的转换：

1. **转换流程**
   - 读取CAD BOM结构
   - 建立Part与CADComponent的映射
   - 处理结构变换规则
   - 创建EBOM结构

2. **转换规则**
   - 结构简化：合并不必要的中间节点
   - 数据丰富：添加非CAD属性信息
   - 属性映射：将CAD参数转换为工程参数
   - 标准件识别：自动识别并分类标准件

```mermaid
graph TD
    subgraph CAD BOM结构
        CADBV[BomView<br>type=CAD_BOM] --> CADBL1[BomLine]
        CADBL1 --> CADBL2[BomLine]
        CADBL1 --> CADBL3[BomLine]
        
        CADBL1 --> CADO1[CADAssembly<br>总装配体]
        CADBL2 --> CADO2[CADAssembly<br>子装配体]
        CADBL3 --> CADO3[CADComponent<br>零件]
    end
    
    subgraph EBOM结构
        EBV[BomView<br>type=EBOM] --> EBL1[BomLine]
        EBL1 --> EBL2[BomLine]
        EBL1 --> EBL3[BomLine]
        
        EBL1 --> EP1[Part<br>产品]
        EBL2 --> EP2[Part<br>组件]
        EBL3 --> EP3[Part<br>零件]
    end
    
    CADBV -- 转换 --> EBV
    CADO1 -- 映射 --> EP1
    CADO2 -- 映射 --> EP2
    CADO3 -- 映射 --> EP3
    
    style CADBV fill:#f9f,stroke:#333
    style EBV fill:#bbf,stroke:#333
    style CADO1 fill:#bfb,stroke:#333
    style CADO2 fill:#bfb,stroke:#333
    style CADO3 fill:#fbb,stroke:#333
    style EP1 fill:#fbb,stroke:#333
    style EP2 fill:#fbb,stroke:#333
    style EP3 fill:#fbb,stroke:#333
```

在转换过程中，CADAssembly通常映射为Part对象(category="assembly")，而CADComponent映射为Part对象(category="component")。转换后的EBOM保持与CAD BOM的关联关系，便于设计变更同步。

#### 2.1.3 工程BOM (EBOM)

EBOM是产品设计的标准化描述，是产品定义的核心数据：

1. **基本特征**
   - 完整的产品结构定义
   - 规范的技术要求描述
   - 版本控制和变更管理
   - 配置规则支持

2. **关键属性**
   - 产品规格参数
   - 材料技术要求
   - 质量控制标准
   - 设计验证要求

3. **主要功能**
   - 产品结构管理
   - 技术文档管理
   - 设计评审支持
   - 变更影响分析

4. **对象类型**
   - **BomView (type=EBOM)**: EBOM的容器对象
   - **BomLine**: EBOM的节点对象
   - **Part**: 产品、组件和零件对象
   - **Document**: 技术文档对象
   - **Specification**: 规格说明对象

```mermaid
graph TD
    subgraph EBOM结构
        EBV[BomView<br>type=EBOM] --> EBL_ROOT[BomLine]
        EBL_ROOT --> EBL_A[BomLine]
        EBL_ROOT --> EBL_B[BomLine]
        EBL_A --> EBL_A1[BomLine]
        EBL_A --> EBL_A2[BomLine]
        EBL_B --> EBL_B1[BomLine]
        EBL_B --> EBL_B2[BomLine]
        
        EBL_ROOT --> E_ROOT[Part<br>产品]
        EBL_A --> E_A[Part<br>总成A]
        EBL_B --> E_B[Part<br>总成B]
        EBL_A1 --> E_A1[Part<br>组件A1]
        EBL_A2 --> E_A2[Part<br>组件A2]
        EBL_B1 --> E_B1[Part<br>组件B1]
        EBL_B2 --> E_B2[Part<br>组件B2]
    end
    
    subgraph 关联信息
        E_A1 -.-|技术规格| SPEC_A1[Specification<br>规格文档]
        E_A1 -.-|3D模型| MODEL_A1[CADComponent<br>CAD文件]
        E_A1 -.-|工程图| DWG_A1[Document<br>图纸]
        E_A1 -.-|材料| MAT_A1[Material<br>材料规范]
    end
    
    style EBV fill:#f9f,stroke:#333
    style E_ROOT fill:#bbf,stroke:#333
    style E_A fill:#bbf,stroke:#333
    style E_B fill:#bbf,stroke:#333
    style SPEC_A1 fill:#bfb,stroke:#333
    style MODEL_A1 fill:#bfb,stroke:#333
    style DWG_A1 fill:#bfb,stroke:#333
    style MAT_A1 fill:#bfb,stroke:#333
```

EBOM中的Part可以关联多种信息，包括规格文档、CAD模型、图纸和材料规范等，形成完整的产品定义。

#### 2.1.4 查找号与定位管理

查找号和定位管理是工程BOM中的关键功能，它将物料清单与实际装配位置关联起来：

1. **查找号系统**
   - 定义：唯一标识装配体中零件位置的编号体系
   - 编号方案：序列式、分区式、层次式等多种方案
   - 自动生成：支持基于规则的查找号自动生成

2. **位置属性**
   - 坐标定位：基于3D空间坐标的精确定位
   - 相对定位：相对于参考点或面的位置描述
   - 装配路径：组装或拆卸的路径和方向定义

3. **视图关联**
   - 与工程图关联：查找号与2D工程图的标注关联
   - 与3D模型关联：查找号与3D模型中位置的映射
   - 爆炸图支持：支持装配爆炸图与查找号的对应

```mermaid
graph TD
    subgraph 查找号与位置管理
        ASM[装配体] --> P1[零件1<br>查找号:A1]
        ASM --> P2[零件2<br>查找号:A2]
        ASM --> P3[零件3<br>查找号:B1]
        
        P1 --> POS1[位置属性<br>坐标/方向]
        P2 --> POS2[位置属性<br>坐标/方向]
        P3 --> POS3[位置属性<br>坐标/方向]
        
        DWG[工程图] -.关联.-> P1
        DWG -.关联.-> P2
        DWG -.关联.-> P3
    end
    
    style ASM fill:#f9f,stroke:#333
    style P1 fill:#bbf,stroke:#333
    style P2 fill:#bbf,stroke:#333
    style P3 fill:#bbf,stroke:#333
    style DWG fill:#bfb,stroke:#333
```

4. **实施方法**
   - BOM行查找号属性：在BomLine上添加查找号和位置属性
   - 查找号规则引擎：支持企业特定的查找号分配规则
   - 冲突检测：防止查找号重复或错误的机制

### 2.2 生产制造阶段

#### 2.2.1 工艺BOM (PBOM)

工艺BOM(Process BOM, PBOM)是连接设计和制造的桥梁，主要用于工艺规划和准备阶段：

1. **基本特征**
   - 基于EBOM结构进行工艺分析
   - 添加工艺路线和工序信息
   - 定义制造工艺要求
   - 组织生产准备数据

2. **关键属性**
   - 工艺路线编号
   - 工序编码和顺序
   - 工艺参数和要求
   - 工装夹具定义

3. **主要功能**
   - 工艺路线规划
   - 工艺文件管理
   - 工序资源配置
   - 生产能力分析

4. **对象类型**
   - **BomView (type=PBOM)**: PBOM的容器对象
   - **BomLine**: PBOM的节点对象
   - **Route**: 工艺路线对象
   - **ProcessStep**: 工序步骤对象
   - **Part**: 引用自EBOM的零部件对象
   - **ProcessDocument**: 工艺文档对象

```mermaid
graph TD
    subgraph PBOM结构
        PBV[BomView<br>type=PBOM] --> PBL_ROOT[BomLine]
        PBL_ROOT --> PBL_R1[BomLine]
        PBL_ROOT --> PBL_R2[BomLine]
        
        PBL_R1 --> PBL_PS1[BomLine]
        PBL_R1 --> PBL_PS2[BomLine]
        PBL_R2 --> PBL_PS3[BomLine]
        PBL_R2 --> PBL_PS4[BomLine]
        
        PBL_PS1 --> PBL_P1[BomLine]
        PBL_PS2 --> PBL_P2[BomLine]
        PBL_PS3 --> PBL_P3[BomLine]
        PBL_PS4 --> PBL_P4[BomLine]
        
        PBL_ROOT --> PR[Part<br>产品]
        PBL_R1 --> R1[Route<br>工艺路线1]
        PBL_R2 --> R2[Route<br>工艺路线2]
        
        PBL_PS1 --> PS1[ProcessStep<br>工序1]
        PBL_PS2 --> PS2[ProcessStep<br>工序2]
        PBL_PS3 --> PS3[ProcessStep<br>工序3]
        PBL_PS4 --> PS4[ProcessStep<br>工序4]
        
        PBL_P1 --> P1[Part<br>零件1]
        PBL_P2 --> P2[Part<br>零件2]
        PBL_P3 --> P3[Part<br>零件3]
        PBL_P4 --> P4[Part<br>零件4]
    end
    
    subgraph 关联资源
        PS1 -.-|工艺文档| PD1[ProcessDocument<br>工艺卡]
        PS1 -.-|工装| T1[Tool<br>工装夹具]
        PS1 -.-|检具| I1[Instrument<br>检测工具]
    end
    
    style PBV fill:#f9f,stroke:#333
    style PR fill:#bbf,stroke:#333
    style R1 fill:#bbf,stroke:#333
    style R2 fill:#bbf,stroke:#333
    style PS1 fill:#bfb,stroke:#333
    style PS2 fill:#bfb,stroke:#333
    style PS3 fill:#bfb,stroke:#333
    style PS4 fill:#bfb,stroke:#333
    style P1 fill:#fbb,stroke:#333
    style P2 fill:#fbb,stroke:#333
    style P3 fill:#fbb,stroke:#333
    style P4 fill:#fbb,stroke:#333
    style PD1 fill:#fbf,stroke:#333
    style T1 fill:#fbf,stroke:#333
    style I1 fill:#fbf,stroke:#333
```

PBOM通常以工艺路线为主线，将产品结构组织为工序步骤，每个工序步骤关联所需的零部件和资源。PBOM是从EBOM转换而来，保持与设计数据的关联。

#### 2.2.2 制造BOM (MBOM)

MBOM面向生产制造过程，反映了产品的实际装配和制造结构：

1. **核心特点**
   - 基于工艺路线组织
   - 包含制造过程信息
   - 关联生产资源
   - 支持车间现场管理

2. **重要属性**
   - 工序步骤定义
   - 工装夹具需求
   - 人力资源要求
   - 质量检验标准

3. **主要应用**
   - 生产计划制定
   - 工艺过程管理
   - 资源需求计算
   - 生产成本核算

4. **对象类型**
   - **BomView (type=MBOM)**: MBOM的容器对象
   - **BomLine**: MBOM的节点对象
   - **Operation**: 制造工序对象
   - **Part**: 引用自EBOM的零部件对象
   - **Resource**: 制造资源对象

```mermaid
graph TD
    subgraph MBOM结构
        MBV[BomView<br>type=MBOM] --> MBL1[BomLine]
        MBL1 --> MBL2[BomLine]
        MBL2 --> MBL3[BomLine]
        MBL2 --> MBL4[BomLine]
        MBL3 --> MBL5[BomLine]
        
        MBL1 --> O1[Operation<br>工序1]
        MBL2 --> O2[Operation<br>工序2]
        MBL3 --> O3[Operation<br>工序3]
        MBL4 --> R1[Resource<br>资源1]
        MBL5 --> P1[Part<br>零件]
    end
    
    subgraph 关联信息
        O1 -.-|工作中心| WC[WorkCenter<br>工作中心]
        O1 -.-|员工| EMP[Employee<br>操作人员]
        O1 -.-|设备| EQP[Equipment<br>设备]
    end
    
    style MBV fill:#f9f,stroke:#333
    style MBL1 fill:#bbf,stroke:#333
    style MBL2 fill:#bbf,stroke:#333
    style MBL3 fill:#bbf,stroke:#333
    style O1 fill:#bfb,stroke:#333
    style O2 fill:#bfb,stroke:#333
    style O3 fill:#bfb,stroke:#333
    style P1 fill:#f96,stroke:#333
    style R1 fill:#fbf,stroke:#333
    style WC fill:#fbb,stroke:#333
    style EMP fill:#fbb,stroke:#333
    style EQP fill:#fbb,stroke:#333
```

MBOM与PBOM紧密相关，通常是从PBOM转换生成。与PBOM相比，MBOM更加注重生产执行和资源配置，直接服务于生产现场。MBOM中的Operation可以关联工作中心、设备和操作人员等资源。

5. **PBOM到MBOM的转换**

PBOM到MBOM的转换是将工艺规划转化为生产执行的关键步骤：
   - 将ProcessStep转换为Operation
   - 关联具体生产资源
   - 保持零部件引用关系
   - 调整生产线平衡

特点：
- 目标对象包括Operation和Part
- Operation节点可以引用资源
- Part节点通常引用自EBOM
- 支持从PBOM转换生成
- 更加注重生产执行和资源配置

## 3. BOM集成场景

### 3.1 多BOM协同

```mermaid
graph TD
    subgraph 多BOM协同
        A[CAD BOM] --> B[EBOM]
        B --> D[PBOM]
        D --> C[MBOM]
        B --> E[SBOM]
        
        F[物料管理] --> B
        G[生产管理] --> C
        H[工艺管理] --> D
        I[服务管理] --> E
    end
    
    style A fill:#f9f,stroke:#333
    style B fill:#bbf,stroke:#333
    style C fill:#bfb,stroke:#333
    style D fill:#fbf,stroke:#333
    style E fill:#fbb,stroke:#333
```

多BOM协同是产品全生命周期管理的关键，不同类型的BOM支持不同业务阶段：

- CAD BOM提供设计数据
- EBOM支持产品定义和物料管理
- PBOM支持工艺规划和工艺管理
- MBOM支持生产执行和生产管理
- SBOM支持服务维护和服务管理

这些BOM之间存在清晰的转换关系和数据流动，共同构成产品数据的完整链条。

### 3.2 BOM转换流程

BOM转换是产品从设计到制造的关键流程，每个阶段的BOM都有特定的关注点和用途：

1. **CAD BOM → EBOM**: 从设计视图转换为产品结构视图
   - 转换CAD组件为标准Part对象
   - 清理非物理节点和设计辅助对象
   - 添加物料编码和属性信息
   - 关联技术文档和规格要求

2. **EBOM → PBOM**: 从产品结构视图转换为工艺规划视图
   - 基于产品结构创建工艺路线
   - 分解装配过程为工序步骤
   - 添加工艺要求和参数
   - 关联工艺文档和资源需求

3. **PBOM → MBOM**: 从工艺规划视图转换为生产执行视图
   - 将工序转换为生产操作
   - 关联具体生产资源
   - 优化生产顺序和批次
   - 添加质量控制节点

```mermaid
sequenceDiagram
    participant CAD as CAD BOM
    participant EBOM as EBOM
    participant PROC as PBOM
    participant MBOM as MBOM
    participant RES as 资源管理
    
    rect rgb(180, 200, 230)
        Note over CAD,EBOM: 设计阶段
        CAD->>CAD: 3D设计
        CAD->>EBOM: 结构转换
        EBOM->>EBOM: 产品定义
    end
    
    rect rgb(200, 220, 250)
        Note over EBOM,PROC: 工艺准备阶段
        EBOM->>PROC: 提供产品结构
        PROC->>PROC: 工艺规划
    end
    
    rect rgb(220, 250, 220)
        Note over PROC,MBOM: 生产转换阶段
        PROC->>MBOM: 创建工序结构
        MBOM->>MBOM: 分解制造步骤
        MBOM->>RES: 关联制造资源
    end
    
    rect rgb(250, 220, 220)
        Note over MBOM,RES: 验证阶段
        MBOM->>PROC: 工艺评审
        PROC->>EBOM: 设计反馈
        MBOM->>RES: 资源确认
    end

    Note over CAD,RES: 保持全链条双向追溯关系
```

整个转换过程保持全链条的双向追溯关系，确保设计变更能够有效传递到制造环节，同时制造反馈也能回流到设计阶段。

### 3.3 BOM冻结与快照

为了支持产品生命周期管理中的各种里程碑和历史回溯需求，BOM系统提供了完善的冻结与快照机制：

1. **BOM快照**
   - 定义：在特定时间点创建的BOM完整副本
   - 不变性：快照创建后内容不可修改
   - 存储方式：高效存储差异化数据，避免重复

2. **里程碑管理**
   - 发布里程碑：产品正式发布时的BOM冻结
   - 审核里程碑：设计审核或评审时的临时冻结
   - 生产里程碑：批量生产前的制造BOM冻结

3. **基线管理**
   - 产品基线：代表产品特定状态的BOM集合
   - 比较功能：支持不同基线间的差异比较
   - 派生功能：支持从现有基线派生新版本

```mermaid
graph TD
    subgraph BOM快照与基线
        T1[时间轴] --> S1[快照1<br>设计评审]
        T1 --> S2[快照2<br>样机验证]
        T1 --> S3[快照3<br>量产基线]
        
        S1 -.-> D1[锁定数据集1]
        S2 -.-> D2[锁定数据集2]
        S3 -.-> D3[锁定数据集3]
        
        D1 --- R1[只读参考]
        D2 --- R2[只读参考]
        D3 --- R3[只读参考]
        
        R3 --> D4[量产变更管理]
    end
    
    style T1 fill:#f9f,stroke:#333
    style S1 fill:#bbf,stroke:#333
    style S2 fill:#bbf,stroke:#333
    style S3 fill:#bbf,stroke:#333
    style D3 fill:#bfb,stroke:#333
    style D4 fill:#fbb,stroke:#333
```

4. **实现机制**
   - 时间点定义：基于时间点的有效性标记
   - 修订锁定：特定修订版本的固化
   - 配置规则：保存BOM配置规则的状态

### 3.4 BOM自动转换及责信度检查

BOM在不同阶段的转换通常涉及复杂的业务逻辑，系统提供了自动转换和质量控制机制：

1. **自动转换规则引擎**
   - 基于模板：预定义的转换模板和规则
   - 智能匹配：基于模式匹配的自动转换
   - 业务规则：可配置的业务规则和转换逻辑

2. **责信度检查**
   - 完整性检查：确保所有必要数据都已转换
   - 一致性检查：确保转换后数据的业务一致性
   - 合规性检查：确保符合行业标准和规范

3. **问题处理流程**
   - 异常标记：自动标记存在问题的转换结果
   - 人工干预：支持特定场景的人工干预和修正
   - 审批流程：重要变更的多级审批机制

```mermaid
flowchart TD
    E[EBOM] --> |转换规则| TR{转换规则引擎}
    TR --> |自动转换| M[MBOM初稿]
    M --> |责信度检查| CC{检查结果}
    
    CC -->|通过| MA[MBOM审批]
    CC -->|警告| MR[人工审核]
    CC -->|错误| MF[修正反馈]
    
    MR --> MA
    MF --> TR
    
    subgraph 责信度检查项
        C1[完整性]
        C2[一致性]
        C3[合规性]
        C4[资源可用性]
    end
    
    CC -.-> C1
    CC -.-> C2
    CC -.-> C3
    CC -.-> C4
    
    style E fill:#f9f,stroke:#333
    style TR fill:#bbf,stroke:#333
    style M fill:#bfb,stroke:#333
    style CC fill:#fbb,stroke:#333
```

4. **关键实施点**
   - 转换映射配置：灵活配置源与目标的映射关系
   - 质量门控：设定质量阈值和必要条件
   - 转换历史记录：记录所有转换活动和决策
   
## 4. 实现新的BOM类型：SBOM案例

服务BOM（Service BOM，SBOM）用于管理产品的服务和维护结构。下面展示如何实现这个新的BOM类型。

### 4.1 领域模型定义
//TBD

### 4.2 创建SBOM结构

//TBD

### 4.3 SBOM的查询和使用

//TBD

### 4.4 与其他BOM的集成

//TBD

## 5. BOM系统扩展点

### 5.1 版本规则扩展

```java
// 1. 自定义版本规则
public class SbomRevisionRule extends RevisionRule {
    @Override
    public ModelObject resolveRevision(ModelObject original) {
        if (original instanceof ServiceTask) {
            // 应用特定的版本解析逻辑
            return resolveServiceTaskRevision((ServiceTask)original);
        }
        return super.resolveRevision(original);
    }
}

```

### 5.2 BOM转换规则扩展
//TBD

### 5.2 计算扩展
////TBD

## 6. 总结

EMOP的BOM系统提供了灵活的树形结构管理能力，通过BomView和BomLine的基础设施，可以构建各种类型的BOM应用。在开发新的BOM类型时，需要：

1. 清晰定义业务模型
2. 合理组织对象关系
3. 实现必要的转换规则
4. 注意性能和数据一致性

理解和运用好这些概念，可以帮助我们构建强大的产品数据管理系统。