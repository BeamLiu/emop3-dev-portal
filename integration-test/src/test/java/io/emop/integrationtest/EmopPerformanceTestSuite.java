package io.emop.integrationtest;

import io.emop.service.registry.ServiceRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.TestInstance;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * 性能测试套件
 * 自动扫描 performance 包下的所有集成测试
 */
@Suite
@SuiteDisplayName("EMOP 性能测试套件")
@SelectPackages({
        "io.emop.integrationtest.performance",
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
public class EmopPerformanceTestSuite implements LauncherSessionListener {

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        suiteSetup();
    }

    private void suiteSetup() {
        // 设置发现SPI
        ServiceRegistry.initAllServices();
        log.info("=== 开始 EMOP 性能测试套件初始化 ===");
        log.info("自动扫描以下包的性能测试:");
        log.info("  - io.emop.integrationtest.performance.*");
        log.info("=== EMOP 性能测试套件初始化完成 ===");
    }
}