package cn.structure.infra.sample.multi.infra.repository.mybatis;

import cn.structure.infra.mybatis.plus.repository.MybatisPlusRepositoryDelegate;
import cn.structure.infra.sample.multi.domain.entity.UserEntity;
import cn.structure.infra.sample.multi.infra.mapper.UserMapper;
import cn.structure.infra.sample.multi.infra.po.MybatisUserPO;
import cn.structure.infra.sample.multi.infra.repository.delegate.UserRepositoryDelegate;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Primary
public class UserMybatisPlusDelegate extends MybatisPlusRepositoryDelegate<UserEntity, MybatisUserPO, Long> implements UserRepositoryDelegate {

    public UserMybatisPlusDelegate(UserMapper userMapper) {
        this.baseMapper = userMapper;
    }

    @Override
    public UserEntity findByName(String name) {
        List<MybatisUserPO> pos = baseMapper.selectList(Wrappers.<MybatisUserPO>lambdaQuery().eq(MybatisUserPO::getUsername, name));
        return pos != null && !pos.isEmpty() ? toEntity(pos.get(0)) : null;
    }
}