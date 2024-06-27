package start.data;

import com.google.common.collect.Lists;
import luonq.data.WriteToDB;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import start.BaseTest;

public class WriteToDBTest extends BaseTest {

    @Autowired
    private WriteToDB writeToDB;

    @Before
    public void before() {
        System.out.println("begin");
    }

    @After
    public void after() {
        System.out.println("end");
    }

    @Test
    public void importStockKLine() throws Exception {
        writeToDB.importToDB(null, "2023-11-01");
    }

    @Test
    public void additionToDB() throws Exception {
        writeToDB.additionToDB(Lists.newArrayList(), Lists.newArrayList("2024-04-26"));
    }

    @Test
    public void earningToDB() {
        writeToDB.earningToDB(Lists.newArrayList("2024-06-27", "2024-06-28","2024-06-30", "2024-07-01", "2024-07-02"));
    }

    @Test
    public void rehabToDB() {
        writeToDB.rehabToDB();
    }
}
