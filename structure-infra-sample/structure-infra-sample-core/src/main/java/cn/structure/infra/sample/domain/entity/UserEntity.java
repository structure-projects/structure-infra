package cn.structure.infra.sample.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@Data
public class UserEntity {

    private Long id;

    private String username;

    private String password;

    private String email;

    private Integer age;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
