package luonq.job;

import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import luonq.stock.Processor;
import org.springframework.stereotype.Component;
import util.BaseUtils;

@Slf4j
@Component
public class GetDataJob extends BaseJob {

    @XxlJob("getTradeDataAndComputeIndicator.job")
    public void getTradeDataJobAndComputeIndicator() throws Exception {
        log.info("getTradeDataAndComputeIndicator.job start");
        Processor.getData();
        BaseUtils.sendEmail("getTradeDataJobAndComputeIndicator finish", "");
        log.info("getTradeDataJobAndComputeIndicator.job end");
    }

    @XxlJob("getRehab.Job")
    public void getRehab() throws Exception {
        log.info("getRehab.job start");
        if (noTrade()) {
            return;
        }
        Processor.getRehab();
        BaseUtils.sendEmail("getRehab finish", "");
        log.info("getRehab.job end");
    }

    @XxlJob("getEarning.Job")
    public void getEarning() throws Exception {
        log.info("getEarning.job start");
        if (noTrade()) {
            return;
        }
        Processor.getEarning();
        BaseUtils.sendEmail("getEarning finish", "");
        log.info("getEarning.job end");
    }


}