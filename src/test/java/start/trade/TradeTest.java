package start.trade;

import luonq.job.GetDataJob;
import luonq.job.TradeJob;
import luonq.polygon.RealTimeDataWS_DB;
import luonq.polygon.RealTimeDataWS_DB2;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import start.BaseTest;

public class TradeTest extends BaseTest {

    @Autowired
    private RealTimeDataWS_DB realTimeDataWS_db;

    @Autowired
    private RealTimeDataWS_DB2 realTimeDataWS_db2;

    @Autowired
    private TradeJob tradeJob;

    @Autowired
    private GetDataJob getDataJob;

    @Test
    public void test() {
        realTimeDataWS_db.init();
    }

    @Test
    public void test2() {
        realTimeDataWS_db2.init();
    }

    @Test
    public void test_sellBeforeCloseMarket() {
        tradeJob.sellBeforeCloseMarket();
    }

    @Test
    public void test_getTradeDataJobAndComputeIndicator() {
        try {
            getDataJob.getTradeDataJobAndComputeIndicator();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test_getOptionTradeDataJob() {
        try {
            getDataJob.getOptionTradeDataJob();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
