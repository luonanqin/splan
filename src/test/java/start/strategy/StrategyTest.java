package start.strategy;

import luonq.strategy.Strategy13;
import luonq.strategy.Strategy14;
import luonq.strategy.Strategy14_1;
import luonq.strategy.Strategy14test;
import luonq.strategy.Strategy15;
import luonq.strategy.Strategy15_1;
import luonq.strategy.Strategy15test;
import luonq.strategy.Strategy17;
import luonq.strategy.Strategy18;
import luonq.strategy.Strategy40;
import luonq.strategy.Strategy41;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import start.BaseTest;

public class StrategyTest extends BaseTest {

    @Autowired
    private Strategy13 strategy13;

    @Autowired
    private Strategy14 strategy14;
    @Autowired
    private Strategy14_1 strategy14_1;
    @Autowired
    private Strategy14test strategy14test;

    @Autowired
    private Strategy15 strategy15;
    @Autowired
    private Strategy15_1 strategy15_1;
    @Autowired
    private Strategy15test strategy15test;

    @Autowired
    private Strategy17 strategy17;

    @Autowired
    private Strategy18 strategy18;

    @Autowired
    private Strategy40 strategy40;

    @Autowired
    private Strategy41 strategy41;

    @Test
    public void test13() {
        try {
            strategy13.main();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test14() {
        try {
            strategy14.main();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test141() {
        try {
            strategy14_1.main();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test14test() {
        try {
            strategy14test.main();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test15() {
        try {
            strategy15.main();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test15_1() {
        try {
            strategy15_1.main();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test15test1() {
        try {
            strategy15test.main();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test17() {
        try {
            strategy17.main();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test18() {
        try {
            strategy18.main();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test40() {
        try {
            strategy40.test();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test41() {
        try {
            //            strategy41.filter1();
            //            strategy41.filter2();
            strategy41.filter3();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
