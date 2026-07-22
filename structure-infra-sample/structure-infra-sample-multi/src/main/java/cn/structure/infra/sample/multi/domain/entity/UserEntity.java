package cn.structure.infra.sample.multi.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

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