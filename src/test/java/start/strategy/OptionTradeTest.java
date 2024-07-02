package start.strategy;

import luonq.execute.GrabOptionTradeData;
import luonq.execute.LoadOptionTradeData;
import luonq.listener.OptionStockListener;
import luonq.polygon.OptionTradeExecutor;
import luonq.polygon.RealTimeDataWS_DB;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import start.BaseTest;

import java.util.Map;

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
        RealTimeDataWS_DB client = new RealTimeDataWS_DB();
        client.setOpenTime(1719830004712l);
        OptionStockListener optionStockListener = new OptionStockListener();
        optionStockListener.cal("AAPL", 217.07);
        optionTradeExecutor.setClient(client);
        optionTradeExecutor.setOptionStockListener(optionStockListener);
        optionTradeExecutor.init();

        Map<String, Double> optionRtIvMap = optionTradeExecutor.getOptionRtIvMap();
        optionRtIvMap.put("AAPL++240705C00220000", 0.213721);
        optionRtIvMap.put("AAPL++240705P00215000", 0.190377);

        Map<String, String> codeToQuoteMap = optionTradeExecutor.getFutuQuote().getCodeToQuoteMap();
        codeToQuoteMap.put("AAPL240705C220000", "0.22|0.23");

        RealTimeDataWS_DB.realtimeQuoteForOptionMap.put("AAPL", 217.07);

        optionTradeExecutor.beginTrade();
    }
}
