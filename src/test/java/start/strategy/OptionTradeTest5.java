package start.strategy;

import com.futu.openapi.FTAPI;
import luonq.execute.LoadOptionTradeData;
import luonq.execute.ReadWriteOptionTradeInfo;
import luonq.futu.BasicQuote;
import luonq.ibkr.TradeApi;
import luonq.listener.OptionStockListener5;
import luonq.polygon.OptionTradeExecutor5;
import luonq.polygon.RealTimeDataWS_DB5;
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
        RealTimeDataWS_DB5 client = new RealTimeDataWS_DB5();
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
        RealTimeDataWS_DB5 client = new RealTimeDataWS_DB5();
        client.setOpenTime(1723469400000l);
        client.setCloseCheckTime(Date.from(LocalDateTime.now().plusDays(1).withHour(3).withMinute(59).withSecond(0).withNano(0).toInstant(ZoneOffset.of("+8"))));
        String stock = "BEKE";
        double open = 15.7;
        RealTimeDataWS_DB5.realtimeQuoteForOptionMap.put(stock, open);

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

        RealTimeDataWS_DB5 client = new RealTimeDataWS_DB5();
        client.setOpenTime(1723469400000l);
        client.setCloseCheckTime(Date.from(LocalDateTime.now().plusDays(1).withHour(3).withMinute(59).withSecond(0).withNano(0).toInstant(ZoneOffset.of("+8"))));
        optionTradeExecutor.setClient(client);

        optionTradeExecutor.init();
        optionTradeExecutor.restart();
    }

    @Test
    public void test_calQuoteMidPrice() throws Exception {
        OptionTradeExecutor5 optionTradeExecutor5 = new OptionTradeExecutor5();
        TradeApi tradeApi = new TradeApi(true);
        FTAPI.init();
        BasicQuote futuQuote = new BasicQuote();
        futuQuote.start();
        RealTimeDataWS_DB5 client = new RealTimeDataWS_DB5();
        client.setOpenTime(System.currentTimeMillis());
        client.setCloseCheckTime(new Date());
        optionTradeExecutor5.setClient(client);
        optionTradeExecutor5.setFutuQuote(futuQuote);
        optionTradeExecutor5.setTradeApi(tradeApi);
        optionTradeExecutor5.monitorFutuDeep("GOOG241025P160000");
        optionTradeExecutor5.monitorFutuDeep("GOOG241025P155000");
        optionTradeExecutor5.setOptionStockListener(new OptionStockListener5());
        optionTradeExecutor5.init();

        optionTradeExecutor5.getCanTradeOptionMap().put("GOOG", "O:GOOG241025C00170000|O:GOOG241025P00160000");
        optionTradeExecutor5.getCanTradeOption2Map().put("GOOG", "O:GOOG241025C00175000|O:GOOG241025P00155000");
        optionTradeExecutor5.getOptionForIbkrMap().put("O:GOOG241025P00160000", "GOOG  241025P00160000");
        optionTradeExecutor5.getOptionForIbkrMap().put("O:GOOG241025P00155000", "GOOG  241025P00155000");
        optionTradeExecutor5.getOptionForFutuMap().put("O:GOOG241025P00160000", "GOOG241025P160000");
        optionTradeExecutor5.getOptionForFutuMap().put("O:GOOG241025P00155000", "GOOG241025P155000");
        optionTradeExecutor5.placeOrder("GOOG");
    }
}
