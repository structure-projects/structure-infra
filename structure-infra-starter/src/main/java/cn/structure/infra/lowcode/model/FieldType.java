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

package cn.structure.infra.lowcode.model;

/**
 * 字段类型枚举
 * <p>
 * 定义低代码资源 schema 中支持的字段类型，框架会根据类型
 * 自动映射到对应存储引擎的原生数据类型。
 *
 * @author chuck
 * @version 1.0.0
 * @since 2026/6/29
 */
public enum FieldType {

    /**
     * 字符串类型
     */
    STRING,

    /**
     * 长整型（64位）
     */
    LONG,

    /**
     * 整型（32位）
     */
    INTEGER,

    /**
     * 布尔型
     */
    BOOLEAN,

    /**
     * 高精度十进制
     */
    DECIMAL,

    /**
     * 日期时间
     */
    DATETIME,

    /**
     * 日期
     */
    DATE,

    /**
     * MongoDB ObjectId
     */
    OBJECT_ID,

    /**
     * 长文本
     */
    TEXT,

    /**
     * JSON 类型
     */
    JSON
}
