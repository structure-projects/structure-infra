package cn.structure.infra.mybatis.plus.configuration;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * MyBatis Plus 自动配置类
 * <p>
 * 当检测到 MyBatis Plus 相关依赖时自动配置
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@AutoConfiguration
@ConditionalOnClass(name = "com.baomidou.mybatisplus.core.mapper.BaseMapper")
@ConditionalOnProperty(prefix = "structure.infra", name = "type", havingValue = "MYBATIS_PLUS", matchIfMissing = true)
public class MybatisPlusAutoConfiguration {

}
