package io.emop.example.cad.extension;

import io.emop.cad.api.extension.CadPropertyProcessor;
import io.emop.cad.model.ItemEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 属性处理扩展示例
 * 演示如何定制属性转换和验证逻辑
 */
@Slf4j
@Component
public class CustomPropertyProcessor implements CadPropertyProcessor {
    
    @Override
    public void processProperties(ItemEntity entity, Map<String, Object> props) {
        log.debug("=== 定制化：处理属性 ===");
        log.debug("ItemCode: {}, 属性数量: {}", entity.getItemCode(), props.size());
        
        // 示例1：属性值转换
        // 例如：将英寸转换为毫米
        if (props.containsKey("length")) {
            Object length = props.get("length");
            if (length instanceof Number) {
                // 假设CAD中是英寸，转换为毫米
                double lengthInMm = ((Number) length).doubleValue() * 25.4;
                props.put("length", lengthInMm);
                log.debug("长度单位转换: {} inch -> {} mm", length, lengthInMm);
            }
        }
        
        // 示例2：计算派生属性
        // 例如：根据材料和重量计算成本
        if (props.containsKey("material") && props.containsKey("weight")) {
            String material = (String) props.get("material");
            Number weight = (Number) props.get("weight");
            // 这里可以调用成本计算服务
            double estimatedCost = calculateCost(material, weight.doubleValue());
            props.put("estimatedCost", estimatedCost);
            log.debug("计算成本: {} {} kg -> {} 元", material, weight, estimatedCost);
        }
        
        // 示例3：设置默认值
        props.putIfAbsent("department", "工程部");
        props.putIfAbsent("projectCode", "DEFAULT");
    }
    
    @Override
    public void validateProperties(ItemEntity entity, Map<String, Object> props) {
        log.debug("=== 定制化：验证属性 ===");
        
        // 示例：必填字段验证
        if (!props.containsKey("material") || props.get("material") == null) {
            throw new IllegalArgumentException(
                String.format("ItemCode [%s] 缺少必填属性: material", entity.getItemCode())
            );
        }
        
        // 示例：属性值范围验证
        if (props.containsKey("weight")) {
            Number weight = (Number) props.get("weight");
            if (weight.doubleValue() <= 0) {
                throw new IllegalArgumentException(
                    String.format("ItemCode [%s] 的重量必须大于0", entity.getItemCode())
                );
            }
        }
        
        // 示例：业务规则验证
        // 例如：特定材料必须有特定的处理工艺
        String material = (String) props.get("material");
        if ("不锈钢".equals(material) && !props.containsKey("surfaceTreatment")) {
            log.warn("ItemCode [{}] 使用不锈钢材料但未指定表面处理工艺", entity.getItemCode());
        }
    }
    
    /**
     * 成本计算示例方法
     */
    private double calculateCost(String material, double weight) {
        // 这里是简化的示例，实际应该调用成本计算服务
        double pricePerKg = switch (material) {
            case "铝合金" -> 30.0;
            case "不锈钢" -> 50.0;
            case "碳钢" -> 20.0;
            default -> 25.0;
        };
        return weight * pricePerKg;
    }
    
    @Override
    public int getOrder() {
        return 10;
    }
}
