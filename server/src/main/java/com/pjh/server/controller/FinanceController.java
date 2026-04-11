package com.pjh.server.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pjh.server.common.Result;
import com.pjh.server.dto.BatchDeleteDTO;
import com.pjh.server.dto.FinanceCreateDTO;
import com.pjh.server.service.FinanceService;
import com.pjh.server.vo.FinanceRecordVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final FinanceService financeService;

    @GetMapping("/list")
    public Result<IPage<FinanceRecordVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return Result.success(financeService.listRecords(page, size, type, category, startDate, endDate));
    }

    @PostMapping
    public Result<Void> create(@RequestBody @Valid FinanceCreateDTO dto) {
        financeService.createRecord(dto);
        return Result.success("记录创建成功", null);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid FinanceCreateDTO dto) {
        financeService.updateRecord(id, dto);
        return Result.success("记录更新成功", null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        financeService.deleteRecord(id);
        return Result.success("记录删除成功", null);
    }

    @PostMapping("/batch-delete")
    public Result<Void> batchDelete(@RequestBody @Valid BatchDeleteDTO dto) {
        financeService.batchDelete(dto.getIds());
        return Result.success("批量删除成功", null);
    }

    @PostMapping("/import")
    public Result<?> importExcel(@RequestParam("file") MultipartFile file) {
        return financeService.importExcel(file);
    }

    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) {
        financeService.downloadTemplate(response);
    }
}
