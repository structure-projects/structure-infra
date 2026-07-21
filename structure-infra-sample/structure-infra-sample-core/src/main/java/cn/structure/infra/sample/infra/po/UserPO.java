package cn.structure.infra.sample.infra.po;

import jakarta.persistence.Id;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户持久化对象（基础版本）
 * <p>
 * 仅包含基础字段定义，不包含任何框架特定的注解。
 * 各持久化技术模块应创建各自的专用 PO，继承或参考此类。
 *
 * @author chuck
 * @version 1.0.2
 * @since 2026/6/28
 */
@Data
public class UserPO {

    @Id
    private Long id;

    private String username;

    private String password;

    private String email;

    private Integer age;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
