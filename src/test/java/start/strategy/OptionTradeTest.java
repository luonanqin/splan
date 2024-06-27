package start.strategy;

import luonq.execute.GrabOptionTradeData;
import luonq.execute.LoadOptionTradeData;
import luonq.listener.OptionStockListener;
import luonq.polygon.OptionTradeExecutor;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import start.BaseTest;

public class OptionTradeTest extends BaseTest {

    @Autowired
    private LoadOptionTradeData loadOptionTradeData;

    @Autowired
    private GrabOptionTradeData grabOptionTradeData;

    @Autowired
    private OptionTradeExecutor optionTradeExecutor;

    @Test
    public void test_grabOptionTradeData(){
        grabOptionTradeData.grab();
    }

    @Test
    public void test_loadOptionTradeData() throws Exception {
        loadOptionTradeData.load();
    }

    @Test
    public void test_optionStockListener() throws Exception{
        loadOptionTradeData.load();
        OptionStockListener optionStockListener = new OptionStockListener();
//        optionStockListener.cal("AAPL", 208.1); // 股价波动被过滤
        optionStockListener.cal("AAPL", 215.07); // 股价波动超过2%
    }

    @Test
    public void test_begintrade() throws Exception {
        loadOptionTradeData.load();
        optionTradeExecutor.init();
        optionTradeExecutor.beginTrade();
    }
}
