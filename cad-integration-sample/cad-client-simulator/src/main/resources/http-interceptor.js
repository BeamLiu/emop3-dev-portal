/**
 * HTTP 拦截器模块
 * 拦截 XMLHttpRequest 和 Fetch API，返回 Mock 数据
 * 依赖: window.MockData
 */

(function () {
    'use strict';

    // 防止重复初始化
    if (window._httpInterceptorInitialized) {
        console.log('[HttpInterceptor] Already initialized, skipping...');
        return;
    }
    window._httpInterceptorInitialized = true;

    function getMockResponse(url, method, body) {
        // 用户信息
        if (url.includes('/api/login/user') || url.includes('/api/user/current') || url.includes('/api/user')) {
            console.log('[Mock Bridge] Returning mock user data');
            return {
                data: { status: 'OK', content: window.MockData.mockUser },
                delay: 100
            };
        }

        // 配置信息
        if (url.includes('/api/config') || url.includes('/api/item/settings')) {
            console.log('[Mock Bridge] Returning mock config');
            const props = window.MockData.mockConfig.syncProps.map(p => p.display);
            const propsMapping = {};
            window.MockData.mockConfig.syncProps.forEach(p => {
                propsMapping[p.propName] = p.display;
            });

            return {
                data: {
                    status: 'OK',
                    content: {
                        props: props,
                        propsMapping: propsMapping,
                        syncPdfFile: window.MockData.mockConfig.syncPdfFile,
                        clientDWGSubDir: window.MockData.mockConfig.clientDWGSubDir,
                        clientImageSubDir: window.MockData.mockConfig.clientImageSubDir,
                        clientPDFSubDir: window.MockData.mockConfig.clientPDFSubDir,
                        showDownloadReleasedVersionOption: window.MockData.mockConfig.showDownloadReleasedVersionOption,
                        scanDrawingFiles: true,
                        defaultTreeWhenBomLessThan: window.MockData.mockConfig.defaultTreeWhenBomLessThan,
                        onlyTableWhenBomGreaterThan: window.MockData.mockConfig.onlyTableWhenBomGreaterThan,
                        configProfile: window.MockData.mockConfig,
                        primaryKey: window.MockData.mockConfig.primaryKey
                    }
                },
                delay: 100
            };
        }

        // 属性配置
        if (url.includes('/api/item/props')) {
            return {
                data: {
                    status: 'OK',
                    content: ['ITEM_ID', 'ITEM_NAME', 'DESCRIPTION', 'MATERIAL', 'WEIGHT']
                },
                delay: 100
            };
        }

        // 搜索请求
        if (url.includes('/api/item/search')) {
            return {
                data: { status: 'OK', content: [] },
                delay: 300
            };
        }

        // 文件夹列表
        if (url.includes('/api/folder/') && url.includes('/files')) {
            return {
                data: { status: 'OK', content: [] },
                delay: 200
            };
        }

        return null;
    }

    function setupXHRInterceptor() {
        const originalXHROpen = XMLHttpRequest.prototype.open;
        const originalXHRSend = XMLHttpRequest.prototype.send;

        XMLHttpRequest.prototype.open = function (method, url, ...args) {
            this._mockUrl = url;
            this._mockMethod = method;
            return originalXHROpen.apply(this, [method, url, ...args]);
        };

        XMLHttpRequest.prototype.send = function (body) {
            const url = this._mockUrl;
            const method = this._mockMethod;

            if (!url.includes('/cad-integration2/api/')) {
                return originalXHRSend.apply(this, arguments);
            }

            console.log(`[Mock Bridge] Intercepting ${method} ${url}`);

            const mockResponse = getMockResponse(url, method, body);

            // 如果返回 null，表示不拦截，让请求正常发送
            if (mockResponse === null) {
                console.log('[Mock Bridge] Not intercepting, passing through:', url);
                return originalXHRSend.apply(this, arguments);
            }

            if (mockResponse) {
                setTimeout(() => {
                    Object.defineProperty(this, 'responseText', {
                        writable: true,
                        value: JSON.stringify(mockResponse.data)
                    });
                    Object.defineProperty(this, 'status', { writable: true, value: 200 });
                    Object.defineProperty(this, 'readyState', { writable: true, value: 4 });
                    this.onreadystatechange && this.onreadystatechange();
                }, mockResponse.delay || 100);
                return;
            }

            console.log('[Mock Bridge] Using default mock response for:', url);
            setTimeout(() => {
                const response = { status: 'OK', content: null };
                Object.defineProperty(this, 'responseText', {
                    writable: true,
                    value: JSON.stringify(response)
                });
                Object.defineProperty(this, 'status', { writable: true, value: 200 });
                Object.defineProperty(this, 'readyState', { writable: true, value: 4 });
                this.onreadystatechange && this.onreadystatechange();
            }, 100);
        };
    }

    function setupFetchInterceptor() {
        const originalFetch = window.fetch;

        window.fetch = function (url, options = {}) {
            if (typeof url === 'string' && url.includes('/cad-integration2/api/')) {
                console.log(`[Mock Bridge] Intercepting fetch ${options.method || 'GET'} ${url}`);

                const mockResponse = getMockResponse(url, options.method || 'GET', options.body);

                // 如果返回 null，表示不拦截，让请求正常发送
                if (mockResponse === null) {
                    console.log('[Mock Bridge] Not intercepting, passing through:', url);
                    return originalFetch.apply(this, arguments);
                }

                return new Promise((resolve) => {
                    const delay = mockResponse?.delay || 100;

                    setTimeout(() => {
                        const responseData = mockResponse?.data || { status: 'OK', content: null };
                        resolve(new Response(JSON.stringify(responseData), {
                            status: 200,
                            headers: { 'Content-Type': 'application/json' }
                        }));
                    }, delay);
                });
            }

            return originalFetch.apply(this, arguments);
        };
    }

    // 初始化拦截器
    setupXHRInterceptor();
    setupFetchInterceptor();

    console.log('[HttpInterceptor] Module loaded');
})();
