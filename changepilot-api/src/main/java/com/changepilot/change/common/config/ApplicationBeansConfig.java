package com.changepilot.change.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ApplicationBeansConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
