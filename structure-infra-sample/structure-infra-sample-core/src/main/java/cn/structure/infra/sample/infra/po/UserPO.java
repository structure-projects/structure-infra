package cn.structure.infra.sample.infra.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user")
@Entity
@Table(name = "t_user")
public class UserPO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String email;

    private Integer age;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
