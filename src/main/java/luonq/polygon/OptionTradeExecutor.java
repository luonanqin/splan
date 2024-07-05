package luonq.polygon;

import bean.NodeList;
import bean.OptionRT;
import bean.OptionRTResp;
import bean.OrderFill;
import bean.StockPosition;
import com.alibaba.fastjson.JSON;
import com.futu.openapi.FTAPI;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import luonq.execute.LoadOptionTradeData;
import luonq.execute.ReadWriteOptionTradeInfo;
import luonq.futu.BasicQuote;
import luonq.futu.GetOptionChain;
import luonq.futu.TradeApi;
import luonq.listener.OptionStockListener;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import util.BaseUtils;
import util.Constants;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Created by Luonanqin on 2023/5/9.
 */
@Component
@Data
@Slf4j
public class OptionTradeExecutor {

    private NodeList list;
    private BasicQuote futuQuote;
    private GetOptionChain getOptionChain;
    private int cut = 990000;
    private List<String> tradeStock = Lists.newArrayList();
    private RealTimeDataWS_DB client;
    private boolean realTrade = true;
    private OptionStockListener optionStockListener;
    private TradeApi tradeApi;

    private static long CANCEL_BUY_ORDER_TIME_MILLI = 10 * 60 * 1000; // 开盘后10分钟撤销买入订单
    private static long STOP_MONITORY_BUY_ORDER_TIME_MILLI = 11 * 60 * 1000; // 开盘后11分钟停止监听买入订单的成交
    private static long STOP_MONITORY_SELL_ORDER_TIME_MILLI = 12 * 60 * 1000; // 开盘后12分钟如果没有买入成交的订单，则停止监听卖出订单的成交
    private static long STOP_LOSS_GAIN_INTERVAL_TIME_MILLI = 2 * 60 * 1000; // 开盘后2分钟才开始监听止损和止盈
    private static long ORDER_INTERVAL_TIME_MILLI = 10 * 1000; // 下单后检查间隔时间
    private static String CALL_TYPE = "C";
    private static String PUT_TYPE = "P";
    public static final double STOP_LOSS_RATIO = -0.2d; // 全交易时段的止损比例
    public static final double STOP_GAIN_RATIO_1 = 0.2d; // 三小时前的止盈比例
    public static final double STOP_GAIN_RATIO_2 = 0.1d; // 三小时后的止盈比例
    public static final long STOP_GAIN_INTERVAL_TIME_LINE = 3 * 60 * 60 * 1000L; // 三小时止盈时间点

    private Set<String> hasBoughtOrder = Sets.newHashSet(); // 已下单买入
    private Set<String> hasSoldOrder = Sets.newHashSet(); // 已下单卖出
    private Set<String> hasBoughtSuccess = Sets.newHashSet(); // 已成交买入
    private Set<String> hasSoldSuccess = Sets.newHashSet(); // 已成交卖出
    private Map<String/* futu option */, Long/* orderId */> buyOrderIdMap = Maps.newHashMap(); // 下单买入的订单id
    private Map<String/* futu option */, Long/* orderId */> sellOrderIdMap = Maps.newHashMap(); // 下单卖出的订单id
    private Map<String/* stock */, Long/* order time */> buyOrderTimeMap = Maps.newHashMap(); // 下单买入时的时间
    private Map<String/* stock */, Long/* order time */> sellOrderTimeMap = Maps.newHashMap(); // 下单卖出时的时间
    private Map<String/* stock */, Double/* order count */> orderCountMap = Maps.newHashMap(); // 下单买入的数量，卖出可以直接使用

