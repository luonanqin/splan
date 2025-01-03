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
        writeToDB.additionToDB(Lists.newArrayList("LAES"), Lists.newArrayList("2024-12-30","2024-12-27","2024-12-26","2024-12-24","2024-12-23","2024-12-20","2024-12-19","2024-12-18","2024-12-17","2024-12-16","2024-12-13","2024-12-12","2024-12-11","2024-12-10","2024-12-09","2024-12-06","2024-12-05","2024-12-04","2024-12-03","2024-12-02","2024-11-29","2024-11-27","2024-11-26","2024-11-25","2024-11-22","2024-11-21","2024-11-20","2024-11-19","2024-11-18","2024-11-15","2024-11-14","2024-11-13","2024-11-12","2024-11-11","2024-11-08","2024-11-07","2024-11-06","2024-11-05","2024-11-04","2024-11-01","2024-10-31","2024-10-30","2024-10-29","2024-10-28","2024-10-25","2024-10-24","2024-10-23","2024-10-22","2024-10-21","2024-10-18","2024-10-17","2024-10-16","2024-10-15","2024-10-14","2024-10-11","2024-10-10","2024-10-09","2024-10-08","2024-10-07","2024-10-04","2024-10-03","2024-10-02","2024-10-01","2024-09-30","2024-09-27","2024-09-26","2024-09-25","2024-09-24","2024-09-23","2024-09-20","2024-09-19","2024-09-18","2024-09-17","2024-09-16","2024-09-13","2024-09-12","2024-09-11","2024-09-10","2024-09-09","2024-09-06","2024-09-05","2024-09-04","2024-09-03","2024-08-30","2024-08-29","2024-08-28","2024-08-27","2024-08-26","2024-08-23","2024-08-22","2024-08-21","2024-08-20","2024-08-19","2024-08-16","2024-08-15","2024-08-14","2024-08-13","2024-08-12","2024-08-09","2024-08-08","2024-08-07","2024-08-06","2024-08-05","2024-08-02","2024-08-01","2024-07-31","2024-07-30","2024-07-29","2024-07-26","2024-07-25","2024-07-24","2024-07-23","2024-07-22","2024-07-19","2024-07-18","2024-07-17","2024-07-16","2024-07-15","2024-07-12","2024-07-11","2024-07-10","2024-07-09","2024-07-08","2024-07-05","2024-07-03","2024-07-02","2024-07-01","2024-06-28","2024-06-27","2024-06-26","2024-06-25","2024-06-24","2024-06-21","2024-06-20","2024-06-18","2024-06-17","2024-06-14","2024-06-13","2024-06-12","2024-06-11","2024-06-10","2024-06-07","2024-06-06","2024-06-05","2024-06-04","2024-06-03","2024-05-31","2024-05-30","2024-05-29","2024-05-28","2024-05-24","2024-05-23","2024-05-22","2024-05-21","2024-05-20","2024-05-17","2024-05-16","2024-05-15","2024-05-14","2024-05-13","2024-05-10","2024-05-09","2024-05-08","2024-05-07","2024-05-06","2024-05-03","2024-05-02","2024-05-01","2024-04-30","2024-04-29","2024-04-26","2024-04-25","2024-04-24","2024-04-23","2024-04-22","2024-04-19","2024-04-18","2024-04-17","2024-04-16","2024-04-15","2024-04-12","2024-04-11","2024-04-10","2024-04-09","2024-04-08","2024-04-05","2024-04-04","2024-04-03","2024-04-02","2024-04-01","2024-03-28","2024-03-27","2024-03-26","2024-03-25","2024-03-22","2024-03-21","2024-03-20","2024-03-19","2024-03-18","2024-03-15","2024-03-14","2024-03-13","2024-03-12","2024-03-11","2024-03-08","2024-03-07","2024-03-06","2024-03-05","2024-03-04","2024-03-01","2024-02-29","2024-02-28","2024-02-27","2024-02-26","2024-02-23","2024-02-22","2024-02-21","2024-02-20","2024-02-16","2024-02-15","2024-02-14","2024-02-13","2024-02-12","2024-02-09","2024-02-08","2024-02-07","2024-02-06","2024-02-05","2024-02-02","2024-02-01","2024-01-31","2024-01-30"));
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
