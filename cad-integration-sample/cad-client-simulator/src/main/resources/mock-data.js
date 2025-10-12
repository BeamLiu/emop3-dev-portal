/**
 * Mock 数据模块
 * 定义所有模拟数据，通过 window.MockData 暴露
 */

(function() {
    'use strict';

    // 防止重复初始化
    if (window.MockData) {
        console.log('[MockData] Already initialized, skipping...');
        return;
    }

    // 当前用户信息
    const mockUser = {
        userName: 'mockuser',
        userId: 'mockuser',
        email: 'mockuser@example.com',
        groups: ['Engineering', 'Design']
    };

    // Mock 配置数据
    const mockConfig = {
        enableEmopLogin: false,
        clientType: 'SOLIDWORKS',
        keepAllCreateItems: false,
        openCreateItemLink: false,
        asmItemTypes: [
            {
                type: 'Design',
                name: '装配A',
                link: '/material-management/#/cad/add/JM8_PartRevision',
                query: '/material/#/search'
            },
            {
                type: 'Design',
                name: '装配B',
                link: '/material-management/#/cad/add/JM8_PartRevision'
            }
        ],
        prtItemTypes: [
            {
                type: 'Design',
                name: '零件A',
                link: '/material-management/#/cad/add/JM8_PartRevision'
            },
            {
                type: 'Design',
                name: '零件B',
                link: '/material-management/#/cad/add/JM8_PartRevision',
                query: '/material/#/search'
            }
        ],
        autoCreateTcItem: true,
        showFileSyncOptions: true,
        syncPdfFile: true,
        syncDwgFile: true,
        showSyncPropertiesOption: true,
        showRebuildBomOption: true,
        showDownloadReleasedVersionOption: true,
        showOpenRefDrawingOption: true,
        defaultTreeWhenBomLessThan: 300,
        onlyTableWhenBomGreaterThan: 2000,
        clientImageSubDir: 'output\\thumbnail',
        clientStepSubDir: 'output\\step',
        clientJtSubDir: 'output\\jt',
        clientPDFSubDir: 'output\\pdf',
        clientDWGSubDir: 'output\\dwg',
        primaryKey: 'ITEM_ID',
        selectItemsByProps: [],
        selectTempItemsByProps: ['materialCustomType', 'formalCadDrawingNo'],
        propsLoadInSearch: [
            { display: '名称', propName: 'object_name' },
            { display: '版本', propName: 'current_revision_id' },
            { display: '所有者', propName: 'owning_user/user_name' },
            { display: '描述', propName: 'object_desc' },
            { display: '图号', propName: 'item_id' },
            { display: '类型', propName: 'object_type' }
        ],
        propsShowInGrid: ['图号', '名称', '所有者', '描述', '数据集@datasetName'],
        propsShowInTable: ['图号', '名称', '版本', '类型', '是否检出@checkedOut', '发布状态@releasedStatus', '所有者', '描述', '数据集@datasetName'],
        syncProps: [
            {
                display: '图号',
                type: 'String',
                propName: 'item_id',
                strategy: 'TC_2_CAD',
                primaryKey: true,
                enabled: true
            },
            {
                display: '名称',
                type: 'String',
                propName: 'object_name',
                strategy: 'TWO_WAY',
                enabled: true
            },
            {
                display: '描述',
                type: 'String',
                propName: 'object_desc',
                strategy: 'TWO_WAY',
                enabled: true
            }
        ],
        classificationConfig: [],
        fileEntityType2DatasetObjectTypeMap: {
            PRT: 'ProPrt',
            ASM: 'ProAsm',
            DRW: 'ProDrw',
            PDF: 'PDF',
            DWG: 'B8ZWCAD',
            STEP: 'STEP',
            JT: 'JT'
        },
        fileEntityType2DatasetRefTypeMap: {
            PRT: 'PrtFile',
            ASM: 'AsmFile',
            DRW: 'DrwFile',
            PDF: 'PDF_Reference',
            DWG: 'B8ZWCAD',
            JPEG: 'JPEG',
            STEP: 'STEP',
            JT: 'JT'
        },
        batchCreateMaterialUrl: '/material/#/batch-add',
        advancedMaterialSearchUrl: '/material/#/search',
        batchChangeWorkStatueUrl: '/material/#/batch-material-translate-formal',
        singleChangeWorkStatueUrl: '/material/#/edit?id={revisionUid}&status=special-edit',
        profiles: 'default,standalone'
    };

    // 模拟的 BOM 数据
    const mockBomData = {
        flatBom: [
            {
                root: true,
                modelFile: {
                    name: 'ASM001.SLDASM',
                    path: 'C:\\workspace\\'
                },
                props: {
                    'ITEM_ID': '100001',
                    'ITEM_NAME': '测试装配体',
                    'DESCRIPTION': '这是一个测试装配体'
                },
                children: [
                    {
                        filename: 'PRT001.SLDPRT',
                        quantity: 2,
                        transform: 'identity'
                    },
                    {
                        filename: 'PRT002.SLDPRT',
                        quantity: 1,
                        transform: 'identity'
                    }
                ],
                drwFiles: [],
                familyInstances: []
            },
            {
                root: false,
                modelFile: {
                    name: 'PRT001.SLDPRT',
                    path: 'C:\\workspace\\'
                },
                props: {
                    'ITEM_ID': '100002',
                    'ITEM_NAME': '测试零件1',
                    'DESCRIPTION': '这是测试零件1'
                },
                children: [],
                drwFiles: [],
                familyInstances: []
            },
            {
                root: false,
                modelFile: {
                    name: 'PRT002.SLDPRT',
                    path: 'C:\\workspace\\'
                },
                props: {
                    'ITEM_ID': '100003',
                    'ITEM_NAME': '测试零件2',
                    'DESCRIPTION': '这是测试零件2'
                },
                children: [],
                drwFiles: [],
                familyInstances: []
            }
        ]
    };

    // 暴露到 window
    window.MockData = {
        mockUser: mockUser,
        mockConfig: mockConfig,
        mockBomData: mockBomData
    };

    console.log('[MockData] Module loaded');
})();
