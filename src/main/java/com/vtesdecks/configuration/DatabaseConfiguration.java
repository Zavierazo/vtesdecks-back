package com.vtesdecks.configuration;

import com.vtesdecks.db.CryptMapper;
import com.vtesdecks.db.handlers.LocalDateHandler;
import com.vtesdecks.db.handlers.LocalDateTimeHandler;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.type.TypeHandler;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;
import java.net.UnknownHostException;

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
    public DataSource dataSource() throws UnknownHostException {
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
    public DataSourceTransactionManager transactionManager() throws UnknownHostException {
        return new DataSourceTransactionManager(dataSource());
    }

    @Bean
    public SqlSessionFactoryBean sqlSessionFactory() throws Exception {
        SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();
        sessionFactory.setDataSource(dataSource());
        sessionFactory.setTypeAliasesPackage(CryptMapper.class.getPackage().getName());
        sessionFactory.setTypeHandlers(new TypeHandler[]{
                new LocalDateTimeHandler(),
                new LocalDateHandler(),
        });
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        sessionFactory.setConfiguration(configuration);
        return sessionFactory;
    }

}
