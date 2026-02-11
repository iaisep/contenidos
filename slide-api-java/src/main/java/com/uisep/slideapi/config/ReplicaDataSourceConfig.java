package com.uisep.slideapi.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuración del DataSource para la base de datos réplica (SOLO LECTURA).
 * Esta es la conexión a la réplica de Odoo UisepFinal.
 */
@Configuration
@EnableJpaRepositories(
    basePackages = "com.uisep.slideapi.repository.replica",
    entityManagerFactoryRef = "replicaEntityManagerFactory",
    transactionManagerRef = "replicaTransactionManager"
)
public class ReplicaDataSourceConfig {
    
    @Bean
    @ConfigurationProperties("spring.datasource.replica")
    public DataSourceProperties replicaDataSourceProperties() {
        return new DataSourceProperties();
    }
    
    @Bean(name = "replicaDataSource")
    @ConfigurationProperties("spring.datasource.replica.hikari")
    public DataSource replicaDataSource() {
        HikariDataSource dataSource = replicaDataSourceProperties()
            .initializeDataSourceBuilder()
            .type(HikariDataSource.class)
            .build();
        
        // Forzar modo solo lectura
        dataSource.setReadOnly(true);
        dataSource.setPoolName("ReplicaPool-ReadOnly");
        
        return dataSource;
    }
    
    @Bean(name = "replicaEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean replicaEntityManagerFactory(
            @Qualifier("replicaDataSource") DataSource dataSource) {
        
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.uisep.slideapi.entity.replica");
        em.setPersistenceUnitName("replica");
        
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setShowSql(false);
        em.setJpaVendorAdapter(vendorAdapter);
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        properties.put("hibernate.hbm2ddl.auto", "none"); // Nunca modificar la réplica
        properties.put("hibernate.default_schema", "public");
        properties.put("hibernate.connection.readOnly", true);
        em.setJpaPropertyMap(properties);
        
        return em;
    }
    
    @Bean(name = "replicaTransactionManager")
    public PlatformTransactionManager replicaTransactionManager(
            @Qualifier("replicaEntityManagerFactory") LocalContainerEntityManagerFactoryBean entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory.getObject());
    }
}
