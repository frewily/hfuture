package top.hfuture;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("top.hfuture.business.mapper")
public class HFutureApplication {

    public static void main(String[] args) {
        SpringApplication.run(HFutureApplication.class, args);
    }

}
