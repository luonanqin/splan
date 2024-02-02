package start.strategy;

import luonq.strategy.Strategy13;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import start.BaseTest;

public class Strategy13Test extends BaseTest {

    @Autowired
    private Strategy13 strategy13;

    @Test
    public void test(){
        try {
            strategy13.main();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
