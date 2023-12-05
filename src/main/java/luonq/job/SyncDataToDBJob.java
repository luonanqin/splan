package luonq.job;

import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import luonq.data.WriteToDB;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SyncDataToDBJob {

    @Autowired
    private WriteToDB writeToDB;

    @XxlJob("syncDataToDB.job")
    public void execute() throws Exception {
        writeToDB.additionToDB();
    }
}
