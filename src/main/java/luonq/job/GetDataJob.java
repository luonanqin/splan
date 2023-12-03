package luonq.job;

import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import luonq.stock.Processor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GetDataJob {

    @XxlJob("getTradeDataAndComputeIndicator.job")
    public void getTradeDataJobAndComputeIndicator() throws Exception {
        Processor.getData();
    }

    @XxlJob("getRehab.Job")
    public void getRehab() throws Exception {
        Processor.getRehab();
    }

    @XxlJob("getEarning.Job")
    public void getEarning() throws Exception {
        Processor.getEarning();
    }
}