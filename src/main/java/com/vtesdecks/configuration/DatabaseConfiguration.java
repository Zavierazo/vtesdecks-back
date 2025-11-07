package com.vtesdecks.configuration;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

@Configuration
public class DatabaseConfiguration {
    @Value("${datasource.jdbcUrl}")
    private String databaseHost;
    @Value("${datasource.username}")
    private String databaseUsername;
    @Value("${datasource.password}")
    private String databasePassword;

    @Primary
    @Bean
    @FlywayDataSource
    public DataSource dataSource() {
        HikariDataSource hikariDataSource = new HikariDataSource();
        hikariDataSource.setMaxLifetime(1200000);
        hikariDataSource.setMaximumPoolSize(100);
        hikariDataSource.setMinimumIdle(10);
        hikariDataSource.setPoolName("MySQL Pool");
        hikariDataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        hikariDataSource.setJdbcUrl(databaseHost);
        hikariDataSource.setUsername(databaseUsername);
        hikariDataSource.setPassword(databasePassword);
        return hikariDataSource;
    }

    @Bean
    public DataSourceTransactionManager transactionManager() {
        return new DataSourceTransactionManager(dataSource());
    }
}
