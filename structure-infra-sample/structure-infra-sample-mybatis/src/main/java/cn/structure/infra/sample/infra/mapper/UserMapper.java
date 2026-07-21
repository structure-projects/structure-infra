package cn.structure.infra.sample.infra.mapper;

import cn.structure.infra.sample.infra.po.MybatisUserPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper（MyBatis Plus）
 *
 * @author chuck
 * @version 1.0.2
 * @since 2026/6/28
 */
@Mapper
public interface UserMapper extends BaseMapper<MybatisUserPO> {
}
