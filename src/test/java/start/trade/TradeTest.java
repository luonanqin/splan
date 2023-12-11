package start.trade;

import luonq.polygon.RealTimeDataWS_DB;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import start.BaseTest;

public class TradeTest extends BaseTest {

    @Autowired
    private RealTimeDataWS_DB realTimeDataWS_db;

    @Test
    public void test(){
        realTimeDataWS_db.init();
    }
}
