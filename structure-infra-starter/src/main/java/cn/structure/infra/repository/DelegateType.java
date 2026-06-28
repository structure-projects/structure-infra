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
