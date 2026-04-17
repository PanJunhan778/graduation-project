package com.pjh.server.config;

import com.pjh.server.util.TenantContextHolder;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NullValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MyBatisPlusConfigTest {

    private final MyBatisPlusConfig myBatisPlusConfig = new MyBatisPlusConfig();

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void resolveTenantIdShouldPreferThreadLocalCompanyId() {
        TenantContextHolder.setCompanyId(4L);

        Expression tenantId = myBatisPlusConfig.resolveTenantId();

        assertThat(tenantId).isInstanceOf(LongValue.class);
        assertThat(((LongValue) tenantId).getValue()).isEqualTo(4L);
    }

    @Test
    void resolveTenantIdShouldReturnNullValueWhenNoTenantContextExists() {
        Expression tenantId = myBatisPlusConfig.resolveTenantId();

        assertThat(tenantId).isInstanceOf(NullValue.class);
    }
}
