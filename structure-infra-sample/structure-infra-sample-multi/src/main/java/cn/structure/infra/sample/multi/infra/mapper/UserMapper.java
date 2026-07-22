package cn.structure.infra.sample.multi.infra.mapper;

import cn.structure.infra.sample.multi.infra.po.MybatisUserPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<MybatisUserPO> {
}