package com.lumina.gateway.audit;

import com.zaxxer.hikari.HikariDataSource;
import com.lumina.gateway.config.GatewayProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 审计落库 MySQL 装配（可选，gateway.audit.mysql-enabled=true 时启用）。
 * <p>
 * 默认排除 DataSourceAutoConfiguration，由本类按需创建 DataSource + JdbcTemplate。
 */
@Configuration
public class MysqlConfiguration {

    @Bean
    @ConditionalOnProperty(name = "gateway.audit.mysql-enabled", havingValue = "true")
    public DataSource auditDataSource(GatewayProperties properties) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(properties.getAudit().getMysqlUrl());
        ds.setUsername(properties.getAudit().getMysqlUser());
        ds.setPassword(properties.getAudit().getMysqlPassword());
        ds.setMaximumPoolSize(5);
        ds.setPoolName("audit-pool");
        return ds;
    }

    @Bean
    @ConditionalOnProperty(name = "gateway.audit.mysql-enabled", havingValue = "true")
    public JdbcTemplate auditJdbcTemplate(DataSource auditDataSource) {
        return new JdbcTemplate(auditDataSource);
    }
}
