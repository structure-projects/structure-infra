package cn.structure.infra.sample.schedule.handler;

import cn.structure.infra.schedule.TaskHandlerRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class DemoTaskHandlers {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private TaskHandlerRegistry handlerRegistry;

    @PostConstruct
    public void registerHandlers() {
        handlerRegistry.register("demo-fixed-rate-handler", this::fixedRateHandler);

        handlerRegistry.register("demo-fixed-delay-handler", this::fixedDelayHandler);

        handlerRegistry.register("demo-param-handler", this::paramHandler);

        handlerRegistry.register("demo-error-handler", this::errorHandler);

        log.info("所有示例 TaskHandler 已注册");
    }

    public void fixedRateHandler(String param) {
        log.info("固定速率任务执行 - 当前时间: {}, 参数: {}", LocalDateTime.now().format(FORMATTER), param);
    }

    public void fixedDelayHandler(String param) {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("固定延迟任务执行 - 当前时间: {}, 参数: {}", LocalDateTime.now().format(FORMATTER), param);
    }

    public void paramHandler(String param) {
        log.info("带参数任务执行 - 参数: {}", param);
    }

    public void errorHandler(String param) {
        log.info("错误处理任务执行 - 参数: {}", param);
        throw new RuntimeException("模拟任务执行异常");
    }
}