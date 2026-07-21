package cn.structure.infra.sample.infra.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户持久化对象（MyBatis Plus 专用）
 * <p>
 * 仅包含 MyBatis Plus 框架的注解，不依赖其他持久化框架。
 *
 * @author chuck
 * @version 1.0.2
 * @since 2026/6/28
 */
@Data
@TableName("t_user")
public class MybatisUserPO {

    @Id
    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String email;

    private Integer age;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
