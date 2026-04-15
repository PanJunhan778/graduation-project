package com.pjh.server.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.pjh.server.common.Result;
import com.pjh.server.service.DashboardService;
import com.pjh.server.vo.FinanceDashboardVO;
import com.pjh.server.vo.HomeAiSummaryVO;
import com.pjh.server.vo.HomeDashboardVO;
import com.pjh.server.vo.HrDashboardVO;
import com.pjh.server.vo.TaxDashboardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@SaCheckRole("owner")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/home")
    public Result<HomeDashboardVO> getHomeDashboard() {
        return Result.success(dashboardService.getHomeDashboard());
    }

    @GetMapping("/home-ai-summary")
    public Result<HomeAiSummaryVO> getHomeAiSummary() {
        return Result.success(dashboardService.getHomeAiSummary());
    }

    @GetMapping("/finance")
    public Result<FinanceDashboardVO> getFinanceDashboard(
            @RequestParam(defaultValue = "last6months") String range
    ) {
        return Result.success(dashboardService.getFinanceDashboard(range));
    }

    @GetMapping("/hr")
    public Result<HrDashboardVO> getHrDashboard(
            @RequestParam(defaultValue = "last6months") String range
    ) {
        return Result.success(dashboardService.getHrDashboard(range));
    }

    @GetMapping("/tax")
    public Result<TaxDashboardVO> getTaxDashboard(
            @RequestParam(required = false) String range
    ) {
        return Result.success(dashboardService.getTaxDashboard(range));
    }
}
