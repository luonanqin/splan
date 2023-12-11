package luonq.job;

import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import luonq.polygon.RealTimeDataWS;
import luonq.polygon.RealTimeDataWS_DB;
import luonq.polygon.TradeExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TradeJob {

    @Autowired
    private RealTimeDataWS_DB realTimeDataWS_db;

    @XxlJob("Trade.job")
    public void trade() throws Exception {
        RealTimeDataWS realTimeDataWS = new RealTimeDataWS();
        realTimeDataWS.init();
    }

    @XxlJob("Trade_DB.job")
    public void trade_DB() throws Exception {
        realTimeDataWS_db.init();
    }

    @XxlJob("SellBeforeCloseMarket.job")
    public void sellBeforeCloseMarket() {
        TradeExecutor tradeExecutor = new TradeExecutor();
        tradeExecutor.closeSell();
    }
}
