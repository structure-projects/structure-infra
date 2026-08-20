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

package cn.structure.infra.repository;

/**
 * 委托类型
 * <p>
 * 用于区分不同用途的 RepositoryDelegate
 * <p>
 * - BASE: 基础代理，承担写操作和默认读操作
 * - READ: 读代理，专门承担读操作（CQRS 模式下使用）
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
public enum DelegateType {

    BASE,

    READ
}
