package com.pjh.server.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateCompanySettingsDTO {

    @Size(max = 100, message = "公司名称不能超过100个字符")
    private String name;

    @Size(max = 50, message = "行业不能超过50个字符")
    private String industry;

    @Size(max = 20, message = "纳税人性质不能超过20个字符")
    private String taxpayerType;

    @Size(max = 500, message = "企业画像不能超过500个字符")
    private String description;
}
