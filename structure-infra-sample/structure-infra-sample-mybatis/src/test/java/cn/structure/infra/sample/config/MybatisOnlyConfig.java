package cn.structure.infra.sample.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

@Configuration
@ComponentScan(basePackages = {
        "cn.structure.infra.sample",
        "cn.structure.infra.repository"
}, excludeFilters = {
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "cn.structure.infra.sample.infra.repository.jpa.*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "cn.structure.infra.sample.infra.repository.mongodb.*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "cn.structure.infra.sample.infra.repository.elasticsearch.*")
})
@MapperScan("cn.structure.infra.sample.infra.mapper")
public class MybatisOnlyConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
        return interceptor;
    }
}