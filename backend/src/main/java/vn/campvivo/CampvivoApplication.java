package vn.campvivo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.TimeZone;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CampvivoApplication {
    public static void main(String[] args) {
        // JVM trên Windows báo múi giờ cũ "Asia/Saigon", PostgreSQL (image Debian 13) từ chối tên này khi kết nối
        // (docs/04-van-hanh/troubleshooting.md). Test không qua main() nên pom.xml đặt thêm argLine cho surefire.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        SpringApplication.run(CampvivoApplication.class, args);
    }
}
