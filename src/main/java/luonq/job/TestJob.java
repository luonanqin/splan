package luonq.job;

import com.google.common.collect.Lists;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import luonq.stock.MergeKline;
import org.springframework.stereotype.Component;
import util.BaseUtils;

import java.util.List;

@Slf4j
@Component
public class TestJob {

    @XxlJob("testJob")
    public void execute() throws Exception {
        System.out.println("################ test job");
        log.info("################ test job");
        List<String> testData = Lists.newArrayList();
        testData.add("testjob1");
        testData.add("testjob2");
        testData.add("testjob3");
        testData.add("testjob4");
        BaseUtils.writeFile("/Users/Luonanqin/study/intellij_idea_workspaces/splan/src/main/resources/historicalData/testjob", testData);

//        GetHistoricalDaily.getData();
        MergeKline.merge();
    }
}
