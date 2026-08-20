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