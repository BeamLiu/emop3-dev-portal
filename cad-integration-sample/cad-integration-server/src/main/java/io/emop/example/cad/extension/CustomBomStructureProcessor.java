package io.emop.example.cad.extension;

import io.emop.cad.api.extension.CadBomStructureProcessor;
import io.emop.cad.model.ItemEntity;
import io.emop.cad.model.ItemEntityBOMLine;
import io.emop.model.cad.CADComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Iterator;

/**
 * BOM结构处理扩展示例
 * 演示如何定制BOM结构处理逻辑
 */
@Slf4j
@Component
public class CustomBomStructureProcessor implements CadBomStructureProcessor {
    
    @Override
    public void processBomStructure(ItemEntity root) {
        log.info("=== 定制化：处理BOM结构 ===");
        log.info("根节点: {}", root.getItemCode());
        
        // 示例1：过滤虚拟件
        filterVirtualParts(root);
        
        // 示例2：调整BOM层级
        // adjustBomLevel(root);
        
        // 示例3：添加自定义BOM属性
        addCustomBomAttributes(root);
    }
    
    @Override
    public String resolveItemCode(ItemEntity entity) {
        // 示例：自定义ItemCode解析规则
        // 例如：根据文件名生成ItemCode
        
        if (entity.getModelFile() != null) {
            String fileName = entity.getModelFile().getName();
            
            // 如果文件名符合特定规则，提取ItemCode
            // 例如：文件名格式为 "ItemCode_Version.prt"
            if (fileName.matches("^[A-Z0-9]+-\\d+.*")) {
                String itemCode = fileName.split("_")[0];
                log.debug("从文件名解析ItemCode: {} -> {}", fileName, itemCode);
                return itemCode;
            }
        }
        
        // 返回null表示使用默认逻辑
        return null;
    }

    @Override
    public CADComponent resolveCadComponent(ItemEntity entity) {
        // 默认返回通用的CADComponent
        return new CADComponent("sample.CADIntegrationCust");
    }
    
    /**
     * 过滤虚拟件
     * 某些CAD系统中会有虚拟件（不需要制造的零件），需要过滤掉
     */
    private void filterVirtualParts(ItemEntity entity) {
        if (entity.getChildren() == null || entity.getChildren().isEmpty()) {
            return;
        }
        
        Iterator<ItemEntityBOMLine> iterator = entity.getChildren().iterator();
        while (iterator.hasNext()) {
            ItemEntityBOMLine bomLine = iterator.next();
            ItemEntity child = bomLine.getItemEntity();
            
            if (child != null) {
                // 示例：根据属性判断是否为虚拟件
                Object isVirtual = child.getProps().get("isVirtual");
                if (Boolean.TRUE.equals(isVirtual)) {
                    log.info("过滤虚拟件: {}", child.getItemCode());
                    iterator.remove();
                    continue;
                }
                
                // 递归处理子节点
                filterVirtualParts(child);
            }
        }
    }
    
    /**
     * 添加自定义BOM属性
     */
    private void addCustomBomAttributes(ItemEntity entity) {
        if (entity.getChildren() == null || entity.getChildren().isEmpty()) {
            return;
        }
        
        for (ItemEntityBOMLine bomLine : entity.getChildren()) {
            ItemEntity child = bomLine.getItemEntity();
            if (child != null) {
                // 示例：计算BOM层级
                int level = calculateBomLevel(child);
                child.getProps().put("bomLevel", level);
                
                // 示例：设置采购类型
                String purchaseType = determinePurchaseType(child);
                child.getProps().put("purchaseType", purchaseType);
                
                // 递归处理子节点
                addCustomBomAttributes(child);
            }
        }
    }
    
    /**
     * 计算BOM层级
     */
    private int calculateBomLevel(ItemEntity entity) {
        // 简化示例：根据子节点数量判断
        if (entity.getChildren() == null || entity.getChildren().isEmpty()) {
            return 0; // 叶子节点
        }
        
        int maxChildLevel = 0;
        for (ItemEntityBOMLine bomLine : entity.getChildren()) {
            if (bomLine.getItemEntity() != null) {
                int childLevel = calculateBomLevel(bomLine.getItemEntity());
                maxChildLevel = Math.max(maxChildLevel, childLevel);
            }
        }
        return maxChildLevel + 1;
    }
    
    /**
     * 确定采购类型
     */
    private String determinePurchaseType(ItemEntity entity) {
        // 示例：根据属性判断采购类型
        Object material = entity.getProps().get("material");
        
        if (material != null) {
            String materialStr = material.toString();
            if (materialStr.contains("标准件")) {
                return "外购";
            } else if (materialStr.contains("定制")) {
                return "委外加工";
            }
        }
        
        // 默认为自制
        return "自制";
    }
    
    @Override
    public int getOrder() {
        return 10;
    }
}
