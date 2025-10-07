package io.emop.example.cad.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 子组件引用（对应服务器端的ItemEntityBOMLine）
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChildReference {
    private String filename;  // 对应模型的名称
    private String aieOccId;  // AIE_OCC_ID，用来区分同层级下的相同Item对象
    private String transform;  // 位置矩阵
    private String quantity;  // 数量
    private Boolean ignore;  // 客户端使用，标记是否忽略
}
