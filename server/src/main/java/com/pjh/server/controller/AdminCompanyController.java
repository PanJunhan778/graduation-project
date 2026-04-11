package com.pjh.server.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pjh.server.common.Result;
import com.pjh.server.dto.CompanyCreateDTO;
import com.pjh.server.dto.OwnerCreateDTO;
import com.pjh.server.service.AdminCompanyService;
import com.pjh.server.vo.CompanyVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/company")
@SaCheckRole("admin")
@RequiredArgsConstructor
public class AdminCompanyController {

    private final AdminCompanyService adminCompanyService;

    @GetMapping("/list")
    public Result<IPage<CompanyVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return Result.success(adminCompanyService.listCompanies(page, size, keyword));
    }

    @PostMapping
    public Result<Void> create(@RequestBody @Valid CompanyCreateDTO dto) {
        adminCompanyService.createCompany(dto);
        return Result.success("公司创建成功", null);
    }

    @PostMapping("/{companyId}/owner")
    public Result<Void> createOwner(
            @PathVariable Long companyId,
            @RequestBody @Valid OwnerCreateDTO dto) {
        adminCompanyService.createOwner(companyId, dto);
        return Result.success("负责人账号创建成功", null);
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Integer> body) {
        Integer status = body.get("status");
        if (status == null) {
            return Result.fail("状态值不能为空");
        }
        adminCompanyService.updateCompanyStatus(id, status);
        return Result.success("状态更新成功", null);
    }
}
