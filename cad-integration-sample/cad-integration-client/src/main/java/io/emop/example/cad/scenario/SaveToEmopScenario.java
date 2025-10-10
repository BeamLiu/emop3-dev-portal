package io.emop.example.cad.scenario;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.emop.example.cad.model.ItemEntity;
import io.emop.example.cad.model.PostItemEntityResponse;
import io.emop.example.cad.service.*;
import io.emop.example.cad.util.Utils;
import io.emop.model.query.tuple.Tuple2;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/**
 * 保存到EMOP场景
 * 演示将CAD模型保存到EMOP系统的完整流程
 */
@Slf4j
public class SaveToEmopScenario {

    private final CadApiService cadApiService = new CadApiService();
    private final FileStorageService fileStorageService = new FileStorageService();
    private final ZipReorganizer zipReorganizer = new ZipReorganizer();
    private final IdMappingService idMappingService = new IdMappingService();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String execute() {
        try {
            log.info("=== 开始保存到EMOP流程 ===");

            // 步骤1: 加载测试数据
            log.info("\n步骤1: 加载BOM测试数据");
            InputStream testDataStream = getClass().getClassLoader()
                    .getResourceAsStream("test-data/1_compare_request.json");
            if (testDataStream == null) {
                throw new RuntimeException("无法找到测试数据文件: test-data/1_compare_request.json");
            }
            List<ItemEntity> itemEntities = objectMapper.readValue(
                    testDataStream,
                    new TypeReference<List<ItemEntity>>() {
                    });
            log.info("加载了 {} 个ItemEntity", itemEntities.size());

            // 步骤2: 调用Compare API
            log.info("\n步骤2: 调用Compare API进行BOM比对");
            List<ItemEntity> comparedEntities = cadApiService.compareItemEntity(itemEntities);
            log.info("Compare完成，返回 {} 个ItemEntity", comparedEntities.size());

            // 步骤3: 提交BOM结构到EMOP（创建Item、Component和File对象）
            log.info("\n步骤3: 提交BOM结构到EMOP");
            PostItemEntityResponse postResponse = cadApiService.postItemEntity(comparedEntities);
            log.info("BOM结构提交成功，File对象已创建");

            // 步骤4: 从Post响应中提取最终的文件ID映射
            log.info("\n步骤4: 从Post响应中提取最终的文件ID映射");
            Map<String, Long> rawFileIdMapping = idMappingService.extractFileIdMapping(postResponse.getItemEntities());
            log.info("提取到 {} 个文件ID映射", rawFileIdMapping.size());

            // 步骤5: 重组ZIP文件并上传
            log.info("\n步骤5: 重组ZIP文件并上传");
            InputStream zipStream = getClass().getClassLoader()
                    .getResourceAsStream("test-data/3_1_creo-upload17453228522993597702.zip");
            // 将 classpath 中的 ZIP 复制到临时文件
            File originalZip = File.createTempFile("cad-upload-", ".zip");
            Files.copy(zipStream, originalZip.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            zipStream.close();
            Tuple2<File, Map<String, Long>> tuple2 = zipReorganizer.reorganizeZip(originalZip, rawFileIdMapping);
            File reorganizedZip = tuple2.first();
            String fileMetadataConfig = zipReorganizer.generateFileMetadataConfig(tuple2.second());
            log.info("ZIP文件重组完成: {}", reorganizedZip.getAbsolutePath());

            // 步骤6: 批量上传ZIP文件（UPDATE_ONLY）
            log.info("\n步骤6: 批量上传ZIP文件更新File对象（UPDATE_ONLY）");
            log.info("File Metadata Config: {}", Utils.previewString(fileMetadataConfig));
            fileStorageService.bulkUploadZip(
                    reorganizedZip,
                    "cad",
                    "demo/cad-integration-client",
                    "UPDATE_ONLY",
                    fileMetadataConfig);
            log.info("文件上传成功，File对象已更新");

            // 步骤7: 提交轻量化转图任务（针对零件或装配，用于界面查看）
            log.info("\n步骤7: 提交轻量化转图任务");
            
            String conversionJobId = cadApiService.submitConversionJob(postResponse.getComponentId());
            log.info("轻量化转图任务已提交，JobId: {}", conversionJobId);
            log.info("可通过 http://localhost:861/dashboard/jobs/{} 查看转图进度", conversionJobId);
            log.info("转换后的文件将存储在MinIO的 cad:cad/demo/cad-integration-client/{{fileId}}/converted/ 子目录下");

            // 清理临时文件
            reorganizedZip.delete();
            originalZip.delete();

            log.info("\n=== 保存到EMOP流程完成 ===");

            // 返回根Item的ItemCode
            return postResponse.getItemEntities().stream()
                    .filter(e -> e.getRoot() != null && e.getRoot())
                    .map(e -> e.getItemCode())
                    .findFirst().orElseThrow();

        } catch (Exception e) {
            log.error("保存到EMOP失败", e);
            throw new RuntimeException(e);
        }
    }
}
