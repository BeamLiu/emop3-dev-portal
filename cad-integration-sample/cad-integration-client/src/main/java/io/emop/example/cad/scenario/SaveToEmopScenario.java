package io.emop.example.cad.scenario;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.emop.example.cad.model.FileEntity;
import io.emop.example.cad.model.ItemEntity;
import io.emop.example.cad.model.PostItemEntityResponse;
import io.emop.example.cad.service.*;
import io.emop.example.cad.util.Utils;
import io.emop.model.query.tuple.Tuple2;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

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
        long compareTime = 0, postTime = 0, uploadTime = 0, conversionTime = 0;
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
            itemEntities.forEach(itemEntity -> {
                itemEntity.getProps().put("材质", new Random().nextBoolean() ? "不锈钢" : "塑料");
                itemEntity.getProps().put("重量", new Random().nextFloat(0, 10f));
            });

            // 步骤2: 调用Compare API
            log.info("\n步骤2: 调用Compare API进行BOM比对");
            long compareStartTime = System.currentTimeMillis();
            List<ItemEntity> comparedEntities = cadApiService.compareItemEntity(itemEntities);
            compareTime = System.currentTimeMillis() - compareStartTime;
            log.info("Compare完成，返回 {} 个ItemEntity", comparedEntities.size());

            // 步骤3: 提交BOM结构到EMOP（创建Item、Component和File对象）
            log.info("\n步骤3: 提交BOM结构到EMOP");
            //添加一个drw文件
            comparedEntities.get(0).getDrwFiles().add(new FileEntity());
            comparedEntities.get(0).getDrwFiles().get(0).setName("sample.drw");
            comparedEntities.get(0).getDrwFiles().get(0).setPath("/home/sample/sample.drw");
            long postStartTime = System.currentTimeMillis();
            PostItemEntityResponse postResponse = cadApiService.postItemEntity(comparedEntities);
            postTime = System.currentTimeMillis() - postStartTime;
            log.info("BOM结构提交成功，File对象已创建");

            // 步骤4: 从Post响应中提取最终的文件ID映射
            log.info("\n步骤4: 从Post响应中提取最终的文件ID映射");
            Map<String, Long> rawFileIdMapping = idMappingService.extractFileIdMapping(postResponse.getItemEntities());
            log.info("提取到 {} 个文件ID映射", rawFileIdMapping.size());

            // 步骤5: 重组ZIP文件（包含元数据配置）
            log.info("\n步骤5: 重组ZIP文件（包含元数据配置）");
            InputStream zipStream = getClass().getClassLoader()
                    .getResourceAsStream("test-data/3_1_creo-upload17453228522993597702.zip");
            // 将 classpath 中的 ZIP 复制到临时文件
            File originalZip = File.createTempFile("cad-upload-", ".zip");
            Files.copy(zipStream, originalZip.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            zipStream.close();
            File reorganizedZip = zipReorganizer.reorganizeZip(originalZip, rawFileIdMapping);
            log.info("ZIP文件重组完成（包含 __file_metadata__.json）: {}", reorganizedZip.getAbsolutePath());

            // 步骤6: 批量上传ZIP文件（UPDATE_ONLY）
            log.info("\n步骤6: 批量上传ZIP文件更新File对象（UPDATE_ONLY）");
            log.info("元数据配置已包含在ZIP文件的 __file_metadata__.json 中");
            long uploadStartTime = System.currentTimeMillis();
            fileStorageService.bulkUploadZip(
                    reorganizedZip,
                    "cad",
                    "demo/cad-integration-client",
                    "UPDATE_ONLY");
            uploadTime = System.currentTimeMillis() - uploadStartTime;
            log.info("文件上传成功，File对象已更新");

            // 步骤7: 提交轻量化转图任务（针对零件或装配，用于界面查看）
            log.info("\n步骤7: 提交轻量化转图任务");
            long conversionStartTime = System.currentTimeMillis();
            String conversionJobId = cadApiService.submitConversionJob(postResponse.getComponentId());
            conversionTime = System.currentTimeMillis() - conversionStartTime;
            log.info("轻量化转图任务已提交，JobId: {}", conversionJobId);
            log.info("可通过 http://localhost:861/dashboard/jobs/{} 查看转图进度", conversionJobId);
            log.info("转换后的文件将存储在MinIO的 cad:cad/demo/cad-integration-client/{{fileId}}/converted/ 子目录下");

            // 清理临时文件
            reorganizedZip.delete();
            originalZip.delete();

            log.info("\n=== 保存到EMOP流程完成 ===");
            log.info("\n【性能统计】");
            log.info("  Compare API 耗时: {} ms", compareTime);
            log.info("  提交BOM结构 耗时: {} ms", postTime);
            log.info("  批量上传文件 耗时: {} ms", uploadTime);
            log.info("  提交转图任务 耗时: {} ms", conversionTime);
            log.info("  服务器交互总耗时: {} ms", (compareTime + postTime + uploadTime + conversionTime));

            // 测试扁平化数据场景（无BOM结构）
            testFlatDataScenarios();

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

    private final String dateCode = System.currentTimeMillis() + "";
    
    /**
     * 测试扁平化数据场景（无BOM结构）
     * 场景1: 新建物料
     * 场景2: 同时创建新物料和更新现有物料
     */
    private void testFlatDataScenarios() {
        log.info("\n=== 测试扁平化数据场景（无BOM结构）===");
        
        try {
            // 场景1: 新建物料 - 扁平化数据
            log.info("\n场景1: 新建物料（扁平化数据）");
            ItemEntity newItem = createFlatDataItem("FLAT-NEW-001-" + dateCode, "新建扁平化物料");
            log.info("新建物料成功: {} (ID: {})", newItem.getItemCode(), newItem.getId());
            
            // 场景2: 同时创建新物料和更新现有物料 - 扁平化数据
            log.info("\n场景2: 同时创建新物料和更新现有物料（扁平化数据）");
            List<ItemEntity> mixedItems = createAndUpdateFlatDataItems(newItem.getId(), newItem.getItemCode());
            log.info("批量操作成功，共处理 {} 个物料", mixedItems.size());
            mixedItems.forEach(item -> {
                log.info("  - {} (ID: {}, UseType: {})", 
                    item.getItemCode(), item.getId(), item.getUseType());
            });
            
            log.info("\n=== 扁平化数据场景测试完成 ===");
            
        } catch (Exception e) {
            log.error("扁平化数据场景测试失败", e);
        }
    }
    
    /**
     * 创建扁平化数据物料（无BOM结构）
     */
    @SneakyThrows
    private ItemEntity createFlatDataItem(String code, String name) {
        log.info("创建扁平化物料: code={}, name={}", code, name);
        
        // 创建扁平化的ItemEntity（无父子结构）
        ItemEntity itemEntity = new ItemEntity();
        itemEntity.setItemCode(code);
        itemEntity.setName(name);
        itemEntity.setUseType("CREATE");
        itemEntity.setRoot(false);
        itemEntity.setProps(new HashMap<>());
        
        // 设置属性
        itemEntity.getProps().put("材质", "不锈钢");
        itemEntity.getProps().put("重量", 5.5f);
        
        // 添加modelFile（必需）
        FileEntity modelFile = new FileEntity();
        modelFile.setName(code + ".prt");
        modelFile.setPath("/tmp/flat-data/" + code + ".prt");
        itemEntity.setModelFile(modelFile);
        
        // 注意：不设置children，保持扁平化
        
        List<ItemEntity> entities = List.of(itemEntity);
        
        // 调用保存接口
        PostItemEntityResponse response = cadApiService.postItemEntity(entities);
        
        return response.getItemEntities().get(0);
    }
    
    /**
     * 同时创建新物料和更新现有物料（扁平化数据，无BOM结构）
     */
    @SneakyThrows
    private List<ItemEntity> createAndUpdateFlatDataItems(Long existingId, String existingCode) {
        log.info("同时创建新物料和更新现有物料: existingId={}, existingCode={}", existingId, existingCode);
        
        // 1. 创建新物料的ItemEntity
        ItemEntity newItem = new ItemEntity();
        String newCode = "FLAT-NEW-002-" + dateCode;
        newItem.setItemCode(newCode);
        newItem.setName("批量创建的扁平化物料");
        newItem.setUseType("CREATE");
        newItem.setRoot(false);
        newItem.setProps(new HashMap<>());
        newItem.getProps().put("材质", "塑料");
        newItem.getProps().put("重量", 2.3f);
        newItem.getProps().put("description", "批量操作中创建的新物料");
        
        // 添加modelFile（必需）
        FileEntity newModelFile = new FileEntity();
        newModelFile.setName(newCode + ".prt");
        newModelFile.setPath("/tmp/flat-data/" + newCode + ".prt");
        newItem.setModelFile(newModelFile);
        
        // 2. 更新现有物料的ItemEntity
        ItemEntity updateItem = new ItemEntity();
        updateItem.setId(existingId);
        updateItem.setItemCode(existingCode);
        updateItem.setName("批量更新的扁平化物料");
        updateItem.setUseType("OVERRIDE");
        updateItem.setRoot(false);
        updateItem.setProps(new HashMap<>());
        updateItem.getProps().put("材质", "铝合金");
        updateItem.getProps().put("重量", 3.2f);
        updateItem.getProps().put("description", "批量操作中更新的现有物料");
        
        // 添加modelFile（必需）
        FileEntity updateModelFile = new FileEntity();
        updateModelFile.setName(existingCode + ".prt");
        updateModelFile.setPath("/tmp/flat-data/" + existingCode + ".prt");
        updateItem.setModelFile(updateModelFile);
        
        // 3. 同时提交两个物料（一个创建，一个更新）
        List<ItemEntity> entities = List.of(newItem, updateItem);
        
        // 调用保存接口
        PostItemEntityResponse response = cadApiService.postItemEntity(entities);
        
        return response.getItemEntities();
    }
}
