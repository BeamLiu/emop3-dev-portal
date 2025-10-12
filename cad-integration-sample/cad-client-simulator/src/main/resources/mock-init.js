/**
 * Mock Bridge 初始化脚本
 * 设置 localStorage 中的配置信息
 * 依赖: window.MockData
 */

(function() {
    'use strict';

    // 防止重复初始化
    if (window._mockBridgeInitialized) {
        console.log('[Mock Bridge] Already initialized, skipping...');
        return;
    }
    window._mockBridgeInitialized = true;

    console.log('[Mock Bridge] Initializing localStorage...');

    // 设置 CAD 类型和配置到 localStorage
    localStorage.setItem('CAD_TYPE', 'SOLIDWORKS');
    localStorage.setItem('CAD_CLIENT_TYPE', 'SOLIDWORKS');
    localStorage.setItem('TC_ITEM_PRIMARYKEY', window.MockData.mockConfig.primaryKey);
    localStorage.setItem('TC_ITEM_PROPS', JSON.stringify(window.MockData.mockConfig.syncProps.map(p => p.display)));
    localStorage.setItem('CLIENT_IMAGE_SUB_DIR', window.MockData.mockConfig.clientImageSubDir);
    localStorage.setItem('CLIENT_PDF_SUB_DIR', window.MockData.mockConfig.clientPDFSubDir);
    localStorage.setItem('CLIENT_DWG_SUB_DIR', window.MockData.mockConfig.clientDWGSubDir);
    localStorage.setItem('CLIENT_STEP_SUB_DIR', window.MockData.mockConfig.clientStepSubDir);
    localStorage.setItem('CLIENT_JT_SUB_DIR', window.MockData.mockConfig.clientJtSubDir);
    localStorage.setItem('TC_CURRENT_USERNAME', window.MockData.mockUser.userName);
    localStorage.setItem('TC_CURRENT_USER', window.MockData.mockUser.userName);
    localStorage.setItem('CONFIG_PROFILE', JSON.stringify(window.MockData.mockConfig));
    localStorage.setItem('SYNC_PDF_FILE', window.MockData.mockConfig.syncPdfFile);
    localStorage.setItem('DEFAULT_TREE_WHEN_BOM_LESS_THEN', window.MockData.mockConfig.defaultTreeWhenBomLessThan);
    localStorage.setItem('ONLY_TABLE_WHEN_BOM_GREATER_THEN', window.MockData.mockConfig.onlyTableWhenBomGreaterThan);
    localStorage.setItem('SHOW_DOWNLOAD_RELEASED_VERSION_OPTION', window.MockData.mockConfig.showDownloadReleasedVersionOption);
    localStorage.setItem('SCAN_DRAWING_FILES', 'true');

    // 设置 x-cad-type cookie
    document.cookie = 'x-cad-type=SOLIDWORKS; path=/';
    
    console.log('[Mock Bridge] localStorage initialized');
    console.log('[Mock Bridge] CONFIG_PROFILE:', JSON.parse(localStorage.getItem('CONFIG_PROFILE')));
})();
