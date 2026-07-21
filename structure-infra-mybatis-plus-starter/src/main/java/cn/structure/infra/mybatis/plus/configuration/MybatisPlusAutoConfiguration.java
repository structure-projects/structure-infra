package cn.structure.infra.mybatis.plus.configuration;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@AutoConfiguration
@ConditionalOnClass(name = "com.baomidou.mybatisplus.core.mapper.BaseMapper")
@ConditionalOnProperty(prefix = "structure.infra", name = "type", havingValue = "MYBATIS_PLUS", matchIfMissing = true)
public class MybatisPlusAutoConfiguration {
}
