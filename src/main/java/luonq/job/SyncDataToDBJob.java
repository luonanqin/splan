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
public class SyncDataToDBJob {

    @Autowired
    private WriteToDB writeToDB;

    @XxlJob("syncDataToDB.job")
    public void syncDataToDB() throws Exception {
        writeToDB.additionToDB();
    }

    @XxlJob("syncEarningToDB.job")
    public void syncEarningToDB() throws Exception {
        Gson gson = new Gson();
        String dateJson = XxlJobHelper.getJobParam();
        List<String> dateList = null;
        if (StringUtils.isNotBlank(dateJson)) {
            dateList = gson.fromJson(dateJson, List.class);
            System.out.println(dateList);
        }
        writeToDB.earningToDB(dateList);
    }

    @XxlJob("syncRehabToDB.job")
    public void syncRehabToDB() throws Exception {
        writeToDB.rehabToDB();
    }
}
