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
        total.setDate("`2023-11-01`");
        total.setCode("FUTU");
        testMapper.insertTest2(total);
    }
}
