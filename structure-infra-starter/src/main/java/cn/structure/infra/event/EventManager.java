package cn.structure.infra.event;

/**
 * <p>
 * 事件管理器
 * </p>
 *
 * @author chuck
 * @version 1.0.1
 * @since 2021/6/21 16:05
 */
public interface EventManager {

    /**
     * 发布事件
     * @param event  事件
     */
    void publish(Event event);

}
