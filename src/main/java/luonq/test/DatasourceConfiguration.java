package luonq.test;

import com.alibaba.druid.pool.DruidDataSource;
import com.xhs.finance.utils.PropUtils;
import com.xhs.purchase.base.TypeAliases;
import com.xiaohongshu.infra.utils.mybatis.MybatisConfig;
import com.xiaohongshu.infra.utils.mybatis.MybatisSQLInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.EnumTypeHandler;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.boot.autoconfigure.SpringBootVFS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import tk.mybatis.spring.annotation.MapperScan;

import javax.sql.DataSource;
import java.io.IOException;

@MapperScan(basePackages = {"com.xhs.purchase.*.mapper"})
@Configuration
@Slf4j
public class DatasourceConfiguration extends MybatisConfig {

    @Autowired
    private Environment env;

    @Bean(name = "druidDataSource", destroyMethod = "close")
    public DruidDataSource createDataSource() throws IOException {
        log.info("====== DataSource init start ! =======");
        DruidDataSource dataSource = new DruidDataSource();
        dataSource.setDriverClassName(env.getProperty("jdbc.driver-class-name"));
        dataSource.setUrl(env.getProperty("jdbc.url"));
        dataSource.setUsername(env.getProperty("jdbc.username"));
        dataSource.setPassword(env.getProperty("jdbc.password"));
        dataSource.configFromPropety(PropUtils.load(DatasourceConfiguration.class, "druid.properties"));
        log.info("====== DataSource init success ! =======");
        return dataSource;
    }

    @Bean(name = "sqlSessionFactory")
    public SqlSessionFactory sqlSessionFactory(@Qualifier("druidDataSource") DataSource dataSource) throws Exception {
        MybatisSQLInterceptor mybatisSQLInterceptor = new MybatisSQLInterceptor();
        SqlSessionFactoryBean sessionFactoryBean = new SqlSessionFactoryBean();
        sessionFactoryBean.setDataSource(dataSource);
        VendorDatabaseIdProvider databaseIdProvider = new VendorDatabaseIdProvider();
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setDefaultEnumTypeHandler(EnumTypeHandler.class);
        sessionFactoryBean.setConfiguration(configuration);
        sessionFactoryBean.setDatabaseIdProvider(databaseIdProvider);
        sessionFactoryBean.setConfiguration(new org.apache.ibatis.session.Configuration());
        sessionFactoryBean.setPlugins(new Interceptor[]{mybatisSQLInterceptor});
        sessionFactoryBean.setTypeAliasesPackage("com.xhs.purchase");
        sessionFactoryBean.setTypeAliasesSuperType(TypeAliases.class);
        sessionFactoryBean.setVfs(SpringBootVFS.class);
        sessionFactoryBean.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath:sqlmap/**/*.xml"));
        return sessionFactoryBean.getObject();
    }

    @Bean(name = "sqlSessionTemplate")
    @Primary
    public SqlSessionTemplate sqlSessionTemplate(@Qualifier("sqlSessionFactory") SqlSessionFactory sqlSessionFactory) throws Exception {
        org.apache.ibatis.session.Configuration configuration = sqlSessionFactory.getConfiguration();
        configuration.setMapUnderscoreToCamelCase(true); // 配置下划线转驼峰
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    @Bean(name = "transactionManager")
    @Primary
    public DataSourceTransactionManager transactionManager(@Qualifier("druidDataSource") DataSource dataSource) throws Exception {
        return new DataSourceTransactionManager(dataSource);
    }
}
