/**
 * CEF Bridge 模块
 * 实现同步和异步的 CEF 桥接方法
 * 依赖: window.MockData, window.TaskManager
 */

(function () {
    'use strict';

    // 防止重复初始化
    if (window.cefBridge) {
        console.log('[CefBridge] Already initialized, skipping...');
        return;
    }

    // 同步桥接方法
    window.cefBridge = {
        loadFlatBom: function (props) {
            console.log('[CefBridge] loadFlatBom called with props:', props);
            const response = {
                status: { error: false, message: '' },
                data: window.MockData.mockBomData,
                infos: []
            };
            const taskId = window.TaskManager.createTask('COMPLETED', JSON.stringify(response));
            console.log('[CefBridge] loadFlatBom returning taskId:', taskId);
            return taskId;
        },

        loadCurrentBOMTreeFormSld: function (props) {
            console.log('[Mock Bridge] loadCurrentBOMTreeFormSld called', props);
            return this.loadFlatBom(props);
        },

        save: function () {
            console.log('[Mock Bridge] save called');
            const response = {
                status: { error: false, message: '保存成功' },
                data: null,
                infos: []
            };
            return JSON.stringify(response);
        },

        getActiveModel: function () {
            console.log('[Mock Bridge] getActiveModel called');
            const response = {
                status: { error: false, message: '' },
                data: { file: 'ASM001.SLDASM' },
                infos: []
            };
            return JSON.stringify(response);
        },

        getCurrentWorkSpaceDir: function () {
            console.log('[Mock Bridge] getCurrentWorkSpaceDir called');
            return 'C:\\\\workspace\\\\';
        },

        fillModelFileChecksum: function (itemEntitiesJson) {
            console.log('[Mock Bridge] fillModelFileChecksum called');
            const itemEntities = JSON.parse(itemEntitiesJson);
            itemEntities.forEach(item => {
                if (item.modelFile) {
                    item.modelFile.checksum = 'mock_checksum_' + Math.random().toString(36).substr(2, 9);
                }
            });
            return JSON.stringify(itemEntities);
        },

        downloadModelFiles: function (url, savePath, insert, itemEntityJson) {
            console.log('[Mock Bridge] downloadModelFiles called', url, savePath, insert);
            const taskId = window.TaskManager.createTask('COMPLETED', 'C:\\workspace\\');
            console.log('[Mock Bridge] downloadModelFiles completed immediately for task:', taskId);
            return taskId;
        },

        uploadFilesByResolveItemEntityTree: function (url, itemEntityJson, imageSubDir) {
            console.log('[Mock Bridge] uploadFilesByResolveItemEntityTree called', url);
            return this.uploadFilesByResolveItemEntityTree2(url, itemEntityJson, imageSubDir, false, false);
        },

        uploadFilesByResolveItemEntityTree2: function (url, itemEntityJson, imageSubDir, syncPdf, syncDwg) {
            console.log('[Mock Bridge] uploadFilesByResolveItemEntityTree2 called', url, syncPdf, syncDwg);
            const response = {
                status: { error: false, message: '上传成功' },
                data: null,
                infos: []
            };
            const taskId = window.TaskManager.createTask('COMPLETED', JSON.stringify(response));
            console.log('[Mock Bridge] uploadFilesByResolveItemEntityTree2 completed immediately for task:', taskId);
            return taskId;
        },

        httpJsonPost: function (url, jsonBody) {
            console.log('[Mock Bridge] httpJsonPost called', url);
            const response = { status: 'OK', content: {} };
            const taskId = window.TaskManager.createTask('COMPLETED', JSON.stringify(response));
            console.log('[Mock Bridge] httpJsonPost completed immediately for task:', taskId);
            return taskId;
        },

        httpGet: function (url) {
            console.log('[Mock Bridge] httpGet called', url);
            const response = { status: 'OK', content: {} };
            const taskId = window.TaskManager.createTask('COMPLETED', JSON.stringify(response));
            console.log('[Mock Bridge] httpGet completed immediately for task:', taskId);
            return taskId;
        },

        getTaskResponse: function (taskId) {
            console.log('[Mock Bridge] getTaskResponse called', taskId);
            const task = window.TaskManager.getTask(taskId);
            return JSON.stringify(task);
        },

        sendStatusBarText: function (content) {
            console.log('[Mock Bridge] sendStatusBarText:', content);
            const response = {
                status: { error: false, message: '' },
                data: null,
                infos: []
            };
            return JSON.stringify(response);
        },

        updateTaskBarProgress: function (progress, state) {
            console.log('[Mock Bridge] updateTaskBarProgress:', progress, state);
        },

        resetTaskBarProgress: function () {
            console.log('[Mock Bridge] resetTaskBarProgress');
        },

        generateJtOnClient: function () {
            return false;
        }
    };

    // 异步桥接方法
    window.asyncCefBridge = {
        openInNewWindow: async function (fullpath) {
            console.log('[Mock Bridge] openInNewWindow called', fullpath);
            await window.TaskManager.delay(500);
            const response = {
                status: { error: false, message: '打开成功' },
                data: { file: fullpath },
                infos: []
            };
            return JSON.stringify(response);
        },

        insertInAssembly: async function (fullpath) {
            console.log('[Mock Bridge] insertInAssembly called', fullpath);
            await window.TaskManager.delay(500);
            const response = {
                status: { error: false, message: '插入成功' },
                data: { file: fullpath },
                infos: []
            };
            return JSON.stringify(response);
        },

        getPreviews: async function (models, subDir) {
            console.log('[Mock Bridge] getPreviews called', models, subDir);
            await window.TaskManager.delay(1000);
            const response = {
                status: { error: false, message: '生成预览图成功' },
                data: null,
                infos: []
            };
            return JSON.stringify(response);
        },

        generateSTEP: async function (models, subDir) {
            console.log('[Mock Bridge] generateSTEP called', models, subDir);
            await window.TaskManager.delay(2000);
            const response = {
                status: { error: false, message: '生成STEP成功' },
                data: null,
                infos: []
            };
            return JSON.stringify(response);
        },

        generateJT: async function (models, subDir) {
            console.log('[Mock Bridge] generateJT called', models, subDir);
            await window.TaskManager.delay(2000);
            const response = {
                status: { error: false, message: '生成JT成功' },
                data: null,
                infos: []
            };
            return JSON.stringify(response);
        },

        getPdf: async function (models, subDir) {
            console.log('[Mock Bridge] getPdf called', models, subDir);
            await window.TaskManager.delay(1500);
            const response = {
                status: { error: false, message: '生成PDF成功' },
                data: null,
                infos: []
            };
            return JSON.stringify(response);
        },

        getDwg: async function (models, subDir) {
            console.log('[Mock Bridge] getDwg called', models, subDir);
            await window.TaskManager.delay(1500);
            const response = {
                status: { error: false, message: '生成DWG成功' },
                data: null,
                infos: []
            };
            return JSON.stringify(response);
        },

        updateProps: async function (itemEntityJson) {
            console.log('[Mock Bridge] updateProps called');
            await window.TaskManager.delay(500);
            const response = {
                status: { error: false, message: '更新属性成功' },
                data: null,
                infos: []
            };
            return JSON.stringify(response);
        },

        rebuildBom: async function () {
            console.log('[Mock Bridge] rebuildBom called');
            await window.TaskManager.delay(1000);
            const response = {
                status: { error: false, message: '重建BOM成功' },
                data: null,
                infos: []
            };
            return JSON.stringify(response);
        }
    };

    console.log('[CefBridge] Module loaded');
})();
