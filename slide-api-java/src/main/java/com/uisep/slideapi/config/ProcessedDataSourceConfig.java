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
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuración del DataSource para la base de datos procesada (LECTURA/ESCRITURA).
 * Esta es la nueva base de datos donde se almacenan los datos depurados.
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = "com.uisep.slideapi.repository.processed",
    entityManagerFactoryRef = "processedEntityManagerFactory",
    transactionManagerRef = "processedTransactionManager"
)
public class ProcessedDataSourceConfig {
    
    @Primary
    @Bean
    @ConfigurationProperties("spring.datasource.processed")
    public DataSourceProperties processedDataSourceProperties() {
        return new DataSourceProperties();
    }
    
    @Primary
    @Bean(name = "processedDataSource")
    @ConfigurationProperties("spring.datasource.processed.hikari")
    public DataSource processedDataSource() {
        return processedDataSourceProperties()
            .initializeDataSourceBuilder()
            .type(HikariDataSource.class)
            .build();
    }
    
    @Primary
    @Bean(name = "processedEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean processedEntityManagerFactory(
            @Qualifier("processedDataSource") DataSource dataSource) {
        
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.uisep.slideapi.entity.processed");
        em.setPersistenceUnitName("processed");
        
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setShowSql(false);
        em.setJpaVendorAdapter(vendorAdapter);
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        properties.put("hibernate.hbm2ddl.auto", "update"); // Crear/actualizar esquema
        properties.put("hibernate.default_schema", "public");
        properties.put("hibernate.jdbc.batch_size", 50);
        properties.put("hibernate.order_inserts", true);
        properties.put("hibernate.order_updates", true);
        em.setJpaPropertyMap(properties);
        
        return em;
    }
    
    @Primary
    @Bean(name = "processedTransactionManager")
    public PlatformTransactionManager processedTransactionManager(
            @Qualifier("processedEntityManagerFactory") LocalContainerEntityManagerFactoryBean entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory.getObject());
    }
}
