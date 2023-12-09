package luonq.job;

import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import luonq.polygon.RealTimeDataWS;
import luonq.polygon.TradeExecutor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TradeJob {

    @XxlJob("Trade.job")
    public void trade() throws Exception {
        RealTimeDataWS realTimeDataWS = new RealTimeDataWS();
        realTimeDataWS.init();
    }

    @XxlJob("SellBeforeCloseMarket.job")
    public void sellBeforeCloseMarket() {
        TradeExecutor tradeExecutor = new TradeExecutor();
        tradeExecutor.closeSell();
    }
}
