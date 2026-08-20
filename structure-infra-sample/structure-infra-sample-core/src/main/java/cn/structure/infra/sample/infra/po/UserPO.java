/*
Copyright 2023 Structure Projects

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

	`http://www.apache.org/licenses/LICENSE-2.0`

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

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
