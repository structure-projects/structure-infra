package cn.structure.infra.sample.mongodb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "cn.structure.infra.sample")
public class MongoSampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(MongoSampleApplication.class, args);
    }
}
