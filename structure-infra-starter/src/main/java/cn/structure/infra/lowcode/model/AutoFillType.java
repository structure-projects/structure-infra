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
 * 自动填充类型枚举
 * <p>
 * 定义字段在数据写入时的自动填充策略，减少重复的字段赋值代码。
 *
 * @author chuck
 * @version 1.0.0
 * @since 2026/6/29
 */
public enum AutoFillType {

    /**
     * 不自动填充
     */
    NONE,

    /**
     * 仅创建时填充
     */
    CREATE,

    /**
     * 仅更新时填充
     */
    UPDATE,

    /**
     * 创建和更新时都填充
     */
    CREATE_UPDATE
}
