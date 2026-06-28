package cn.structure.infra.mybatis.plus.repository;

import cn.structure.infra.repository.RepositoryDelegate;
import cn.structure.infra.repository.RepositoryDelegateFactory;
import cn.structure.infra.repository.RepositoryType;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.springframework.context.ApplicationContext;

/**
 * MyBatis Plus 仓储委托工厂
 * <p>
 * 自动创建 MybatisPlusRepositoryDelegate 实例
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
public class MybatisPlusDelegateFactory implements RepositoryDelegateFactory {

    private final ApplicationContext applicationContext;

    public MybatisPlusDelegateFactory(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public RepositoryType getType() {
        return RepositoryType.MYBATIS_PLUS;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public RepositoryDelegate<?, ?> createDelegate(Class<?> poClass, Class<?> idClass) {
        try {
            BaseMapper mapper = (BaseMapper) findMapperByPoClass(poClass);
            if (mapper == null) {
                return null;
            }
            return new MybatisPlusRepositoryDelegate(mapper, poClass);
        } catch (Exception e) {
            return null;
        }
    }

    private Object findMapperByPoClass(Class<?> poClass) {
        String poClassName = poClass.getName();
        String mapperClassName = poClassName.replace(".po.", ".mapper.")
                .replace("PO", "Mapper");
        try {
            Class<?> mapperClass = Class.forName(mapperClassName);
            return applicationContext.getBean(mapperClass);
        } catch (Exception e) {
            String simpleMapperName = poClass.getSimpleName().replace("PO", "Mapper");
            for (String beanName : applicationContext.getBeanDefinitionNames()) {
                if (beanName.endsWith(simpleMapperName)) {
                    return applicationContext.getBean(beanName);
                }
            }
            return null;
        }
    }
}
