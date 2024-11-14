package start.strategy;

import com.futu.openapi.FTAPI;
import com.google.common.collect.Maps;
import luonq.execute.GrabOptionTradeData;
import luonq.execute.LoadOptionTradeData;
import luonq.execute.ReadWriteOptionTradeInfo;
import luonq.futu.BasicQuote;
import luonq.ibkr.TradeApi;
import luonq.listener.OptionStockListener;
import luonq.listener.OptionStockListener2;
import luonq.polygon.OptionTradeExecutor2;
import luonq.polygon.RealTimeDataWS_DB2;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import start.BaseTest;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class OptionTradeTest2 extends BaseTest {

    @Autowired
    private LoadOptionTradeData loadOptionTradeData;

    @Autowired
    private GrabOptionTradeData grabOptionTradeData;

    @Autowired
    private OptionTradeExecutor2 optionTradeExecutor;

    @Test
    public void test_grabOptionTradeData() {
        grabOptionTradeData.grab();
    }

    @Test
    public void test_getOptionChain() throws Exception {
        grabOptionTradeData.init();
        grabOptionTradeData.lastTradeDate = "2024-07-18";
        grabOptionTradeData.calCurrentTradeDate();
        grabOptionTradeData.stocks.add("WYNN");
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
        optionStockListener.cal("NKLA", 11.38); // 股价波动超过2%
    }

    @Test
    public void test_getRealTimeIV() throws Exception {
        RealTimeDataWS_DB2 client = new RealTimeDataWS_DB2();
        client.setCloseCheckTime(new Date());
        client.setOpenTime(1720531800000l);
        HashMap<String, String> canTradeOptionForRtIVMap = Maps.newHashMap();
        canTradeOptionForRtIVMap.put("NKLA", "NKLA++240719C00012000|NKLA++240719P00011000");
        optionTradeExecutor.setClient(client);
        optionTradeExecutor.setCanTradeOptionForRtIVMap(canTradeOptionForRtIVMap);
//        optionTradeExecutor.getRealTimeIV();
        System.out.println();
    }

    @Test
    public void test_begintrade() throws Exception {
        loadOptionTradeData.load();
        ReadWriteOptionTradeInfo.init();
        RealTimeDataWS_DB2 client = new RealTimeDataWS_DB2();
        client.setSeason(-4);
        client.setOpenTime(1723469400000l);
        client.setCloseCheckTime(Date.from(LocalDateTime.now().plusDays(1).withHour(3).withMinute(59).withSecond(0).withNano(0).toInstant(ZoneOffset.of("+8"))));
        String stock = "BEKE";
        double open = 15.7;
        String callRt = "BEKE++240816C00015500";
        String putRt = "BEKE++240816C00016000";
        RealTimeDataWS_DB2.realtimeQuoteForOptionMap.put(stock, open);

        OptionStockListener2 optionStockListener = new OptionStockListener2();
        optionTradeExecutor.setClient(client);
        optionTradeExecutor.setOptionStockListener(optionStockListener);
        optionStockListener.setOptionTradeExecutor(optionTradeExecutor);

        FTAPI.init();
        BasicQuote futuQuote = new BasicQuote();
        futuQuote.start();
        optionTradeExecutor.setFutuQuote(futuQuote);

        optionTradeExecutor.init();
        optionTradeExecutor.getFutuRealTimeIV();

        optionStockListener.cal(stock, open);

        Map<String, Double> optionRtIvMap = optionTradeExecutor.getOptionRtIvMap();
        optionRtIvMap.put(callRt, 0.8);
        optionRtIvMap.put(putRt, 0.8);

        Map<String, Double> codeToAskMap = optionTradeExecutor.getFutuQuote().getCodeToAskMap();
        Map<String, Double> codeToBidMap = optionTradeExecutor.getFutuQuote().getCodeToBidMap();
        codeToAskMap.put("U240816C15000", 0.63);
        codeToBidMap.put("U240816C15000", 0.43);
        codeToAskMap.put("U240816P14500", 0.83);
        codeToBidMap.put("U240816P14500", 0.42);

        optionTradeExecutor.beginTrade();
    }

    @Test
    public void test_calTradePrice() throws Exception {
        loadOptionTradeData.load();
        String stock = "TSLA";
        double open = 262.7;
        String callRt = "TSLA++240705C00230000";
        String putRt = "TSLA++240705C00220000";
        RealTimeDataWS_DB2.realtimeQuoteForOptionMap.put(stock, open);

        OptionStockListener2 optionStockListener = new OptionStockListener2();
        optionTradeExecutor.setOptionStockListener(optionStockListener);
        optionTradeExecutor.init();

        optionStockListener.cal(stock, open);

        Map<String, Double> optionRtIvMap = optionTradeExecutor.getOptionRtIvMap();
        optionRtIvMap.put(callRt, 0.8);
        optionRtIvMap.put(putRt, 0.8);

        Map<String, Double> codeToAskMap = optionTradeExecutor.getFutuQuote().getCodeToAskMap();
        Map<String, Double> codeToBidMap = optionTradeExecutor.getFutuQuote().getCodeToBidMap();
        codeToAskMap.put("TSLA240705C230000", 0.23);
        codeToBidMap.put("TSLA240705P220000", 0.22);

        optionTradeExecutor.calTradePrice(stock, callRt, "C", null);
    }

    @Test
    public void test_restart() throws Exception {
        loadOptionTradeData.load();
        ReadWriteOptionTradeInfo.init();

        FTAPI.init();
        BasicQuote futuQuote = new BasicQuote();
        futuQuote.start();
        optionTradeExecutor.setFutuQuote(futuQuote);

        TradeApi tradeApi = new TradeApi();
        optionTradeExecutor.setTradeApi(tradeApi);

        OptionStockListener2 optionStockListener = new OptionStockListener2();
        optionStockListener.setOptionTradeExecutor(optionTradeExecutor);
        optionTradeExecutor.setOptionStockListener(optionStockListener);

        RealTimeDataWS_DB2 client = new RealTimeDataWS_DB2();
        client.initManyTime();
        optionTradeExecutor.setClient(client);

        optionTradeExecutor.reSendOpenPrice();
        optionTradeExecutor.init();
        optionTradeExecutor.restart();
    }

    @Test
    public void test_readWrite(){
        loadOptionTradeData.load();
        ReadWriteOptionTradeInfo.init();

        ReadWriteOptionTradeInfo.writeBuyOrderCost("NVDA  240816C00115000", 1.43);
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
