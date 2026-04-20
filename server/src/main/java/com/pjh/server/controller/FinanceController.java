package com.pjh.server.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pjh.server.common.Result;
import com.pjh.server.dto.BatchDeleteDTO;
import com.pjh.server.dto.FinanceCreateDTO;
import com.pjh.server.service.FinanceService;
import com.pjh.server.vo.FinanceRecordVO;
import com.pjh.server.vo.FinanceRecycleBinVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/finance")
@SaCheckRole(value = {"owner", "staff"}, mode = SaMode.OR)
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
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) String keyword) {
        return Result.success(financeService.listRecords(page, size, type, category, startDate, endDate, keyword));
    }

    @GetMapping("/categories")
    public Result<List<String>> listCategories(@RequestParam(required = false) String type) {
        return Result.success(financeService.listCategories(type));
    }

    @GetMapping("/recycle-bin/list")
    @SaCheckRole("owner")
    public Result<IPage<FinanceRecycleBinVO>> listRecycleBin(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(financeService.listRecycleBinRecords(page, size));
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

    @PostMapping("/recycle-bin/{id}/restore")
    @SaCheckRole("owner")
    public Result<Void> restore(@PathVariable Long id) {
        financeService.restoreRecord(id);
        return Result.success("财务记录恢复成功", null);
    }

    @PostMapping("/recycle-bin/batch-restore")
    @SaCheckRole("owner")
    public Result<Integer> batchRestore(@RequestBody @Valid BatchDeleteDTO dto) {
        int restoredCount = financeService.batchRestore(dto.getIds());
        return Result.success("批量恢复成功", restoredCount);
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
