package start.strategy;

import luonq.execute.GrabOptionTradeData;
import luonq.execute.LoadOptionTradeData;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import start.BaseTest;

public class OptionTradeTest extends BaseTest {

    @Autowired
    private LoadOptionTradeData loadOptionTradeData;

    @Autowired
    private GrabOptionTradeData grabOptionTradeData;

    @Test
    public void test_grabOptionTradeData(){
        grabOptionTradeData.grab();
    }

    @Test
    public void test_loadOptionTradeData() throws Exception {
        loadOptionTradeData.load();
        loadOptionTradeData.cal("AAPL", 208.1); // 股价波动被过滤
        loadOptionTradeData.cal("AAPL", 213); // 股价波动超过2%
    }
}
