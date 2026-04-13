package com.pjh.server.util;

import cn.dev33.satoken.stp.StpUtil;
import com.pjh.server.common.Constants;
import com.pjh.server.exception.BusinessException;
import org.springframework.stereotype.Service;

@Service
public class CurrentSessionService {

    public Long requireCurrentUserId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            throw new BusinessException(401, "未登录或登录已过期");
        }
    }

    public Long requireCurrentCompanyId() {
        Object companyId = StpUtil.getExtra(Constants.JWT_COMPANY_ID_KEY);
        if (companyId == null) {
            throw new BusinessException("无法获取当前公司信息");
        }
        try {
            return Long.parseLong(companyId.toString());
        } catch (NumberFormatException e) {
            throw new BusinessException("当前公司信息无效");
        }
    }

    public String requireCurrentRole() {
        Object role = StpUtil.getExtra(Constants.JWT_ROLE_KEY);
        if (role == null) {
            throw new BusinessException("无法获取当前用户角色");
        }
        return role.toString();
    }
}
