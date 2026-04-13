package com.pjh.server.service;

import com.pjh.server.vo.FinanceDashboardVO;
import com.pjh.server.vo.HomeDashboardVO;
import com.pjh.server.vo.HrDashboardVO;
import com.pjh.server.vo.TaxDashboardVO;

public interface DashboardService {
    HomeDashboardVO getHomeDashboard();

    FinanceDashboardVO getFinanceDashboard(String range);

    HrDashboardVO getHrDashboard(String range);

    TaxDashboardVO getTaxDashboard(String range);
}
