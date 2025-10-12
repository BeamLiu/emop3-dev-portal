package io.emop.example.cad.simulator;

import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.handler.CefAppHandlerAdapter;
import org.cef.handler.CefKeyboardHandler;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.misc.BoolRef;
import org.cef.network.CefRequest;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * CAD客户端模拟器 - 使用JBR CEF浏览器访问CAD集成前端
 */
public class CadClientSimulator {

    private static final String DEFAULT_URL = "http://localhost:4200/cad-integration2/";
    private static final String WINDOW_TITLE = "CAD Client Simulator";

    private static CefApp cefApp;
    private static CefClient client;
    private static CefBrowser browser;
    private static JFrame frame;
    private static JTextField addressBar;
    private static CountDownLatch initLatch = new CountDownLatch(1);

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false");

        SwingUtilities.invokeLater(() -> {
            createAndShowGUI();
        });
    }

    private static boolean isCefSupported() {
        try {
            Class.forName("org.cef.CefApp");
            return true;
        } catch (ClassNotFoundException e) {
            System.err.println("JCEF classes not found: " + e.getMessage());
            return false;
        } catch (Exception | UnsatisfiedLinkError e) {
            System.err.println("JCEF不可用: " + e.getMessage());
            return false;
        }
    }

    private static void createAndShowGUI() {
        if (!isCefSupported()) {
            JOptionPane.showMessageDialog(null,
                    "JCEF不可用。请确保您使用的是包含JCEF的JetBrains Runtime (JBR)。\n\n" +
                            "下载地址: https://github.com/JetBrains/JetBrainsRuntime/releases",
                    "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            frame = new JFrame(WINDOW_TITLE + " - 初始化中...");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1400, 900);

            JLabel loadingLabel = new JLabel("正在初始化浏览器引擎...", SwingUtilities.CENTER);
            loadingLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
            frame.add(loadingLabel, BorderLayout.CENTER);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            Thread initThread = new Thread(() -> {
                try {
                    initializeCef();
                } catch (Exception e) {
                    e.printStackTrace();
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(frame,
                                "初始化浏览器时出错: " + e.getMessage(),
                                "错误", JOptionPane.ERROR_MESSAGE);
                    });
                }
            });
            initThread.setDaemon(true);
            initThread.start();

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "创建窗口时出错: " + e.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void initializeCef() throws Exception {
        System.out.println("开始初始化JCEF...");

        CefSettings settings = new CefSettings();
        settings.windowless_rendering_enabled = false;
        settings.remote_debugging_port = 9222;

        CefApp.addAppHandler(new CefAppHandlerAdapter(null) {
            @Override
            public void stateHasChanged(CefApp.CefAppState state) {
                System.out.println("CEF State changed to: " + state);
                if (state == CefApp.CefAppState.INITIALIZED) {
                    System.out.println("CEF已初始化完成");
                    initLatch.countDown();
                }
            }
        });

        System.out.println("启动CEF应用...");
        CefApp.startup(new String[0]);

        cefApp = CefApp.getInstance(settings);
        System.out.println("CEF App实例已创建");

        boolean initialized = initLatch.await(10, TimeUnit.SECONDS);
        if (!initialized) {
            System.out.println("警告: CEF初始化超时，继续尝试...");
        }

        Thread.sleep(1000);

        System.out.println("创建CEF客户端...");
        client = cefApp.createClient();

        CefMessageRouter messageRouter = CefMessageRouter.create();
        client.addMessageRouter(messageRouter);

        client.addKeyboardHandler(new CefKeyboardHandler() {
            @Override
            public boolean onPreKeyEvent(CefBrowser browser, CefKeyEvent keyEvent, BoolRef boolRef) {
                if (keyEvent.type == CefKeyEvent.EventType.KEYEVENT_KEYDOWN ||
                        keyEvent.type == CefKeyEvent.EventType.KEYEVENT_RAWKEYDOWN) {

                    switch (keyEvent.windows_key_code) {
                        case 116: // F5
                            SwingUtilities.invokeLater(() -> {
                                if (CadClientSimulator.browser != null) {
                                    CadClientSimulator.browser.reload();
                                }
                            });
                            return true;

                        case 123: // F12
                            SwingUtilities.invokeLater(() -> openDeveloperTools());
                            return true;
                    }
                }
                return false;
            }

            @Override
            public boolean onKeyEvent(CefBrowser browser, CefKeyEvent keyEvent) {
                return false;
            }
        });

        client.addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadStart(CefBrowser cefBrowser, CefFrame frame, CefRequest.TransitionType transitionType) {
                // 在页面开始加载时就注入 Mock Bridge，确保在其他脚本之前执行
                if (frame.isMain()) {
                    String url = cefBrowser.getURL();
                    if (url.contains("/cad-integration2/")) {
                        System.out.println("页面开始加载，准备注入 Mock Bridge: " + url);
                        // 延迟一点点，确保 DOM 准备好
                        new Thread(() -> {
                            try {
                                Thread.sleep(100);
                                injectMockBridge(cefBrowser);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }
                }
            }

            @Override
            public void onLoadingStateChange(CefBrowser cefBrowser, boolean isLoading,
                                             boolean canGoBack, boolean canGoForward) {
                if (!isLoading) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            if (addressBar != null) {
                                addressBar.setText(cefBrowser.getURL());
                            }
                            // 不再重复注入，避免重置 taskPool
                            // injectMockBridge(cefBrowser);
                        }
                    });
                }
            }
        });

        System.out.println("创建浏览器实例...");
        browser = client.createBrowser(DEFAULT_URL, false, false);

        Component browserUI = null;
        int attempts = 0;
        while (browserUI == null && attempts < 50) {
            try {
                browserUI = browser.getUIComponent();
                if (browserUI != null) {
                    break;
                }
            } catch (Exception e) {
                System.out.println("等待浏览器UI组件准备... 尝试 " + (attempts + 1));
            }
            Thread.sleep(100);
            attempts++;
        }

        if (browserUI == null) {
            throw new RuntimeException("无法获取浏览器UI组件");
        }

        System.out.println("浏览器初始化完成，更新UI...");

        final Component finalBrowserUI = browserUI;
        SwingUtilities.invokeLater(() -> {
            try {
                frame.getContentPane().removeAll();
                frame.setTitle(WINDOW_TITLE);

                createUIComponents(finalBrowserUI);

                frame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        cleanupResources();
                        System.exit(0);
                    }
                });

                frame.revalidate();
                frame.repaint();

                System.out.println("UI更新完成");

            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(frame, "更新UI时出错: " + e.getMessage(),
                        "错误", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private static void cleanupResources() {
        System.out.println("开始清理资源...");
        try {
            if (browser != null) {
                browser.close(true);
                browser = null;
            }
            if (client != null) {
                client.dispose();
                client = null;
            }
            if (cefApp != null) {
                cefApp.dispose();
                cefApp = null;
            }
        } catch (Exception e) {
            System.err.println("清理资源时出错: " + e.getMessage());
        }
        System.out.println("资源清理完成");
    }

    private static void createUIComponents(Component browserUI) {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        JButton backButton = new JButton("⬅️");
        backButton.setToolTipText("后退");
        backButton.addActionListener(e -> {
            if (browser != null && browser.canGoBack()) {
                browser.goBack();
            }
        });

        JButton forwardButton = new JButton("➡️");
        forwardButton.setToolTipText("前进");
        forwardButton.addActionListener(e -> {
            if (browser != null && browser.canGoForward()) {
                browser.goForward();
            }
        });

        JButton refreshButton = new JButton("🔄");
        refreshButton.setToolTipText("刷新 (F5)");
        refreshButton.addActionListener(e -> {
            if (browser != null) {
                browser.reload();
            }
        });

        JButton homeButton = new JButton("🏠");
        homeButton.setToolTipText("主页");
        homeButton.addActionListener(e -> {
            if (browser != null) {
                browser.loadURL(DEFAULT_URL);
            }
        });

        JButton devToolsButton = new JButton("🔧");
        devToolsButton.setToolTipText("开发者工具 (F12)");
        devToolsButton.addActionListener(e -> openDeveloperTools());

        addressBar = new JTextField();
        addressBar.setText(DEFAULT_URL);
        addressBar.addActionListener(e -> {
            String url = addressBar.getText().trim();
            if (!url.isEmpty() && browser != null) {
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "http://" + url;
                }
                browser.loadURL(url);
            }
        });

        toolbar.add(backButton);
        toolbar.add(forwardButton);
        toolbar.add(refreshButton);
        toolbar.add(homeButton);
        toolbar.add(Box.createHorizontalStrut(5));
        toolbar.add(new JLabel("URL: "));
        toolbar.add(addressBar);
        toolbar.add(Box.createHorizontalStrut(5));
        toolbar.add(devToolsButton);

        frame.add(toolbar, BorderLayout.NORTH);
        frame.add(browserUI, BorderLayout.CENTER);

        JMenuBar menuBar = createMenuBar();
        frame.setJMenuBar(menuBar);
    }

    private static JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("文件");
        fileMenu.setMnemonic(KeyEvent.VK_F);

        JMenuItem exitItem = new JMenuItem("退出", KeyEvent.VK_X);
        exitItem.addActionListener(e -> {
            cleanupResources();
            frame.dispose();
            System.exit(0);
        });

        fileMenu.add(exitItem);

        JMenu toolsMenu = new JMenu("工具");
        toolsMenu.setMnemonic(KeyEvent.VK_T);

        JMenuItem refreshItem = new JMenuItem("刷新", KeyEvent.VK_R);
        refreshItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        refreshItem.addActionListener(e -> {
            if (browser != null) {
                browser.reload();
            }
        });

        JMenuItem devToolsItem = new JMenuItem("开发者工具", KeyEvent.VK_D);
        devToolsItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F12, 0));
        devToolsItem.addActionListener(e -> openDeveloperTools());

        toolsMenu.add(refreshItem);
        toolsMenu.add(devToolsItem);

        menuBar.add(fileMenu);
        menuBar.add(toolsMenu);

        return menuBar;
    }

    private static void openDeveloperTools() {
        if (browser != null) {
            try {
                CefBrowser devTools = browser.getDevTools();
                if (devTools != null) {
                    SwingUtilities.invokeLater(() -> {
                        JFrame devToolsFrame = new JFrame("Developer Tools - CAD Client Simulator");
                        devToolsFrame.setSize(1000, 700);
                        devToolsFrame.setLocationRelativeTo(frame);

                        Component devToolsUI = devTools.getUIComponent();
                        devToolsFrame.add(devToolsUI, BorderLayout.CENTER);

                        devToolsFrame.addWindowListener(new WindowAdapter() {
                            @Override
                            public void windowClosing(WindowEvent e) {
                                devTools.close(true);
                                devToolsFrame.dispose();
                            }
                        });

                        devToolsFrame.setVisible(true);
                    });
                } else {
                    System.err.println("无法获取开发者工具实例");
                }
            } catch (Exception e) {
                System.err.println("打开开发者工具失败: " + e.getMessage());
                e.printStackTrace();

                JOptionPane.showMessageDialog(frame,
                        "开发者工具打开失败，可以使用远程调试:\n" +
                                "打开浏览器访问: http://localhost:9222",
                        "开发者工具", JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    /**
     * 注入 Mock Bridge 脚本
     */
    private static void injectMockBridge(CefBrowser browser) {
        try {
            // 只在 CAD 集成页面注入
            String url = browser.getURL();
            if (!url.contains("/cad-integration2/")) {
                return;
            }

            System.out.println("注入 Mock Bridge 到页面: " + url);

            // 按顺序加载多个脚本文件
            String[] scriptFiles = {
                "/mock-data.js",           // 1. 数据定义
                "/task-manager.js",        // 2. 任务管理器
                "/http-interceptor.js",    // 3. HTTP 拦截器
                "/cef-bridge.js",          // 4. CEF 桥接方法
                "/mock-init.js"            // 5. 初始化 localStorage
            };

            for (String scriptFile : scriptFiles) {
                String script = loadScriptFromResource(scriptFile);
                if (script != null) {
                    browser.executeJavaScript(script, url, 0);
                    System.out.println("注入脚本成功: " + scriptFile);
                } else {
                    System.err.println("无法加载脚本: " + scriptFile);
                }
            }

            System.out.println("Mock Bridge 注入完成");
        } catch (Exception e) {
            System.err.println("注入 Mock Bridge 失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 从 classpath 加载脚本文件
     */
    private static String loadScriptFromResource(String resourcePath) {
        try {
            java.io.InputStream is = CadClientSimulator.class.getResourceAsStream(resourcePath);
            if (is != null) {
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();
                return sb.toString();
            }
        } catch (Exception e) {
            System.err.println("加载脚本失败 " + resourcePath + ": " + e.getMessage());
        }
        return null;
    }
}
