package luonq.job;

import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import luonq.execute.GrabOptionTradeData;
import luonq.stock.Processor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GetDataJob extends BaseJob {

    @XxlJob("getTradeDataAndComputeIndicator.job")
    public void getTradeDataJobAndComputeIndicator() throws Exception {
        log.info("getTradeDataAndComputeIndicator.job start");
        Processor.getData();
        log.info("getTradeDataJobAndComputeIndicator.job end");
    }

    @XxlJob("getRehab.Job")
    public void getRehab() throws Exception {
        log.info("getRehab.job start");
        if (noTrade()) {
            return;
        }
        Processor.getRehab();
        log.info("getRehab.job end");
    }

    @XxlJob("getEarning.Job")
    public void getEarning() throws Exception {
        log.info("getEarning.job start");
        if (noTrade()) {
            return;
        }
        Processor.getEarning();
        log.info("getEarning.job end");
    }

    @Autowired
    private GrabOptionTradeData grabOptionTradeData;

    @XxlJob("getOptionTradeData.job")
    public void getOptionTradeData() throws Exception {
        log.info("getOptionTradeData.job start");
        grabOptionTradeData.grab();
        log.info("getOptionTradeData.job end");
    }
}