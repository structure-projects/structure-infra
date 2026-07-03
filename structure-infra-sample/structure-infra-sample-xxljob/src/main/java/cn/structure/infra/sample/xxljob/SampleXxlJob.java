package cn.structure.infra.sample.xxljob;

import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class SampleXxlJob {

    private final AtomicInteger counter = new AtomicInteger(0);

    @XxlJob("sampleJobHandler")
    public void sampleJobHandler() {
        int count = counter.incrementAndGet();
        
        log.info("SampleXxlJob executed, count={}", count);
    }

    @XxlJob("simpleJobHandler")
    public void simpleJobHandler() {
        log.info("SimpleXxlJob executed");
    }

    public int getCounter() {
        return counter.get();
    }

    public void resetCounter() {
        counter.set(0);
    }
}