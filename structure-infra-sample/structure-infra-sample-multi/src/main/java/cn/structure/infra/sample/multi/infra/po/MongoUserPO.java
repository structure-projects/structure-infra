package cn.structure.infra.sample.multi.infra.po;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "t_user")
public class MongoUserPO {

    @Id
    private Long id;

    private String username;

    private String password;

    private String email;

    private Integer age;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}