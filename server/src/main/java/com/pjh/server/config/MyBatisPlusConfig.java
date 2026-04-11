package com.pjh.server.config;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.pjh.server.common.Constants;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NullValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration
public class MyBatisPlusConfig {

    private static final Set<String> IGNORE_TABLES = Set.of(
            "company", "user", "audit_log", "ai_chat_log"
    );

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                try {
                    Object companyId = StpUtil.getExtra(Constants.JWT_COMPANY_ID_KEY);
                    if (companyId != null) {
                        return new LongValue(Long.parseLong(companyId.toString()));
                    }
                } catch (Exception ignored) {
                }
                return new NullValue();
            }

            @Override
            public String getTenantIdColumn() {
                return "company_id";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                return IGNORE_TABLES.contains(tableName);
            }
        }));

        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());

        return interceptor;
    }
}