    public ThreadPoolExecutor threadPool = new ThreadPoolExecutor(10, 10, 3600, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

    // 能交易的股票
    private Set<String> canTradeStocks = Sets.newHashSet();
    // code的实时iv
    private Map<String, Double> optionRtIvMap = Maps.newHashMap();
    // 股票对应实时iv code
    private Map<String, String> canTradeOptionForRtIVMap = Maps.newHashMap();
    // 实时iv code对应富途code
    private Map<String, String> optionCodeMap = Maps.newHashMap();
    // 股票对应富途code
    private Map<String, String> canTradeOptionForFutuMap = Maps.newHashMap();
    // 股票的实时报价
    private Map<String, Double> realtimeQuoteForOptionMap = Maps.newHashMap();
    // 期权对应行权价
    private Map<String, Double> optionStrikePriceMap = Maps.newHashMap();
    // 期权对应行到期日
    private Map<String, String> optionExpireDateMap = Maps.newHashMap();
    // 无风险利率
    private double riskFreeRate;
    // 当前合法交易日
    private String currentTradeDate;

    public void init() {
        FTAPI.init();
        futuQuote = new BasicQuote();
        futuQuote.start();
        tradeApi = new TradeApi();
        tradeApi.useSimulateEnv();
        tradeApi.setAccountId(TradeApi.simulateUsOptionAccountId);
        //        tradeApi.useRealEnv();
        tradeApi.start();
        tradeApi.unlock();
        //        tradeApi.clearStopLossStockSet();

        canTradeOptionForRtIVMap = optionStockListener.getCanTradeOptionForRtIVMap();
        optionCodeMap = optionStockListener.getOptionCodeMap();
        canTradeOptionForFutuMap = optionStockListener.getCanTradeOptionForFutuMap();
        optionStrikePriceMap = optionStockListener.getOptionStrikePriceMap();
        optionExpireDateMap = optionStockListener.getOptionExpireDateMap();
        optionCodeMap = optionStockListener.getOptionCodeMap();
        canTradeOptionForRtIVMap = optionStockListener.getCanTradeOptionForRtIVMap();
        realtimeQuoteForOptionMap = RealTimeDataWS_DB.realtimeQuoteForOptionMap;
        riskFreeRate = LoadOptionTradeData.riskFreeRate;
        currentTradeDate = LoadOptionTradeData.currentTradeDate;
        canTradeStocks = optionStockListener.getCanTradeStocks();
    }

    public void beginTrade() throws InterruptedException {
        String today = LocalDate.now().format(Constants.DB_DATE_FORMATTER);
        // 能交易的股票
        if (CollectionUtils.isEmpty(canTradeStocks)) {
            log.info("there is no stock can be traded");
            return;
        }
        log.info("there are stock can be traded. stock: {}", canTradeStocks);
        // 监听实时iv
        getRealTimeIV(canTradeOptionForRtIVMap);

        // 均分账户资金
        //        double funds = tradeApi.getFunds();
        //        double funds = 10000d / 7.3d; // todo 测试用要删
        double funds = 10000d; // todo 测试用要删
        double avgFund = (int) funds / canTradeStocks.size();
        log.info("funds: {}, avgFund: {}", funds, avgFund);

        // 监听富途报价
        List<String> futuOptionList = canTradeOptionForFutuMap.values().stream().flatMap(o -> Arrays.stream(o.split("\\|"))).collect(Collectors.toList());
        for (String optionCode : futuOptionList) {
            futuQuote.subOrderBook(optionCode);
        }
        log.info("monitor option quote list: {}", futuOptionList);

        threadPool.execute(() -> monitorBuyOrder());
        threadPool.execute(() -> monitorSellOrder());
        stopLossAndGain();

        while (true) {
            for (String stock : canTradeStocks) {
                if (hasBoughtOrder.contains(stock)) {
                    continue;
                }

                String callAndPut = canTradeOptionForRtIVMap.get(stock);
                String[] split = callAndPut.split("\\|");
                String callRt = split[0];
                String putRt = split[1];
                Double callIv = optionRtIvMap.get(callRt);
                Double putIv = optionRtIvMap.get(putRt);
                if (callIv == null || putIv == null) {
                    continue;
                }

                String call = callRt.replaceAll("\\+", "");
                String put = putRt.replaceAll("\\+", "");
                Double stockPrice = realtimeQuoteForOptionMap.get(stock);
                if (stockPrice == null) {
                    log.warn("wait {} quote", stock);
                    continue;
                }

                String callFutu = optionCodeMap.get(callRt);
                String putFutu = optionCodeMap.get(putRt);
                Map<String, String> codeToQuoteMap = futuQuote.getCodeToQuoteMap();
                String callQuote = codeToQuoteMap.get(callFutu);
                String putQuote = codeToQuoteMap.get(putFutu);
                if (StringUtils.isAnyBlank(callQuote, putQuote)) {
                    invalidTradeStock(stock);
                    log.info("there is no legal option quote. stock={}", stock);
                    continue;
                }

                double callTradePrice = calTradePrice(stock, callRt, CALL_TYPE);
                double putTradePrice = calTradePrice(stock, putRt, PUT_TYPE); // todo 重点测试各种价格

                // 如果下单数量小数位小于等于0.5，取整要减一，如果小数位大于0.5，则不变。目的是为了后面如果改价避免成交数量超过限制
                double countDouble = avgFund / (callTradePrice + putTradePrice) / 100;
                String countStr = String.valueOf(countDouble);
                int dotIndex = countStr.indexOf(".");
                String dotStr = countStr.substring(dotIndex + 1, dotIndex + 2);
                double count = (int) countDouble;
                if (Integer.valueOf(dotStr) <= 5) {
                    count = (int) countDouble - 1;
                }

                if (callTradePrice < 0.1 || putTradePrice < 0.1) {
                    invalidTradeStock(stock);
                    log.info("option price less than 0.1. don't trade stock={}\tcall={}\tcallPrice={}\tput={}\tputPrice={}", stock, call, callTradePrice, put, putTradePrice);
                    continue;
                }

                long buyCallOrderId = tradeApi.placeNormalBuyOrder(callFutu, count, callTradePrice);
                long buyPutOrderId = tradeApi.placeNormalBuyOrder(putFutu, count, putTradePrice);
                log.info("simulate trade: buyCallOrder={}\tcall={}\tcallPrice={}\tbuyPutOrder={}\tput={}\tputPrice={}\tcount={}", buyCallOrderId, call, callTradePrice, buyPutOrderId, put, putTradePrice, count);

                long curTime = System.currentTimeMillis();
                orderCountMap.put(stock, count);
                buyOrderTimeMap.put(stock, curTime);
                buyOrderIdMap.put(callFutu, buyCallOrderId);
                buyOrderIdMap.put(putFutu, buyPutOrderId);
                ReadWriteOptionTradeInfo.writeOrderCount(stock, count);
                ReadWriteOptionTradeInfo.writeBuyOrderTime(stock, curTime);
                ReadWriteOptionTradeInfo.writeBuyOrderId(callFutu, buyCallOrderId);
                ReadWriteOptionTradeInfo.writeBuyOrderId(putFutu, buyPutOrderId);
                ReadWriteOptionTradeInfo.writeHasBoughtOrder(stock);
                hasBoughtOrder.add(stock);
            }
            if (canTradeStocks.size() == hasBoughtOrder.size()) {
                //                RealTimeDataWS_DB.getRealtimeQuoteForOption = false;
                break;
            }
        }

        while (true) {
            if (canTradeStocks.size() == hasBoughtSuccess.size() && hasBoughtSuccess.size() == hasSoldSuccess.size()) {
                RealTimeDataWS_DB.getRealtimeQuoteForOption = false;
                Thread.sleep(5000);
                return;
            }
            Thread.sleep(10000);
        }
    }

    public double calTradePrice(String stock, String ivRt, String optionType) {
        Double iv = optionRtIvMap.get(ivRt);
        String option = ivRt.replaceAll("\\+", "");
        Double strikePrice = optionStrikePriceMap.get(option);
        String expireDate = optionExpireDateMap.get(option);
        Double stockPrice = realtimeQuoteForOptionMap.get(stock);
        String futu = optionCodeMap.get(ivRt);
        Map<String, String> codeToQuoteMap = futuQuote.getCodeToQuoteMap();
        String quote = codeToQuoteMap.get(futu);

        double predPrice;
        if (StringUtils.equalsAnyIgnoreCase(optionType, CALL_TYPE)) {
            predPrice = BaseUtils.getCallPredictedValue(stockPrice, strikePrice, riskFreeRate, iv, currentTradeDate, expireDate);
        } else {
            predPrice = BaseUtils.getPutPredictedValue(stockPrice, strikePrice, riskFreeRate, iv, currentTradeDate, expireDate);
        }
        log.info("calculate predicate price. stock={}\toptionCode={}\tprice={}\tStrikePrice={}\tiv={}\tpredPrice={}", stock, option, stockPrice, strikePrice, iv, predPrice);

        String[] quoteSplit = quote.split("\\|");
        double bidPrice = Double.parseDouble(quoteSplit[0]);
        double askPrice = Double.parseDouble(quoteSplit[1]);
        double midPrice = BigDecimal.valueOf((bidPrice + askPrice) / 2).setScale(2, RoundingMode.UP).doubleValue();
        log.info("monitor option quote detail: optionCode={}\toptionBid={}\toptionAsk={}\toptionMid={}", futu, bidPrice, askPrice, midPrice);

        double tradePrice;
        if (predPrice < bidPrice || predPrice > midPrice) {
            tradePrice = midPrice;
        } else {
            tradePrice = predPrice;
        }
        return tradePrice;
    }

    public void getRealTimeIV(Map<String, String> canTradeOptionForRtIVMap) {
        if (MapUtils.isEmpty(canTradeOptionForRtIVMap)) {
            log.info("there is no canTradeOptionForRtIVMap");
            return;
        }

        HttpClient httpClient = new HttpClient();
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {

            int showtimes = 10, showCount = 0;

            @Override
            public void run() {
                if (MapUtils.isEmpty(canTradeOptionForRtIVMap)) {
                    return;
                }
                String options = canTradeOptionForRtIVMap.values().stream().flatMap(o -> Arrays.stream(o.split("\\|"))).collect(Collectors.joining(","));
                String url = "https://restapi.ivolatility.com/equities/rt/options-rawiv?apiKey=5uO8Wid7AY945OJ2&symbols=" + options;

                List<String> results = Lists.newArrayList();
                GetMethod get = new GetMethod(url);
                try {
                    long curent = System.currentTimeMillis();
                    httpClient.executeMethod(get);
                    InputStream content = get.getResponseBodyAsStream();
                    OptionRTResp resp = JSON.parseObject(content, OptionRTResp.class);
                    List<OptionRT> data = resp.getData();
                    if (CollectionUtils.isNotEmpty(data)) {
                        for (OptionRT rt : data) {
                            String symbol = rt.getSymbol();
                            String timestamp = rt.getTimestamp();
                            double iv = rt.getIv();
                            String line = symbol + "\t" + timestamp + "\t" + iv + "\t" + curent;
                            results.add(line);
                            if (timestamp.contains("T09:2")) {
                                continue;
                            }
                            optionRtIvMap.put(symbol.replaceAll(" ", "+"), iv);
                        }

                        showCount++;
                        if (showCount == showtimes) {
                            log.info("rt iv data: {}", results);
                            showCount = 0;
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, 0, 2000);
    }

    public void monitorBuyOrder() {
        log.info("monitor buy order");
        long openTime = client.getOpenTime();
        while (true) {
            for (String stock : hasBoughtOrder) {
                if (hasBoughtSuccess.contains(stock)) {
                    continue;
                }

                Double count = orderCountMap.get(stock);

                String callAndPut = canTradeOptionForRtIVMap.get(stock);
                String[] split = callAndPut.split("\\|");
                String callRt = split[0];
                String putRt = split[1];
                String call = optionCodeMap.get(callRt);
                String put = optionCodeMap.get(putRt);

                Long buyCallOrderId = buyOrderIdMap.get(call);
                Long buyPutOrderId = buyOrderIdMap.get(put);
                Map<String, StockPosition> positionMap = tradeApi.getPositionMap(call, put);
                StockPosition callPosition = positionMap.get(call);
                StockPosition putPosition = positionMap.get(put);
                Double callTradeCount = callPosition == null ? 0 : callPosition.getCanSellQty();
                Double putTradeCount = putPosition == null ? 0 : putPosition.getCanSellQty();
                boolean callSuccess = callTradeCount.compareTo(count) >= 0;
                boolean putSuccess = putTradeCount.compareTo(count) >= 0;
                if (callSuccess && putSuccess) {
                    ReadWriteOptionTradeInfo.writeHasBoughtSuccess(stock);
                    hasBoughtSuccess.add(stock);
                    log.info("{} buy trade success. call={}\torderId={}\tput={}\torderId={}\tcount={}", stock, call, buyCallOrderId, put, buyPutOrderId, count);
                } else if (System.currentTimeMillis() - buyOrderTimeMap.get(stock) > ORDER_INTERVAL_TIME_MILLI) {
                    if (!callSuccess) {
                        double tradePrice = calTradePrice(stock, callRt, CALL_TYPE);
                        long modifyOrderId = tradeApi.upOrderPrice(buyCallOrderId, count, tradePrice);
                        log.info("modify buy call order: orderId={}\tcall={}\ttradePrice={}\tcount={}\tremainCount={}", modifyOrderId, call, tradePrice, count, count - callTradeCount);
                    }
                    if (!putSuccess) {
                        double tradePrice = calTradePrice(stock, putRt, PUT_TYPE);
                        long modifyOrderId = tradeApi.upOrderPrice(buyPutOrderId, count, tradePrice);
                        log.info("modify buy put order: orderId={}\tput={}\ttradePrice={}\tcount={}\tremainCount={}", modifyOrderId, put, tradePrice, count, count - putTradeCount);
                    }
                    long curTime = System.currentTimeMillis();
                    buyOrderTimeMap.put(stock, curTime);
                    ReadWriteOptionTradeInfo.writeBuyOrderTime(stock, curTime);
                }
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                log.error("ignore1", e);
            }

            // 11分钟后监听结束直接返回
            //            long current = System.currentTimeMillis();
            //            if (current - openTime >= STOP_MONITORY_BUY_ORDER_TIME_MILLI) {
            //                log.info("time is over 11min. stop monitor buy order");
            //                return;
            //            }

            if (canTradeStocks.size() == hasBoughtSuccess.size()) {
                log.info("all stock has bought: {}. stop monitor buy order", canTradeStocks);
                return;
            }
        }
    }

    public void monitorSellOrder() {
        log.info("monitor sell order");
        long openTime = client.getOpenTime();
        while (true) {
            long current = System.currentTimeMillis();
            //            if (!hasBoughtSuccess.isEmpty()) {
            for (String stock : hasSoldOrder) {
                if (hasSoldSuccess.contains(stock)) {
                    continue;
                }

                Double count = orderCountMap.get(stock);

                String callAndPut = canTradeOptionForRtIVMap.get(stock);
                String[] split = callAndPut.split("\\|");
                String callRt = split[0];
                String putRt = split[1];
                String call = optionCodeMap.get(callRt);
                String put = optionCodeMap.get(putRt);

                Long sellCallOrderId = sellOrderIdMap.get(call);
                Long sellPutOrderId = sellOrderIdMap.get(put);
                Map<String, StockPosition> positionMap = tradeApi.getPositionMap(call, put);
                StockPosition callPosition = positionMap.get(call);
                StockPosition putPosition = positionMap.get(put);
                Double callTradeCount = callPosition == null ? 0 : callPosition.getCanSellQty();
                Double putTradeCount = putPosition == null ? 0 : putPosition.getCanSellQty();
                boolean callSuccess = callTradeCount.compareTo(count) >= 0;
                boolean putSuccess = putTradeCount.compareTo(count) >= 0;
                if (callSuccess && putSuccess) {
                    ReadWriteOptionTradeInfo.writeHasSoldSuccess(stock);
                    hasSoldSuccess.add(stock);
                    canTradeOptionForRtIVMap.remove(stock);
                    log.info("{} sell trade success. call={}\torderId={}\tput={}\torderId={}", stock, call, sellCallOrderId, put, sellPutOrderId);
                } else if (System.currentTimeMillis() - sellOrderTimeMap.get(stock) > ORDER_INTERVAL_TIME_MILLI) {
                    if (!callSuccess) {
                        double tradePrice = calTradePrice(stock, callRt, CALL_TYPE);
                        long modifyOrderId = tradeApi.upOrderPrice(sellCallOrderId, count, tradePrice);
                        log.info("modify sell call order: orderId={}\tcall={}\ttradePrice={}\tcount={}\tremainCount={}", modifyOrderId, call, tradePrice, count, count - callTradeCount);
                    }
                    if (!putSuccess) {
                        double tradePrice = calTradePrice(stock, putRt, PUT_TYPE);
                        long modifyOrderId = tradeApi.upOrderPrice(sellPutOrderId, count, tradePrice);
                        log.info("modify sell put order: orderId={}\tput={}\ttradePrice={}\tcount={}\tremainCount={}", modifyOrderId, put, tradePrice, count, count - callTradeCount);
                    }
                    long curTime = System.currentTimeMillis();
                    sellOrderTimeMap.put(stock, curTime);
                    ReadWriteOptionTradeInfo.writeSellOrderTime(stock, curTime);
                }
            }

            //                // 12分钟后有买入成交，当卖出订单数量等于买入成交数量时，监听结束直接返回
            //                if (current - openTime >= STOP_MONITORY_SELL_ORDER_TIME_MILLI) {
            //                    if (hasSoldOrder.size() == hasBoughtSuccess.size()) {
            //                        log.info("time is over 12min. all buy order has been sold");
            //                        return;
            //                    }
            //                }
            //            } else {
            //                // 12分钟后没有买入成交，监听结束直接返回
            //                if (current - openTime >= STOP_MONITORY_SELL_ORDER_TIME_MILLI) {
            //                    log.info("time is over 12min. there is no buy order. stop monitor sell order ");
            //                    return;
            //                }
            //            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                log.error("ignore2", e);
            }
            // 如果可交易数量=买入成功数量，且买入成功数量=卖出成功数量
            if (canTradeStocks.size() == hasBoughtSuccess.size() && hasBoughtSuccess.size() == hasSoldSuccess.size()) {
                log.info("all stock has sold: {}. stop monitor buy order", canTradeStocks);
                return;
            }
        }
    }

    /**
     * 1.有止损流程：
     * 买入下单等待成交->已买入->触发止损->卖出下单等待成交->已卖出
     * 买入下单待成交 ≥ 已买入 ≥ 触发止损 = 卖出下单等待成交 = 已卖出
     * 2.无止损流程：
     * 买入下单等待成交->已买入->到时间卖出->卖出下单等待成交->已卖出
     * 买入下单待成交 ≥ 已买入 = 卖出下单等待成交 = 已卖出
     * <p>
     * 开盘2分钟以后才开始止损和止盈
     */
    public void stopLossAndGain() {
        Map<String, String> codeToQuoteMap = futuQuote.getCodeToQuoteMap();
        long openTime = client.getOpenTime();
        long closeTime = client.getCloseCheckTime().getTime();
        long monitorTime = openTime + STOP_LOSS_GAIN_INTERVAL_TIME_MILLI;
        long stopGainTime = openTime + STOP_GAIN_INTERVAL_TIME_LINE;
        long cur = System.currentTimeMillis();
        long delay = monitorTime - cur;
        if (cur > monitorTime) {
            delay = 0;
        }
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {

            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                for (String stock : hasBoughtSuccess) {
                    if (hasSoldOrder.contains(stock)) {
                        continue;
                    }

                    String callAndPut = canTradeOptionForFutuMap.get(stock);
                    String[] split = callAndPut.split("\\|");
                    String call = split[0];
                    String put = split[1];

                    String callQuote = codeToQuoteMap.get(call);
                    String putQuote = codeToQuoteMap.get(put);
                    if (StringUtils.isAnyBlank(callQuote, putQuote)) {
                        continue;
                    }

                    String[] callQuoteSplit = callQuote.split("\\|");
                    String[] putQuoteSplit = putQuote.split("\\|");
                    double callBidPrice = Double.parseDouble(callQuoteSplit[0]);
                    double callAskPrice = Double.parseDouble(callQuoteSplit[1]);
                    double callMidPrice = BigDecimal.valueOf((callBidPrice + callAskPrice) / 2).setScale(2, RoundingMode.DOWN).doubleValue();
                    double putBidPrice = Double.parseDouble(putQuoteSplit[0]);
                    double putAskPrice = Double.parseDouble(putQuoteSplit[1]);
                    double putMidPrice = BigDecimal.valueOf((putBidPrice + putAskPrice) / 2).setScale(2, RoundingMode.DOWN).doubleValue();

                    // 根据已成交的订单查看买入价，不断以最新报价计算是否触发止损价（不能以当前市价止损，因为流动性不够偏差会很大）
                    StockPosition callPosition = tradeApi.getPositionMap(call).get(call);
                    StockPosition putPosition = tradeApi.getPositionMap(put).get(put);
                    // 测试用 start ---------------
                    //                    callPosition = new StockPosition();
                    //                    callPosition.setCostPrice(1.03);
                    //                    callPosition.setCanSellQty(54);
                    //                    putPosition = new StockPosition();
                    //                    putPosition.setCostPrice(0.78);
                    //                    putPosition.setCanSellQty(54);
                    // 测试用 end -----------------
                    double callOpen = callPosition.getCostPrice();
                    double putOpen = putPosition.getCostPrice();
                    double callDiff = callMidPrice - callOpen;
                    double putDiff = putMidPrice - putOpen;
                    double allDiff = BigDecimal.valueOf(callDiff + putDiff).setScale(2, RoundingMode.HALF_UP).doubleValue();
                    double diffRatio = BigDecimal.valueOf(allDiff / (callOpen + putOpen)).setScale(2, RoundingMode.HALF_UP).doubleValue();
                    double callCount = callPosition.getCanSellQty();
                    double putCount = putPosition.getCanSellQty();
                    if ((currentTime / 1000) % 10 == 0) {
                        log.info("monitor loss and gain. call={}\tcallOpen={}\tcallBid={}\tcallAsk={}\tcallMid={}\tput={}\tputOpen={}\tputBid={}\tputAsk={}\tputMid={}\tcallDiff={}\tputDiff={}\tallDiff={}\tdiffRatio={}",
                          call, callOpen, callBidPrice, callAskPrice, callMidPrice, put, putOpen, putBidPrice, putAskPrice, putMidPrice, callDiff, putDiff, allDiff, diffRatio);
                    }

                    /**
                     * 1.全交易时段止损都是-20%
                     * 2.开盘三小时前止盈是20%
                     * 3.开盘三小时后止盈是10%
                     * 4.如果未到止盈线，收盘前会卖出
                     * 这么做的目的是三小时前交易活跃波动较大，所以止盈可以设置高一些。三小时之后则尽快止盈退出避免损失
                     */
                    if (diffRatio < STOP_LOSS_RATIO || (currentTime < stopGainTime && diffRatio > STOP_GAIN_RATIO_1) || (currentTime > stopGainTime && diffRatio > STOP_GAIN_RATIO_2)) {
                        long sellCallOrderId = tradeApi.placeNormalSellOrder(call, callCount, callMidPrice);
                        long sellPutOrderId = tradeApi.placeNormalSellOrder(put, putCount, putMidPrice);

                        long curTime = System.currentTimeMillis();
                        sellOrderTimeMap.put(stock, curTime);
                        sellOrderIdMap.put(call, sellCallOrderId);
                        sellOrderIdMap.put(put, sellPutOrderId);
                        ReadWriteOptionTradeInfo.writeSellOrderTime(stock, curTime);
                        ReadWriteOptionTradeInfo.writeSellOrderId(call, sellCallOrderId);
                        ReadWriteOptionTradeInfo.writeSellOrderId(put, sellPutOrderId);
                        ReadWriteOptionTradeInfo.writeHasSoldOrder(stock);
                        hasSoldOrder.add(stock);

                        log.info("stop loss and gain order. call={}\tcallOpen={}\tcallBid={}\tcallAsk={}\tcallMid={}\tput={}\tputOpen={}\tputBid={}\tputAsk={}\tputMid={}\tcallDiff={}\tputDiff={}\tallDiff={}\tdiffRatio={}\tsellCallOrder={}\tsellPutOrder={}",
                          call, callOpen, callBidPrice, callAskPrice, callMidPrice, put, putOpen, putBidPrice, putAskPrice, putMidPrice, callDiff, putDiff, allDiff, diffRatio, sellCallOrderId, sellPutOrderId);
                    }
                }
                // 11分钟后没有买入成功的，监听结束直接返回
                //                    if (currentTime - openTime > STOP_MONITORY_BUY_ORDER_TIME_MILLI && hasBoughtSuccess.isEmpty()) {
                //                        log.info("time is over 11min. there is no buy trade to monitor stop loss.");
                //                        return;
                //                    }

                if (currentTime > closeTime || canTradeStocks.size() == hasSoldSuccess.size()) {
                    // 反注册报价监听
                    RealTimeDataWS_DB.getRealtimeQuoteForOption = false;
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        log.error("ignore4 ", e);
                    }
                    log.info("time is close to the close time or all stock has sold. stop monitor");
                    return; // todo  结束了如何停止timer
                }
            }
        }, delay, 500);
    }

    public void cancelOrder() {
        try {
            Map<String, Long> orderMap = tradeApi.getOrderList();
            for (String code : orderMap.keySet()) {
                Long orderId = orderMap.get(code);
                OrderFill orderFill = tradeApi.getOrderFill(orderId);
                if (orderFill == null) {
                    tradeApi.cancelOrder(orderId);
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void restart() {
        Map<String, StockPosition> positionMap = tradeApi.getPositionMap(null);
        Map<String/* stock */, String/* call */> stockToCallMap = Maps.newHashMap();
        Map<String/* stock */, String/* put */> stockToPutMap = Maps.newHashMap();
        Set<String> stocks = Sets.newHashSet();
        for (String code : positionMap.keySet()) {
            String stock = "";
            for (int i = 0; i < code.length(); i++) {
                char c = code.charAt(i);
                if (c == '2') {
                    stock = code.substring(0, i);
                    stocks.add(stock);
                    break;
                }
            }
            if (StringUtils.isBlank(stock)) {
                continue;
            }

            String optionTypeStr = code.substring(stock.length() + 6);
            if (StringUtils.startsWithIgnoreCase(optionTypeStr, "C")) {
                stockToCallMap.put(stock, code);
            } else {
                stockToPutMap.put(stock, code);
            }
        }
        log.info("current position: {}", positionMap);
        for (String stock : stocks) {
            if (stockToCallMap.containsKey(stock) && stockToPutMap.containsKey(stock)) {
                hasBoughtSuccess.add(stock);
                canTradeOptionForFutuMap.put(stock, stockToCallMap.get(stock) + "|" + stockToPutMap.get(stock));
            }
        }

        stopLossAndGain();
    }

    public void invalidTradeStock(String stock) {
        hasBoughtOrder.add(stock);
        hasBoughtSuccess.add(stock);
        hasSoldOrder.add(stock);
        hasSoldSuccess.add(stock);
        ReadWriteOptionTradeInfo.writeHasBoughtOrder(stock);
        ReadWriteOptionTradeInfo.writeHasBoughtSuccess(stock);
        ReadWriteOptionTradeInfo.writeHasSoldOrder(stock);
        ReadWriteOptionTradeInfo.writeHasSoldSuccess(stock);
        canTradeOptionForRtIVMap.remove(stock);
    }

    public static void main(String[] args) throws InterruptedException {
        double avgFund = 10000d;
        double callTradePrice = 1.2;
        double putTradePrice = 1.2;
        double countDouble = avgFund / (callTradePrice + putTradePrice) / 100;
        String countStr = String.valueOf(countDouble);
        int dotIndex = countStr.indexOf(".");
        String dotStr = countStr.substring(dotIndex + 1, dotIndex + 2);
        double count = (int) countDouble;
        if (Integer.valueOf(dotStr) <= 5) {
            count = (int) countDouble - 1;
        }
        System.out.println(count);
    }
}
