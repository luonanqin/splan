package start.strategy;

import luonq.execute.GrabOptionTradeData;
import luonq.execute.LoadOptionTradeData;
import luonq.listener.OptionStockListener;
import luonq.polygon.OptionTradeExecutor;
import luonq.polygon.RealTimeDataWS_DB;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import start.BaseTest;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;

public class OptionTradeTest extends BaseTest {

    @Autowired
    private LoadOptionTradeData loadOptionTradeData;

    @Autowired
    private GrabOptionTradeData grabOptionTradeData;

    @Autowired
    private OptionTradeExecutor optionTradeExecutor;

    @Test
    public void test_grabOptionTradeData() {
        grabOptionTradeData.grab();
    }

    @Test
    public void test_getOptionChain() throws Exception {
        grabOptionTradeData.init();
        grabOptionTradeData.lastTradeDate = "2024-07-02";
        grabOptionTradeData.earningStocks.add("NVDA");
        grabOptionTradeData.grabOptionChain();
        grabOptionTradeData.grabLastdayOHLC();
        grabOptionTradeData.grabOptionId();
        grabOptionTradeData.grabHistoricalIv();
    }

    @Test
    public void test_loadOptionTradeData() throws Exception {
        loadOptionTradeData.load();
    }

    @Test
    public void test_optionStockListener() throws Exception {
        loadOptionTradeData.load();
        OptionStockListener optionStockListener = new OptionStockListener();
        //        optionStockListener.cal("AAPL", 208.1); // 股价波动被过滤
        optionStockListener.cal("AAPL", 215.07); // 股价波动超过2%
    }

    @Test
    public void test_begintrade() throws Exception {
        loadOptionTradeData.load();
        RealTimeDataWS_DB client = new RealTimeDataWS_DB();
        client.setOpenTime(1720093500000l);
        client.setCloseCheckTime(Date.from(LocalDateTime.now().plusDays(1).withHour(3).withMinute(59).withSecond(0).withNano(0).toInstant(ZoneOffset.of("+8"))));
        String stock = "NVDA";
        double open = 128.0;
        String callRt = "TSLA++240705C00230000";
        String putRt = "TSLA++240705C00220000";
        RealTimeDataWS_DB.realtimeQuoteForOptionMap.put(stock, open);

        OptionStockListener optionStockListener = new OptionStockListener();
        optionTradeExecutor.setClient(client);
        optionTradeExecutor.setOptionStockListener(optionStockListener);
        optionTradeExecutor.init();

        optionStockListener.cal(stock, open);

        Map<String, Double> optionRtIvMap = optionTradeExecutor.getOptionRtIvMap();
        optionRtIvMap.put(callRt, 0.8);
        optionRtIvMap.put(putRt, 0.8);

        Map<String, String> codeToQuoteMap = optionTradeExecutor.getFutuQuote().getCodeToQuoteMap();
        codeToQuoteMap.put("TSLA240705C230000", "0.22|0.23");
        codeToQuoteMap.put("TSLA240705P220000", "0.22|0.23");

        optionTradeExecutor.beginTrade();
    }

    @Test
    public void test_calTradePrice() throws Exception {
        loadOptionTradeData.load();
        String stock = "TSLA";
        double open = 224.0;
        String callRt = "TSLA++240705C00230000";
        String putRt = "TSLA++240705C00220000";
        RealTimeDataWS_DB.realtimeQuoteForOptionMap.put(stock, open);

        OptionStockListener optionStockListener = new OptionStockListener();
        optionTradeExecutor.setOptionStockListener(optionStockListener);
        optionTradeExecutor.init();

        optionStockListener.cal(stock, open);

        Map<String, Double> optionRtIvMap = optionTradeExecutor.getOptionRtIvMap();
        optionRtIvMap.put(callRt, 0.8);
        optionRtIvMap.put(putRt, 0.8);

        Map<String, String> codeToQuoteMap = optionTradeExecutor.getFutuQuote().getCodeToQuoteMap();
        codeToQuoteMap.put("TSLA240705C230000", "0.22|0.23");
        codeToQuoteMap.put("TSLA240705P220000", "0.22|0.23");

        optionTradeExecutor.calTradePrice(stock, callRt, "C");
    }

    @Test
    public void test_restart() throws Exception {
        OptionStockListener optionStockListener = new OptionStockListener();
        optionTradeExecutor.setOptionStockListener(optionStockListener);
        optionTradeExecutor.init();
        optionTradeExecutor.restart();
    }
}
