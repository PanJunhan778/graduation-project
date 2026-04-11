package com.pjh.server.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pjh.server.common.Result;
import com.pjh.server.dto.BatchDeleteDTO;
import com.pjh.server.dto.TaxUpsertDTO;
import com.pjh.server.service.TaxService;
import com.pjh.server.vo.TaxRecordVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/tax")
@SaCheckRole(value = {"owner", "staff"}, mode = SaMode.OR)
@RequiredArgsConstructor
public class TaxController {

    private final TaxService taxService;

    @GetMapping("/list")
    public Result<IPage<TaxRecordVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String taxType,
            @RequestParam(required = false) Integer paymentStatus,
            @RequestParam(required = false) String taxPeriod) {
        return Result.success(taxService.listRecords(page, size, taxType, paymentStatus, taxPeriod));
    }

    @PostMapping
    public Result<Void> create(@RequestBody @Valid TaxUpsertDTO dto) {
        taxService.createRecord(dto);
        return Result.success("税务记录创建成功", null);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid TaxUpsertDTO dto) {
        taxService.updateRecord(id, dto);
        return Result.success("税务记录更新成功", null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        taxService.deleteRecord(id);
        return Result.success("税务记录删除成功", null);
    }

    @PostMapping("/batch-delete")
    public Result<Void> batchDelete(@RequestBody @Valid BatchDeleteDTO dto) {
        taxService.batchDelete(dto.getIds());
        return Result.success("批量删除成功", null);
    }

    @PostMapping("/import")
    public Result<?> importExcel(@RequestParam("file") MultipartFile file) {
        return taxService.importExcel(file);
    }

    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) {
        taxService.downloadTemplate(response);
    }
}
