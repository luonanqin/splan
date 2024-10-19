package start.strategy;

import com.futu.openapi.FTAPI;
import com.google.common.collect.Maps;
import luonq.execute.LoadOptionTradeData;
import luonq.execute.ReadWriteOptionTradeInfo;
import luonq.futu.BasicQuote;
import luonq.listener.OptionStockListener5;
import luonq.polygon.OptionTradeExecutor5;
import luonq.polygon.RealTimeDataWS_DB2;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import start.BaseTest;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;

public class OptionTradeTest5 extends BaseTest {

    @Autowired
    private LoadOptionTradeData loadOptionTradeData;

    @Autowired
    private OptionTradeExecutor5 optionTradeExecutor;

    @Test
    public void test_optionStockListener() throws Exception {
        loadOptionTradeData.load();
        FTAPI.init();
        BasicQuote futuQuote = new BasicQuote();
        futuQuote.start();
        RealTimeDataWS_DB2 client = new RealTimeDataWS_DB2();
        client.setOpenTime(1723469400000l);
        client.setCloseCheckTime(Date.from(LocalDateTime.now().plusDays(1).withHour(3).withMinute(59).withSecond(0).withNano(0).toInstant(ZoneOffset.of("+8"))));

        OptionStockListener5 optionStockListener = new OptionStockListener5();
        optionTradeExecutor.setClient(client);
        optionTradeExecutor.setOptionStockListener(optionStockListener);
        optionTradeExecutor.setFutuQuote(futuQuote);
        optionStockListener.setOptionTradeExecutor(optionTradeExecutor);
        optionTradeExecutor.init();
                optionStockListener.cal("AAPL", 232.15); // 股价波动被过滤
//        optionStockListener.cal("NKLA", 11.38); // 股价波动超过2%
    }

    @Test
    public void test_begintrade() throws Exception {
        loadOptionTradeData.load();
        ReadWriteOptionTradeInfo.init();
        RealTimeDataWS_DB2 client = new RealTimeDataWS_DB2();
        client.setOpenTime(1723469400000l);
        client.setCloseCheckTime(Date.from(LocalDateTime.now().plusDays(1).withHour(3).withMinute(59).withSecond(0).withNano(0).toInstant(ZoneOffset.of("+8"))));
        String stock = "BEKE";
        double open = 15.7;
        RealTimeDataWS_DB2.realtimeQuoteForOptionMap.put(stock, open);

        OptionStockListener5 optionStockListener = new OptionStockListener5();
        optionTradeExecutor.setClient(client);
        optionTradeExecutor.setOptionStockListener(optionStockListener);
        optionStockListener.setOptionTradeExecutor(optionTradeExecutor);
        optionTradeExecutor.init();

        optionStockListener.cal(stock, open);

        Map<String, Double> codeToAskMap = optionTradeExecutor.getFutuQuote().getCodeToAskMap();
        Map<String, Double> codeToBidMap = optionTradeExecutor.getFutuQuote().getCodeToBidMap();
        codeToAskMap.put("U240816C15000", 0.63);
        codeToBidMap.put("U240816C15000", 0.43);
        codeToAskMap.put("U240816P14500", 0.83);
        codeToBidMap.put("U240816P14500", 0.42);

        optionTradeExecutor.beginTrade();
    }

    @Test
    public void test_restart() throws Exception {
        loadOptionTradeData.load();
        ReadWriteOptionTradeInfo.init();

        OptionStockListener5 optionStockListener = new OptionStockListener5();
        optionTradeExecutor.setOptionStockListener(optionStockListener);

        RealTimeDataWS_DB2 client = new RealTimeDataWS_DB2();
        client.setOpenTime(1723469400000l);
        client.setCloseCheckTime(Date.from(LocalDateTime.now().plusDays(1).withHour(3).withMinute(59).withSecond(0).withNano(0).toInstant(ZoneOffset.of("+8"))));
        optionTradeExecutor.setClient(client);

        optionTradeExecutor.init();
        optionTradeExecutor.restart();
    }

    @Test
    public void test_calQuoteMidPrice() throws InterruptedException {
        String futuCode = "AAPL240726C120000";
        optionTradeExecutor.setCloseTime(1721419140000l);
        Map<String, Double> codeToAskMap = Maps.newHashMap();
        Map<String, Double> codeToBidMap = Maps.newHashMap();
        codeToAskMap.put(futuCode, 1.01);
        codeToBidMap.put(futuCode, 0.98);
        optionTradeExecutor.setCodeToAskMap(codeToAskMap);
        optionTradeExecutor.setCodeToBidMap(codeToBidMap);
        optionTradeExecutor.calculateMidPrice(futuCode);

        Thread.sleep(5000);
        codeToAskMap.put(futuCode, 1.01);
        codeToBidMap.put(futuCode, 0.98);
        optionTradeExecutor.calculateMidPrice(futuCode);
        optionTradeExecutor.setCodeToBidMap(codeToBidMap);

        Thread.sleep(5000);
        codeToAskMap.put(futuCode, 0.99);
        codeToBidMap.put(futuCode, 0.96);
        optionTradeExecutor.calculateMidPrice(futuCode);
        optionTradeExecutor.setCodeToBidMap(codeToBidMap);

        Thread.sleep(5000);
        codeToAskMap.put(futuCode, 1.6);
        codeToBidMap.put(futuCode, 1.0);
        optionTradeExecutor.calculateMidPrice(futuCode);
        optionTradeExecutor.setCodeToBidMap(codeToBidMap);

        for (int i = 0; i < 7; i++) {
            Thread.sleep(5000);
            codeToAskMap.put(futuCode, 1.0);
            codeToBidMap.put(futuCode, 0.5);
            optionTradeExecutor.calculateMidPrice(futuCode);
            optionTradeExecutor.setCodeToBidMap(codeToBidMap);
        }
    }
}
