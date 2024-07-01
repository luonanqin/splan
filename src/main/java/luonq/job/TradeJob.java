package luonq.job;

import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import luonq.polygon.OptionTradeExecutor;
import luonq.polygon.RealTimeDataWS_DB;
import luonq.polygon.TradeExecutor;
import luonq.polygon.TradeExecutor_DB;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import util.BaseUtils;

@Slf4j
@Component
public class TradeJob extends BaseJob {

    @Autowired
    private RealTimeDataWS_DB realTimeDataWS_db;

    @Autowired
    private TradeExecutor_DB tradeExecutor_db;

    @Autowired
    private OptionTradeExecutor optionTradeExecutor;

    @XxlJob("Trade_DB.job")
    public void trade_DB() throws Exception {
        log.info("trade_DB.job start");
        if (noTrade()) {
            return;
        }
        realTimeDataWS_db.init();
        BaseUtils.sendEmail("trade finish", "");
        log.info("trade_DB.job end");
    }

    @XxlJob("SellBeforeCloseMarket.job")
    public void sellBeforeCloseMarket() {
        log.info("sellBeforeCloseMarket.job start");
        try {
            tradeExecutor_db.init();
            tradeExecutor_db.closeSell();
            log.info("tradeExecutor_db sell");
            return;
        } catch (Exception e) {
            log.error("sellBeforeCloseMarket error", e);
        }
        TradeExecutor tradeExecutor = new TradeExecutor();
        tradeExecutor.closeSell();
        log.info("sellBeforeCloseMarket.job end");
    }

    @XxlJob("cancelTimeoutOrder.job")
    public void cancelTimeoutOrder() {
        log.info("cancelTimeoutOrder.job stat");
        try {
            optionTradeExecutor.cancelOrder();
        } catch (Exception e) {
            log.error("cancelTimeoutOrder error", e);
        }
        log.info("cancelTimeoutOrder.job end");
    }
}
