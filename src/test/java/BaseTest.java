import com.google.gson.Gson;
import com.xhs.finance.framework.mvc.handle.GlobalResponseHandler;
import com.xiaohongshu.infra.redschedule.boot.starter.RedScheduleAutoConfiguration;
import com.xiaohongshu.infra.rpc.springboot.support.listener.ThriftApplicationListener;
import lombok.extern.slf4j.Slf4j;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("sit")
@ComponentScan(basePackages = {"com.xhs.purchase"},
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {GlobalResponseHandler.class, ThriftApplicationListener.class, RedScheduleAutoConfiguration.class}))
@Slf4j
public class BaseTest {

    protected Logger logger = LoggerFactory.getLogger(getClass());

    protected static Gson gson;
    @Autowired
    protected Environment environment;

    static {
        System.setProperty("user.env", "sit");
        gson = new Gson();
    }

}
