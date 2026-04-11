package com.pjh.server.config;

import cn.dev33.satoken.jwt.StpLogicJwtForMixin;
import cn.dev33.satoken.stp.StpLogic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SaTokenJwtConfig {

    @Bean
    public StpLogic getStpLogicJwt() {
        return new StpLogicJwtForMixin();
    }
}
