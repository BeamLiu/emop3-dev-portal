package io.emop.example.filestorage;

import io.emop.example.filestorage.usecase.AttachmentFileDemo;
import io.emop.example.filestorage.usecase.BasicUploadDownloadDemo;
import io.emop.example.filestorage.usecase.BatchOperationsDemo;
import io.emop.example.filestorage.usecase.MultiSiteDemo;
import io.emop.service.registry.ServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EMOP文件存储演示 - 重构版本
 * <p>
 * 本演示项目展示了EMOP平台文件存储系统的四个核心使用场景：
 * 1. 基础上传下载 - 基本的文件上传和下载操作
 * 2. 批量操作 - 批量文件处理和ZIP操作
 * 3. 附件文件管理 - 缩略图、校验文件等附件操作
 * 4. 异地卷支持 - 多站点架构下的站点选择和文件操作
 * <p>
 * 每个场景都在独立的演示类中实现，提供清晰的示例代码和详细的日志输出。
 */
public class FileStorageClientDemo {

    private static final Logger logger = LoggerFactory.getLogger(FileStorageClientDemo.class);

    private final BasicUploadDownloadDemo basicDemo;
    private final BatchOperationsDemo batchDemo;
    private final AttachmentFileDemo attachmentDemo;
    private final MultiSiteDemo multiSiteDemo;

    public FileStorageClientDemo() {
        this.basicDemo = new BasicUploadDownloadDemo();
        this.batchDemo = new BatchOperationsDemo();
        this.attachmentDemo = new AttachmentFileDemo();
        this.multiSiteDemo = new MultiSiteDemo();
    }

    public static void main(String[] args) {
        FileStorageClientDemo demo = new FileStorageClientDemo();

        // 检查是否指定了特定的演示场景
        if (args.length > 0) {
            String scenario = args[0];
            logger.info("=== 运行指定的演示场景: {} ===", scenario);
            demo.runScenario(scenario);
            return;
        }

        // 运行所有演示场景
        logger.info("=== EMOP文件存储系统演示开始 ===");
        logger.info("本演示将展示四个核心文件存储场景的使用方法");
        logger.info("提示: 可以通过参数运行单个场景，如: mvn exec:java -Dexec.args=\"basic\"");

        ServiceRegistry.initAllServices();

        try {
            // 场景1：基础上传下载演示
            logger.info("\n" + "=".repeat(60));
            logger.info("场景1：基础文件上传下载操作");
            logger.info("=".repeat(60));
            demo.basicDemo.runDemo();

            // 场景2：批量操作演示
            logger.info("\n" + "=".repeat(60));
            logger.info("场景2：批量文件操作");
            logger.info("=".repeat(60));
            demo.batchDemo.runDemo();

            // 场景3：附件文件管理演示
            logger.info("\n" + "=".repeat(60));
            logger.info("场景3：附件文件管理");
            logger.info("=".repeat(60));
            demo.attachmentDemo.runDemo();

            // 场景4：异地卷支持演示
            logger.info("\n" + "=".repeat(60));
            logger.info("场景4：异地卷支持（多站点架构）");
            logger.info("=".repeat(60));
            demo.multiSiteDemo.runDemo();

            logger.info("\n" + "=".repeat(60));
            logger.info("所有演示场景执行完成！");
            logger.info("=".repeat(60));

        } catch (Exception e) {
            logger.error("演示过程中发生错误", e);
            System.exit(1);
        }

        logger.info("=== EMOP文件存储系统演示结束 ===");
        System.exit(0);
    }

    /**
     * 运行单个演示场景
     *
     * @param scenarioName 场景名称
     */
    public void runScenario(String scenarioName) {
        ServiceRegistry.initAllServices();

        switch (scenarioName.toLowerCase()) {
            case "basic":
            case "1":
                logger.info("=== 运行基础上传下载演示 ===");
                basicDemo.runDemo();
                break;
            case "batch":
            case "2":
                logger.info("=== 运行批量操作演示 ===");
                batchDemo.runDemo();
                break;
            case "attachment":
            case "3":
                logger.info("=== 运行附件文件演示 ===");
                attachmentDemo.runDemo();
                break;
            case "multiSite":
            case "4":
                logger.info("=== 运行异地卷支持演示 ===");
                multiSiteDemo.runDemo();
                break;
            default:
                logger.error("未知的演示场景: {}. 支持的场景: basic, batch, attachment, multiSite", scenarioName);
                break;
        }
    }
}