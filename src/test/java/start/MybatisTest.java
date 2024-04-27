package start;

import bean.Total;
import luonq.mapper.TestMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class MybatisTest extends BaseTest{

    @Autowired
    private TestMapper testMapper;

    @Before
    public void before(){
        System.out.println("begin");
    }

    @After
    public void after(){
        System.out.println("end");
    }

    @Test
    public void test(){
        testMapper.insertTest();
    }

    @Test
    public void test2(){
        Total total = new Total();
        total.setDbYear("2023-11-01");
        total.setCode("FUTU");
        testMapper.insertTest2(total);
    }

    @Test
    public void test3() {
        String s = testMapper.showTables("2023-11-01");
        System.out.println(s);
    }

    @Test
    public void test4(){
        testMapper.createTable("`2023-11-02`");
    }
}
