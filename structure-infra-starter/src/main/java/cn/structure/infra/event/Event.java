package cn.structure.infra.event;

/**
 * <p>
 * 事件接口
 * </p>
 *
 * @author chuck
 * @version 1.0.1
 * @since 2021/6/1 17:01
 */
public interface Event {

    /**
     * 获取事件ID 事件ID
     *
     * @return 事件ID
     */
    String getEventId();

    /**
     * 获取事件渠道类型
     *
     * @return 事件类型
     */
    default EventChannel getEventChannel() {
        return EventChannel.DEFAULT;
    }

}
