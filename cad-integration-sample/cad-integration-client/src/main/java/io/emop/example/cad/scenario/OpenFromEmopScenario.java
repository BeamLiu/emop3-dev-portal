package io.emop.example.cad.scenario;

import io.emop.example.cad.model.ItemEntity;
import io.emop.example.cad.service.*;
import io.emop.model.cad.CADComponent;
import io.emop.model.common.ModelObject;
import io.emop.model.query.Q;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 从EMOP打开场景
 * 演示从EMOP系统打开CAD模型的完整流程
 */
@Slf4j
@RequiredArgsConstructor
public class OpenFromEmopScenario {
    
    private final CadApiService cadApiService = new CadApiService();
    private final FileStorageService fileStorageService = new FileStorageService();
    private final IdMappingService idMappingService = new IdMappingService();
    
    private final String cadComponentCode;

    public void execute() {
        long getTime = 0, downloadTime = 0;
        try {
            log.info("=== 开始从EMOP打开流程 ===");
            
            Long componentId = Q.<ModelObject>objectType("CADIntegrationCust").where("code=?",cadComponentCode).first().getId();
            
            log.info("\n步骤1: 获取BOM结构");
            log.info("ComponentId: {}", componentId);
            
            try {
                long getStartTime = System.currentTimeMillis();
                List<ItemEntity> itemEntities = cadApiService.getItemEntity(componentId);
                getTime = System.currentTimeMillis() - getStartTime;
                log.info("获取到 {} 个ItemEntity", itemEntities.size());
                
                // 步骤2: 收集所有文件ID
                log.info("\n步骤2: 收集所有文件ID");
                List<Long> fileIds = new ArrayList<>();
                for (ItemEntity entity : itemEntities) {
                    idMappingService.collectFileIds(entity, fileIds);
                }
                log.info("收集到 {} 个文件ID", fileIds.size());
                
                if (!fileIds.isEmpty()) {
                    // 步骤3: 批量下载文件
                    log.info("\n步骤3: 批量下载CAD文件");
                    long downloadStartTime = System.currentTimeMillis();
                    byte[] zipContent = fileStorageService.bulkDownloadByIds(
                        fileIds, 
                        "cad-model-download.zip"
                    );
                    downloadTime = System.currentTimeMillis() - downloadStartTime;
                    log.info("批量下载完成");
                    
                    // 步骤4: 保存到本地
                    log.info("\n步骤4: 保存文件到本地");
                    File outputFile = Paths.get("/tmp/cad-model-download.zip").toFile();
                    Files.write(outputFile.toPath(), zipContent);
                    log.info("文件已保存到: {}", outputFile.getAbsolutePath());
                }
                
                log.info("\n=== 从EMOP打开流程完成 ===");
                log.info("\n【性能统计】");
                log.info("  获取BOM结构 耗时: {} ms", getTime);
                log.info("  批量下载文件 耗时: {} ms", downloadTime);
                log.info("  服务器交互总耗时: {} ms", (getTime + downloadTime));
                
            } catch (Exception e) {
                log.warn("从EMOP打开演示失败（可能是componentId不存在）: {}", e.getMessage());
                log.info("提示：请先运行保存场景，或使用真实的componentId");
            }
            
        } catch (Exception e) {
            log.error("从EMOP打开失败", e);
            throw new RuntimeException(e);
        }
    }
}
