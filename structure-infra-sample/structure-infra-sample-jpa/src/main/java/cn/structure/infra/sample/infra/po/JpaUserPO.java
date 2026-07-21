package cn.structure.infra.sample.infra.po;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户持久化对象（JPA 专用）
 * <p>
 * JPA 框架专用的 PO，包含 JPA 注解。
 *
 * @author chuck
 * @version 1.0.2
 * @since 2026/6/28
 */
@Data
@Entity
@Table(name = "t_user")
public class JpaUserPO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    private String password;

    private String email;

    private Integer age;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}