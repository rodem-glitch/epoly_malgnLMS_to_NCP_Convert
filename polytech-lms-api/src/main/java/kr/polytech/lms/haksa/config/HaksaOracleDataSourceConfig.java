package kr.polytech.lms.haksa.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * 왜: 폴리텍 학사 Oracle DB를 별도 DataSource로 구성합니다.
 * - MySQL(primary)과 분리하여, haksa 패키지의 JPA 리포지토리만 Oracle에 연결합니다.
 * - haksa.oracle.url이 비어있으면 이 설정이 비활성화되어 기존 MySQL만 동작합니다.
 */
@Configuration
@ConditionalOnProperty(name = "haksa.oracle.url", matchIfMissing = false)
@EnableConfigurationProperties(HaksaOracleProperties.class)
@EnableJpaRepositories(
        basePackages = "kr.polytech.lms.haksa.repository",
        entityManagerFactoryRef = "haksaEntityManagerFactory",
        transactionManagerRef = "haksaTransactionManager"
)
public class HaksaOracleDataSourceConfig {

    // 왜: HikariCP를 직접 구성하여 Oracle 전용 커넥션 풀을 만듭니다.
    @Bean(name = "haksaDataSource")
    public DataSource haksaDataSource(HaksaOracleProperties props) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(props.getUrl());
        ds.setUsername(props.getUsername());
        ds.setPassword(props.getPassword());
        ds.setDriverClassName(props.getDriverClassName());

        HaksaOracleProperties.HikariSettings h = props.getHikari();
        ds.setMaximumPoolSize(h.getMaximumPoolSize());
        ds.setMinimumIdle(h.getMinimumIdle());
        ds.setConnectionTimeout(h.getConnectionTimeout());
        ds.setConnectionTestQuery(h.getConnectionTestQuery());
        ds.setKeepaliveTime(h.getKeepaliveTime());
        ds.setMaxLifetime(h.getMaxLifetime());
        ds.setReadOnly(h.isReadOnly());
        ds.setPoolName(h.getPoolName());

        return ds;
    }

    // 왜: haksa 패키지의 엔티티만 스캔하여 Oracle EntityManager를 구성합니다.
    @Bean(name = "haksaEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean haksaEntityManagerFactory(
            @Qualifier("haksaDataSource") DataSource dataSource) {

        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("kr.polytech.lms.haksa.entity");
        em.setPersistenceUnitName("haksa-oracle");

        HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
        adapter.setShowSql(false);
        adapter.setGenerateDdl(false);
        // 왜: Oracle 방언을 명시하여 Hibernate가 Oracle SQL을 올바르게 생성합니다.
        adapter.setDatabasePlatform("org.hibernate.dialect.OracleDialect");
        em.setJpaVendorAdapter(adapter);

        Map<String, Object> props = new HashMap<>();
        // 왜: 뷰테이블은 읽기 전용이므로 DDL 자동생성을 차단합니다.
        props.put("hibernate.hbm2ddl.auto", "none");
        // 왜: Oracle의 대소문자 구분을 유지합니다.
        props.put("hibernate.physical_naming_strategy",
                "org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl");
        em.setJpaPropertyMap(props);

        return em;
    }

    // 왜: haksa 리포지토리 전용 트랜잭션 매니저 (읽기 전용)
    @Bean(name = "haksaTransactionManager")
    public PlatformTransactionManager haksaTransactionManager(
            @Qualifier("haksaEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}
