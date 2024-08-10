package start.strategy;

import com.google.common.collect.Maps;
import luonq.execute.GrabOptionTradeData;
import luonq.execute.LoadOptionTradeData;
import luonq.execute.ReadWriteOptionTradeInfo;
import luonq.listener.OptionStockListener;
import luonq.polygon.OptionTradeExecutor;
import luonq.polygon.RealTimeDataWS_DB;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import start.BaseTest;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashMap;
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
        RealTimeDataWS_DB client = new RealTimeDataWS_DB();
        client.setCloseCheckTime(new Date());
        client.setOpenTime(1720531800000l);
        HashMap<String, String> canTradeOptionForRtIVMap = Maps.newHashMap();
        canTradeOptionForRtIVMap.put("NKLA", "NKLA++240719C00012000|NKLA++240719P00011000");
        optionTradeExecutor.setClient(client);
        optionTradeExecutor.setCanTradeOptionForRtIVMap(canTradeOptionForRtIVMap);
        optionTradeExecutor.getRealTimeIV();
        System.out.println();
    }

    @Test
    public void test_begintrade() throws Exception {
        loadOptionTradeData.load();
        ReadWriteOptionTradeInfo.init();
        RealTimeDataWS_DB client = new RealTimeDataWS_DB();
        client.setOpenTime(1721024787753l);
        client.setCloseCheckTime(Date.from(LocalDateTime.now().plusDays(1).withHour(3).withMinute(59).withSecond(0).withNano(0).toInstant(ZoneOffset.of("+8"))));
        String stock = "TSLA";
        double open = 261.0;
        String callRt = "TSLA++240719C00230000";
        String putRt = "TSLA++240719C00220000";
        RealTimeDataWS_DB.realtimeQuoteForOptionMap.put(stock, open);

        OptionStockListener optionStockListener = new OptionStockListener();
        optionTradeExecutor.setClient(client);
        optionTradeExecutor.setOptionStockListener(optionStockListener);
        optionStockListener.setOptionTradeExecutor(optionTradeExecutor);
        optionTradeExecutor.init();
        optionTradeExecutor.getRealTimeIV();

        optionStockListener.cal(stock, open);

        Map<String, Double> optionRtIvMap = optionTradeExecutor.getOptionRtIvMap();
        optionRtIvMap.put(callRt, 0.8);
        optionRtIvMap.put(putRt, 0.8);

        Map<String, Double> codeToAskMap = optionTradeExecutor.getFutuQuote().getCodeToAskMap();
        Map<String, Double> codeToBidMap = optionTradeExecutor.getFutuQuote().getCodeToBidMap();
        codeToAskMap.put("TSLA240705C230000", 0.23);
        codeToBidMap.put("TSLA240705P220000", 0.22);

        optionTradeExecutor.beginTrade();
    }

    @Test
    public void test_calTradePrice() throws Exception {
        loadOptionTradeData.load();
        String stock = "TSLA";
        double open = 262.7;
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

        Map<String, Double> codeToAskMap = optionTradeExecutor.getFutuQuote().getCodeToAskMap();
        Map<String, Double> codeToBidMap = optionTradeExecutor.getFutuQuote().getCodeToBidMap();
        codeToAskMap.put("TSLA240705C230000", 0.23);
        codeToBidMap.put("TSLA240705P220000", 0.22);

        optionTradeExecutor.calTradePrice(stock, callRt, "C");
    }

    @Test
    public void test_restart() throws Exception {
        OptionStockListener optionStockListener = new OptionStockListener();
        optionTradeExecutor.setOptionStockListener(optionStockListener);
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
        optionTradeExecutor.calQuoteMidPrice(futuCode);

        Thread.sleep(5000);
        codeToAskMap.put(futuCode, 1.01);
        codeToBidMap.put(futuCode, 0.98);
        optionTradeExecutor.calQuoteMidPrice(futuCode);
        optionTradeExecutor.setCodeToBidMap(codeToBidMap);

        Thread.sleep(5000);
        codeToAskMap.put(futuCode, 0.99);
        codeToBidMap.put(futuCode, 0.96);
        optionTradeExecutor.calQuoteMidPrice(futuCode);
        optionTradeExecutor.setCodeToBidMap(codeToBidMap);

        Thread.sleep(5000);
        codeToAskMap.put(futuCode, 1.6);
        codeToBidMap.put(futuCode, 1.0);
        optionTradeExecutor.calQuoteMidPrice(futuCode);
        optionTradeExecutor.setCodeToBidMap(codeToBidMap);

        for (int i = 0; i < 7; i++) {
            Thread.sleep(5000);
            codeToAskMap.put(futuCode, 1.0);
            codeToBidMap.put(futuCode, 0.5);
            optionTradeExecutor.calQuoteMidPrice(futuCode);
            optionTradeExecutor.setCodeToBidMap(codeToBidMap);
        }
    }
}
