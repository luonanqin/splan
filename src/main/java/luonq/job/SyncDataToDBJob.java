package luonq.job;

import com.google.gson.Gson;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import luonq.data.WriteToDB;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class SyncDataToDBJob extends BaseJob {

    @Autowired
    private WriteToDB writeToDB;

    @XxlJob("syncDataToDB.job")
    public void syncDataToDB() throws Exception {
        log.info("syncDataToDB.job start");
        writeToDB.additionToDB();
        //        BaseUtils.sendEmail("syncDataToDB finish", "");
        log.info("syncDataToDB.job end");
    }

    @XxlJob("syncEarningToDB.job")
    public void syncEarningToDB() throws Exception {
        log.info("syncEarningToDB.job start");
        if (noTrade()) {
            return;
        }
        Gson gson = new Gson();
        String dateJson = XxlJobHelper.getJobParam();
        List<String> dateList = null;
        if (StringUtils.isNotBlank(dateJson)) {
            dateList = gson.fromJson(dateJson, List.class);
            log.info("syncEarningToDB.job dateList: {}", dateList);
        }
        writeToDB.earningToDB(dateList);
        //        BaseUtils.sendEmail("syncEarningToDB finish", "");
        log.info("syncEarningToDB.job end");
    }

    @XxlJob("syncRehabToDB.job")
    public void syncRehabToDB() throws Exception {
        log.info("syncRehabToDB.job start");
        if (noTrade()) {
            return;
        }
        writeToDB.rehabToDB();
        //        BaseUtils.sendEmail("syncRehabToDB finish", "");
        log.info("syncRehabToDB.job end");
    }
}
