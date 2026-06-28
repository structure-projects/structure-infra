package cn.structure.infra.sample.infra.repository.mybatis;

import cn.structure.infra.annotations.DelegateFor;
import cn.structure.infra.mybatis.plus.repository.MybatisPlusRepositoryDelegate;
import cn.structure.infra.repository.RepositoryType;
import cn.structure.infra.sample.infra.mapper.UserMapper;
import cn.structure.infra.sample.infra.po.UserPO;
import cn.structure.infra.sample.infra.repository.delegate.UserRepositoryDelegate;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@DelegateFor(
        name = "userRepository",
        type = RepositoryType.MYBATIS_PLUS,
        po = UserPO.class,
        description = "用户仓储 MyBatis Plus 实现",
        priority = 10
)
@AllArgsConstructor
public class UserMybatisPlusDelegate extends MybatisPlusRepositoryDelegate<UserPO, Long> implements UserRepositoryDelegate {

    private final UserMapper userMapper;

    @Override
    public UserPO finByName(String name) {
        return userMapper.selectOne(Wrappers.<UserPO>lambdaQuery().eq(UserPO::getUsername, name));
    }
}