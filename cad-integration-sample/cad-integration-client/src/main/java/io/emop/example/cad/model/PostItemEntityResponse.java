package io.emop.example.cad.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

/**
 * Post BOM响应
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PostItemEntityResponse {
    private Long componentId;
    private List<String> warnings;
    private List<ItemEntity> itemEntities;
}
