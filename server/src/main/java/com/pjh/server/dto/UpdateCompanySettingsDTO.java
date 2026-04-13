package com.pjh.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateCompanySettingsDTO {

    @NotBlank(message = "公司名称不能为空")
    @Size(max = 100, message = "公司名称不超过100个字符")
    private String name;

    @Size(max = 50, message = "行业不超过50个字符")
    private String industry;

    @Size(max = 20, message = "纳税人性质不超过20个字符")
    private String taxpayerType;

    @Size(max = 500, message = "企业画像不超过500个字符")
    private String description;
}
