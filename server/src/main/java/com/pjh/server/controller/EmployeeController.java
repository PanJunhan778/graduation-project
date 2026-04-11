package com.pjh.server.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pjh.server.common.Result;
import com.pjh.server.dto.BatchDeleteDTO;
import com.pjh.server.dto.EmployeeUpsertDTO;
import com.pjh.server.service.EmployeeService;
import com.pjh.server.vo.EmployeeVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/employee")
@SaCheckRole(value = {"owner", "staff"}, mode = SaMode.OR)
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping("/list")
    public Result<IPage<EmployeeVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Integer status) {
        return Result.success(employeeService.listEmployees(page, size, department, status));
    }

    @PostMapping
    public Result<Void> create(@RequestBody @Valid EmployeeUpsertDTO dto) {
        employeeService.createEmployee(dto);
        return Result.success("员工记录创建成功", null);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid EmployeeUpsertDTO dto) {
        employeeService.updateEmployee(id, dto);
        return Result.success("员工记录更新成功", null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        employeeService.deleteEmployee(id);
        return Result.success("员工记录删除成功", null);
    }

    @PostMapping("/batch-delete")
    public Result<Void> batchDelete(@RequestBody @Valid BatchDeleteDTO dto) {
        employeeService.batchDelete(dto.getIds());
        return Result.success("批量删除成功", null);
    }

    @PostMapping("/import")
    public Result<?> importExcel(@RequestParam("file") MultipartFile file) {
        return employeeService.importExcel(file);
    }

    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) {
        employeeService.downloadTemplate(response);
    }
}
