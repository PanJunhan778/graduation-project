package com.pjh.server.util;

public final class TenantContextHolder {

    private static final ThreadLocal<Long> COMPANY_ID_HOLDER = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void setCompanyId(Long companyId) {
        if (companyId == null) {
            COMPANY_ID_HOLDER.remove();
            return;
        }
        COMPANY_ID_HOLDER.set(companyId);
    }

    public static Long getCompanyId() {
        return COMPANY_ID_HOLDER.get();
    }

    public static void clear() {
        COMPANY_ID_HOLDER.remove();
    }
}
