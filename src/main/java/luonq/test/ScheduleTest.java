package luonq.test;

import org.springframework.stereotype.Component;

@Component
public class ScheduleTest {

//    @Scheduled(cron = "*/6 * * * * ?")
    public void test(){
        System.out.println(System.currentTimeMillis());
    }
}
