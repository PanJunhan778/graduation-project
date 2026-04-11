package com.pjh.server.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BatchDeleteDTO {

    @NotEmpty(message = "请选择要删除的数据")
    private List<Long> ids;
}
