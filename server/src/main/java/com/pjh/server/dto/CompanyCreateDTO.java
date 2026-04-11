package com.pjh.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CompanyCreateDTO {

    @NotBlank(message = "公司名称不能为空")
    @Size(max = 100, message = "公司名称不超过100个字符")
    private String name;

    @NotBlank(message = "企业码不能为空")
    @Pattern(regexp = "^[A-Z0-9]{6}$", message = "企业码必须为6位大写字母或数字")
    private String companyCode;

    @Size(max = 50, message = "行业不超过50个字符")
    private String industry;

    @Size(max = 20, message = "纳税人性质不超过20个字符")
    private String taxpayerType;

    private String description;
}
