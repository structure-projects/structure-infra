package cn.structure.infra.schedule;

/**
 * 任务处理器函数式接口。
 *
 * <p>业务方通过实现此接口定义具体的任务执行逻辑，并由
 * {@link TaskHandlerRegistry} 按 {@code handlerName} 注册到调度器。
 * 调度器在触发任务时通过注册表查找对应的 handler 并调用其 {@link #execute(String)} 方法。</p>
 *
 * <p><b>设计意图：</b>采用函数式接口将"调度触发"与"业务执行"解耦，
 * 调度器只关心何时触发，业务方只关心执行什么；同时支持 Lambda 表达式，
 * 便于在 Spring 配置类中以简洁方式注册任务处理器。</p>
 *
 * <p><b>错误隔离约定：</b>实现方在 {@link #execute(String)} 中应妥善处理异常，
 * 即便未捕获，调度器内部也会对异常进行兜底捕获，避免单个任务的异常影响其他任务的调度。</p>
 */
@FunctionalInterface
public interface TaskHandler {

    /**
     * 执行任务逻辑。
     *
     * @param param 任务执行参数，由 {@link ScheduleTask#getHandlerParam()} 传入；
     *              可能为 {@code null}，由实现方自行判断是否需要处理
     */
    void execute(String param);
}