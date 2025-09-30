package io.emop.integrationtest.dto;

import io.emop.model.annotation.DTOEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@DTOEntity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RevisionCountDTO {

    private String province;

    private Long count;
}
