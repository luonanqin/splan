package luonq.polygon;

import bean.NodeList;
import bean.OptionRT;
import bean.OptionRTResp;
import bean.Order;
import bean.StockEvent;
import bean.StockPosition;
import com.alibaba.fastjson.JSON;
import com.futu.openapi.FTAPI;
import com.futu.openapi.pb.TrdCommon;
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
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import util.BaseUtils;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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

import static util.Constants.TRADE_ERROR_CODE;
import static util.Constants.TRADE_PROHIBT_CODE;

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
    private static long ORDER_INTERVAL_TIME_MILLI = 1 * 1000; // 下单后检查间隔时间
    private static String CALL_TYPE = "C";
    private static String PUT_TYPE = "P";
    public static final double STOP_LOSS_RATIO = -0.2d; // 全交易时段的止损比例
    public static final double STOP_GAIN_RATIO_1 = 0.2d; // 三小时前的止盈比例
    public static final double STOP_GAIN_RATIO_2 = 0.1d; // 三小时后的止盈比例
    public static final long STOP_GAIN_INTERVAL_TIME_LINE = 3 * 60 * 60 * 1000L; // 三小时止盈时间点
    public static final int ADJUST_TRADE_PRICE_TIMES = 10; // 卖出挂单价调价次数上限
    public static final long ADJUST_TRADE_PRICE_TIME_INTERVAL = 5 * 1000L; // 卖出挂单价调价间隔5秒

    private static Integer EXIST = 1;
    private static Integer NOT_EXIST = 0;
    private Map<String, Integer> hasBoughtOrderMap = Maps.newHashMap(); // 已下单买入
    private Map<String, Integer> hasSoldOrderMap = Maps.newHashMap(); // 已下单卖出
    private Set<String> hasBoughtSuccess = Sets.newHashSet(); // 已成交买入
    private Set<String> hasSoldSuccess = Sets.newHashSet(); // 已成交卖出
    private Set<String> invalidStocks = Sets.newHashSet(); // 实际不能成交的股票，不用写入文件
    private boolean hasFinishBuying = false; // 已经完成买入
    private Map<String/* futu option */, Long/* orderId */> buyOrderIdMap = Maps.newHashMap(); // 下单买入的订单id
    private Map<String/* futu option */, Long/* orderId */> sellOrderIdMap = Maps.newHashMap(); // 下单卖出的订单id
    private Map<String/* stock */, Long/* order time */> buyOrderTimeMap = Maps.newHashMap(); // 下单买入时的时间
    private Map<String/* stock */, Long/* order time */> sellOrderTimeMap = Maps.newHashMap(); // 下单卖出时的时间
    private Map<String/* stock */, Double/* order count */> orderCountMap = Maps.newHashMap(); // 下单买入的数量，卖出可以直接使用
    private Map<String/* futu option */, Integer/* adjust trade price times */> optionAdjustPriceTimesMap = Maps.newHashMap(); // 卖出挂单价调价次数
    private Map<String/* futu option */, Long/* adjust trade price timestamp */> optionAdjustPriceTimestampMap = Maps.newHashMap(); // 卖出挂单价调价的最新时间

    public ThreadPoolExecutor threadPool = new ThreadPoolExecutor(10, 10, 3600, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

    // listener计算出能交易的股票，待过滤无效和排序截取
    private Set<String> canTradeStocks = Sets.newHashSet();
    // listener计算出能交易的股票及对应前日期权成交量
    Map<String, Long> stockLastOptionVolMap = Maps.newHashMap();
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
    // futu报价
    private Map<String, String> codeToQuoteMap = Maps.newHashMap();
    // 无风险利率
    // 平均到每个股票的交易金额
    private double avgFund;
    private double funds = 100000d; // todo 测试用要删
    private long closeTime;

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

        canTradeOptionForFutuMap = optionStockListener.getCanTradeOptionForFutuMap();
        optionStrikePriceMap = optionStockListener.getOptionStrikePriceMap();
        optionExpireDateMap = optionStockListener.getOptionExpireDateMap();
        optionCodeMap = optionStockListener.getOptionCodeMap();
        canTradeOptionForRtIVMap = optionStockListener.getCanTradeOptionForRtIVMap();
        realtimeQuoteForOptionMap = RealTimeDataWS_DB.realtimeQuoteForOptionMap;
        riskFreeRate = LoadOptionTradeData.riskFreeRate;
        currentTradeDate = LoadOptionTradeData.currentTradeDate;
        canTradeStocks = optionStockListener.getCanTradeStocks();
        stockLastOptionVolMap = optionStockListener.getStockLastOptionVolMap();
        codeToQuoteMap = futuQuote.getCodeToQuoteMap();
        closeTime = client.getCloseCheckTime().getTime();
    }

    public void beginTrade() throws InterruptedException {
        // 能交易的股票
        if (CollectionUtils.isEmpty(canTradeStocks)) {
            log.info("there is no stock can be traded");
            hasFinishBuying = true;
            return;
        }
        log.info("there are stock can be traded. stock: {}", canTradeStocks);

        // 按前日的总成交量倒排，过滤掉无效stock之后，截取前五个进行交易
        List<String> sortedCanTradeStock = stockLastOptionVolMap.entrySet().stream().sorted((o1, o2) -> o2.getValue().compareTo(o1.getValue())).map(kv -> kv.getKey()).collect(Collectors.toList());
        sortedCanTradeStock.removeAll(invalidStocks);
        if (CollectionUtils.isEmpty(sortedCanTradeStock)) {
            log.info("except invalid stock, there is no stock can be traded");
            hasFinishBuying = true;
            return;
        }

        if (sortedCanTradeStock.size() > 5) {
            canTradeStocks = Sets.newHashSet(sortedCanTradeStock.subList(0, 5));
            log.info("can trade stock size > 5. after intercept, the stocks are {}", canTradeStocks);
        } else {
            canTradeStocks = Sets.newHashSet(sortedCanTradeStock);
            log.info("can trade stock size <= 5. don't intercept, the stocks are {}", canTradeStocks);
        }

        // 均分账户资金
        //        double funds = tradeApi.getFunds();
        int actualSize = canTradeStocks.size();
        if (actualSize == 0) {
            log.info("all stock has been sold. exit");
            return;
        } else {
            avgFund = (int) funds / actualSize;
        }
        log.info("funds: {}, avgFund: {}", funds, avgFund);

        getOrder();
        threadPool.execute(() -> monitorBuyOrder());
        threadPool.execute(() -> monitorSellOrder());
        stopLossAndGain();
        delayUnsubscribeQuote();

        while (true) {
            for (String stock : canTradeStocks) {
                int hasBoughtOrder = MapUtils.getInteger(hasBoughtOrderMap, stock, NOT_EXIST);
                if (hasBoughtOrder == EXIST) {
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
                String callQuote = codeToQuoteMap.get(callFutu);
                String putQuote = codeToQuoteMap.get(putFutu);
                if (StringUtils.isAnyBlank(callQuote, putQuote)) {
                    invalidTradeStock(stock);
                    log.info("there is no legal option quote. stock={}", stock);
                    continue;
                }

                double callTradePrice = calTradePrice(stock, callRt, CALL_TYPE);
                double putTradePrice = calTradePrice(stock, putRt, PUT_TYPE);

                // 如果下单数量小数位小于等于0.5，取整要减一，如果小数位大于0.5，则不变。目的是为了后面如果改价避免成交数量超过限制
                double countDouble = avgFund / (callTradePrice + putTradePrice) / 100;
                String countStr = String.valueOf(countDouble);
                int dotIndex = countStr.indexOf(".");
                String dotStr = countStr.substring(dotIndex + 1, dotIndex + 2);
                double count = (int) countDouble;
                if (Integer.valueOf(dotStr) <= 5) {
                    count = (int) countDouble - 1;
                }

                if (count <= 0) {
                    invalidTradeStock(stock);
                    log.info("count is illegal. stock={}, count={}", stock, count);
                }

                //                if (callTradePrice < 0.1 || putTradePrice < 0.1) {
                //                    invalidTradeStock(stock);
                //                    log.info("option price less than 0.1. don't trade stock={}\tcall={}\tcallPrice={}\tput={}\tputPrice={}", stock, call, callTradePrice, put, putTradePrice);
                //                    continue;
                //                }

                long buyCallOrderId = tradeApi.placeNormalBuyOrder(callFutu, count, callTradePrice);
                long buyPutOrderId = tradeApi.placeNormalBuyOrder(putFutu, count, putTradePrice);
                log.info("begin trade: buyCallOrder={}\tcall={}\tcallPrice={}\tbuyPutOrder={}\tput={}\tputPrice={}\tcount={}", buyCallOrderId, call, callTradePrice, buyPutOrderId, put, putTradePrice, count);

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
                hasBoughtOrderMap.put(stock, EXIST);
            }
            Set<String> hasBoughtOrderStocks = hasBoughtOrderMap.entrySet().stream().filter(e -> e.getValue().intValue() == EXIST).map(e -> e.getKey()).collect(Collectors.toSet());
            if (CollectionUtils.intersection(canTradeStocks, hasBoughtOrderStocks).size() == canTradeStocks.size()) {
                log.info("all stock has bought order: {}. stop buy order", canTradeStocks);
                break;
            }
        }

        while (true) {
            if (hasFinishBuying && CollectionUtils.intersection(hasBoughtSuccess, hasSoldSuccess).size() == hasBoughtSuccess.size()) {
                log.info("all stock have finished trading. End!");
                Thread.sleep(5000);
                return;
            }
            Thread.sleep(10000);
        }
    }

    public void getOrder() {
        List<Long> orderIds = Lists.newArrayList();
        buyOrderIdMap.values().forEach(id -> orderIds.add(id));
        sellOrderIdMap.values().forEach(id -> orderIds.add(id));
        try {
            tradeApi.getOrderList(orderIds);
        } catch (InterruptedException e) {
            log.error("get order error", e);
        }
    }

    public double calTradePrice(String stock, String ivRt, String optionType) {
        Double iv = optionRtIvMap.get(ivRt);
        String option = ivRt.replaceAll("\\+", "");
        Double strikePrice = optionStrikePriceMap.get(option);
        String expireDate = optionExpireDateMap.get(option);
        Double stockPrice = realtimeQuoteForOptionMap.get(stock);

        double predPrice;
        if (StringUtils.equalsAnyIgnoreCase(optionType, CALL_TYPE)) {
            predPrice = BaseUtils.getCallPredictedValue(stockPrice, strikePrice, riskFreeRate, iv, currentTradeDate, expireDate);
        } else {
            predPrice = BaseUtils.getPutPredictedValue(stockPrice, strikePrice, riskFreeRate, iv, currentTradeDate, expireDate);
        }
        log.info("calculate predicate price. stock={}\toptionCode={}\tprice={}\tStrikePrice={}\tiv={}\tpredPrice={}", stock, option, stockPrice, strikePrice, iv, predPrice);

        String futu = optionCodeMap.get(ivRt);
        String quote = codeToQuoteMap.get(futu);
        String[] quoteSplit = quote.split("\\|");
        double bidPrice = Double.parseDouble(quoteSplit[0]);
        double askPrice = Double.parseDouble(quoteSplit[1]);
        double midPrice = BigDecimal.valueOf((bidPrice + askPrice) / 2).setScale(2, RoundingMode.UP).doubleValue();
        log.info("monitor option quote detail: optionCode={}\toptionBid={}\toptionAsk={}\toptionMid={}", futu, bidPrice, askPrice, midPrice);

        //        double futuPredPrice = 0;
        //        double futuIv = futuQuote.getOptionIvTimeMap(futu);
        //        if (futuIv > 0) {
        //            if (StringUtils.equalsAnyIgnoreCase(optionType, CALL_TYPE)) {
        //                futuPredPrice = BaseUtils.getCallPredictedValue(stockPrice, strikePrice, riskFreeRate, futuIv, currentTradeDate, expireDate);
        //            } else {
        //                futuPredPrice = BaseUtils.getPutPredictedValue(stockPrice, strikePrice, riskFreeRate, futuIv, currentTradeDate, expireDate);
        //            }
        //        }
        //        log.info("calculate futu predicate price. optionCode={}\tpredPrice={}\tiv={}", option, futuPredPrice, futuIv);

        double tradePrice;
        if (predPrice < bidPrice || predPrice > midPrice) {
            tradePrice = midPrice;
        } else {
            tradePrice = predPrice;
        }
        return tradePrice;
    }

    public double calQuoteMidPrice(String futuCode) {
        if (!codeToQuoteMap.containsKey(futuCode)) {
            return 0;
        }
        String quote = codeToQuoteMap.get(futuCode);
        String[] quoteSplit = quote.split("\\|");
        double bidPrice = Double.parseDouble(quoteSplit[0]);
        double askPrice = Double.parseDouble(quoteSplit[1]);
        BigDecimal midPriceDecimal = BigDecimal.valueOf((bidPrice + askPrice) / 2).setScale(2, RoundingMode.DOWN);
        double midPrice = midPriceDecimal.doubleValue();

        long current = System.currentTimeMillis();
        Long lastTimestamp = MapUtils.getLong(optionAdjustPriceTimestampMap, futuCode, current);
        if (lastTimestamp == current) {
            optionAdjustPriceTimestampMap.put(futuCode, current);
        }

        // 记录调价次数，并计算每间隔5秒一次调价幅度，同时更新最新调价时间和调价次数
        Integer lastAdjustTimes = MapUtils.getInteger(optionAdjustPriceTimesMap, futuCode, 0);
        int adjustTimes = lastAdjustTimes + 1;
        optionAdjustPriceTimesMap.put(futuCode, adjustTimes);
        if (current > closeTime && current - lastTimestamp > ADJUST_TRADE_PRICE_TIME_INTERVAL) {
            BigDecimal adjustPriceDecimal = BigDecimal.valueOf((midPrice - bidPrice) / ADJUST_TRADE_PRICE_TIMES * adjustTimes).setScale(2, RoundingMode.HALF_UP);
            midPrice = midPriceDecimal.subtract(adjustPriceDecimal).setScale(2, RoundingMode.HALF_UP).doubleValue();
            optionAdjustPriceTimestampMap.put(futuCode, current);
        }

        return midPrice;
    }

    public void getRealTimeIV() {
        //        for (String stock : hasBoughtSuccess) {
        //            canTradeOptionForRtIVMap.remove(stock);
        //        }
        //        log.info("get real time iv: {}", canTradeOptionForRtIVMap);
        //        if (MapUtils.isEmpty(canTradeOptionForRtIVMap)) {
        //            log.info("there is no canTradeOptionForRtIVMap");
        //            return;
        //        }

        int beginMarket = 93000;
        int closeMarket = 160000;

        HttpClient httpClient = new HttpClient();
        HttpConnectionManagerParams httpConnectionManagerParams = new HttpConnectionManagerParams();
        httpConnectionManagerParams.setSoTimeout(5000);
        httpClient.getHttpConnectionManager().setParams(httpConnectionManagerParams);

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {

            int showtimes = 10, showCount = 0;

            @Override
            public void run() {
                if (MapUtils.isEmpty(canTradeOptionForRtIVMap) && !hasFinishBuying) {
                    log.info("get realtime iv. but there is no option to get. waiting......");
                    return;
                }

                if (hasFinishBuying) {
                    log.info("all stock don't need get realtime iv");
                    timer.cancel();
                    return;
                }
                String options = canTradeOptionForRtIVMap.entrySet().stream().filter(kv -> !hasBoughtSuccess.contains(kv.getKey())).flatMap(kv -> Arrays.stream(kv.getValue().split("\\|"))).collect(Collectors.joining(","));
                String url = "https://restapi.ivolatility.com/equities/rt/options-rawiv?apiKey=5uO8Wid7AY945OJ2&symbols=" + options;
                //                log.info("request rt iv url={}", url);

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

                            if (iv == -1.0) {
                                continue;
                            }
                            int timeInt = 0;
                            try {
                                String timeStr = timestamp.substring(11, 19).replaceAll(":", "");
                                timeInt = Integer.valueOf(timeStr);
                            } catch (Exception e) {
                                log.error("calculate rt iv time error. timestamp={}", timestamp, e);
                                return;
                            }
                            if (timeInt < beginMarket || timeInt > closeMarket) {
                                continue;
                            }

                            results.add(line);
                            optionRtIvMap.put(symbol.replaceAll(" ", "+"), iv);
                        }
                        if (MapUtils.isNotEmpty(optionRtIvMap)) {
                            for (String stock : canTradeOptionForRtIVMap.keySet()) {
                                if (hasBoughtSuccess.contains(stock)) {
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

                                String callFutu = optionCodeMap.get(callRt);
                                String putFutu = optionCodeMap.get(putRt);
                                String callQuote = codeToQuoteMap.get(callFutu);
                                String putQuote = codeToQuoteMap.get(putFutu);
                                if (StringUtils.isAnyBlank(callQuote, putQuote)) {
                                    invalidTradeStock(stock);
                                    log.info("get realtime iv check out invalid stock: {}", stock);
                                }
                            }
                            log.info("rt iv data: {}", results);
                        }

                        //                        showCount++;
                        //                        if (showCount == showtimes) {
                        //                        log.info("rt iv data: {}", results);
                        //                            showCount = 0;
                        //                        }
                    }
                    if (curent > (closeTime + 60000)) {
                        timer.cancel();
                    }
                } catch (IOException e) {
                    log.error("getRealTimeIV error. url={}", url, e);
                }
            }
        }, 0, 1500);
    }

    public void getFutuRealTimeIV() {
        int beginMarket = 93000;
        int closeMarket = 160000;
        long openTime = client.getOpenTime();
        String pattern = "yyyy-MM-dd HH:mm:ss";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);

        HttpClient httpClient = new HttpClient();
        HttpConnectionManagerParams httpConnectionManagerParams = new HttpConnectionManagerParams();
        httpConnectionManagerParams.setSoTimeout(5000);
        httpClient.getHttpConnectionManager().setParams(httpConnectionManagerParams);

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {

            int showtimes = 10, showCount = 0;

            @Override
            public void run() {
                if (MapUtils.isEmpty(canTradeOptionForRtIVMap) && !hasFinishBuying) {
                    log.info("get realtime iv. but there is no option to get. waiting......");
                    return;
                }

                if (hasFinishBuying) {
                    log.info("all stock don't need get realtime iv");
                    timer.cancel();
                    return;
                }

                for (String stock : canTradeOptionForRtIVMap.keySet()) {
                    if (hasBoughtSuccess.contains(stock)) {
                        continue;
                    }

                    String callAndPut = canTradeOptionForRtIVMap.get(stock);
                    try {
                        String[] split = callAndPut.split("\\|");
                        String callRt = split[0];
                        String putRt = split[1];

                        String callFutu = optionCodeMap.get(callRt);
                        String putFutu = optionCodeMap.get(putRt);
                        String callIvTime = futuQuote.getOptionIvTimeMap(callFutu);
                        String putIvTime = futuQuote.getOptionIvTimeMap(putFutu);
                        if (StringUtils.isAnyBlank(callIvTime, putIvTime)) {
                            log.info("get futu iv is empty. callIvTime={}, putIvTime={}", callIvTime, putIvTime);
                            continue;
                        }
                        String[] callSplit = callIvTime.split("\\|");
                        String[] putSplit = putIvTime.split("\\|");

                        long callTime = LocalDateTime.parse(callSplit[1].substring(0, pattern.length()), formatter).toInstant(ZoneOffset.of("-4")).toEpochMilli();
                        long putTime = LocalDateTime.parse(putSplit[1].substring(0, pattern.length()), formatter).toInstant(ZoneOffset.of("-4")).toEpochMilli();
                        if (callTime < openTime || putTime < openTime) {
                            continue;
                        }
                        String callQuote = codeToQuoteMap.get(callFutu);
                        String putQuote = codeToQuoteMap.get(putFutu);
                        if (StringUtils.isAnyBlank(callQuote, putQuote)) {
                            invalidTradeStock(stock);
                            log.info("get realtime iv check out invalid stock: {}", stock);
                        } else {
                            double callIv = Double.parseDouble(callSplit[0]);
                            double putIv = Double.parseDouble(putSplit[0]);
                            optionRtIvMap.put(callRt, callIv);
                            optionRtIvMap.put(putRt, putIv);
                            log.info("rt iv data: call={} {}\tput={} {}", callFutu, callIvTime, putFutu, putIvTime);
                        }
                    } catch (Exception e) {
                        log.error("getFutuRealTimeIV error. callAndPut={}", callAndPut, e);
                    }
                }
                long current = System.currentTimeMillis();
                if (current > (closeTime + 60000)) {
                    timer.cancel();
                }
            }
        }, 0, 1500);
    }

    public void monitorBuyOrder() {
        log.info("monitor buy order");
        long openTime = client.getOpenTime();
        Map<String, Double> lastTradePriceMap = Maps.newHashMap();
        while (true) {
            for (String stock : hasBoughtOrderMap.keySet()) {
                int hasBoughtOrder = MapUtils.getInteger(hasBoughtOrderMap, stock, NOT_EXIST);
                if (hasBoughtOrder == NOT_EXIST) { // 不在boughtOrder里，说明还没有买入订单不需要监听
                    continue;
                }
                if (hasBoughtSuccess.contains(stock)) { // 已经全部买完，不需要监听
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
                Order buyCallOrder = tradeApi.getOrder(buyCallOrderId);
                Order buyPutOrder = tradeApi.getOrder(buyPutOrderId);

                boolean callSuccess = buyCallOrder != null && buyCallOrder.getOrderStatus() == TrdCommon.OrderStatus.OrderStatus_Filled_All_VALUE;
                boolean putSuccess = buyPutOrder != null && buyPutOrder.getOrderStatus() == TrdCommon.OrderStatus.OrderStatus_Filled_All_VALUE;
                if (callSuccess && putSuccess) {
                    ReadWriteOptionTradeInfo.writeHasBoughtSuccess(stock);
                    hasBoughtSuccess.add(stock);
                    log.info("{} buy trade success. call={}\torderId={}\tput={}\torderId={}\tcount={}", stock, call, buyCallOrderId, put, buyPutOrderId, count);
                } else if (System.currentTimeMillis() - buyOrderTimeMap.get(stock) > ORDER_INTERVAL_TIME_MILLI) {
                    if (!callSuccess) {
                        double hasTradeCount = 0;
                        if (buyCallOrder != null) {
                            hasTradeCount = buyCallOrder.getCount();
                        }
                        double tradePrice = calTradePrice(stock, callRt, CALL_TYPE);
                        if (!lastTradePriceMap.containsKey(call) || lastTradePriceMap.get(call).compareTo(tradePrice) != 0) {
                            long modifyOrderId = tradeApi.upOrderPrice(buyCallOrderId, count, tradePrice);
                            log.info("modify buy call order: orderId={}\tcall={}\ttradePrice={}\tcount={}\thasTradeCount={}", modifyOrderId, call, tradePrice, count, hasTradeCount);
                        }
                        lastTradePriceMap.put(call, tradePrice);
                    }
                    if (!putSuccess) {
                        double hasTradeCount = 0;
                        if (buyPutOrder != null) {
                            hasTradeCount = buyPutOrder.getCount();
                        }
                        double tradePrice = calTradePrice(stock, putRt, PUT_TYPE);
                        if (!lastTradePriceMap.containsKey(put) || lastTradePriceMap.get(put).compareTo(tradePrice) != 0) {
                            long modifyOrderId = tradeApi.upOrderPrice(buyPutOrderId, count, tradePrice);
                            log.info("modify buy put order: orderId={}\tput={}\ttradePrice={}\tcount={}\thasTradeCount={}", modifyOrderId, put, tradePrice, count, hasTradeCount);
                        }
                        lastTradePriceMap.put(put, tradePrice);
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

            if (CollectionUtils.intersection(canTradeStocks, hasBoughtSuccess).size() == canTradeStocks.size()) {
                log.info("all stock has bought success: {}. stop monitor buy order", canTradeStocks);
                hasFinishBuying = true;
                RealTimeDataWS_DB.getRealtimeQuoteForOption = false;
                return;
            }
        }
    }

    public void monitorSellOrder() {
        log.info("monitor sell order");
        long openTime = client.getOpenTime();
        Map<String, Double> lastTradePriceMap = Maps.newHashMap();
        while (true) {
            long current = System.currentTimeMillis();
            //            if (!hasBoughtSuccess.isEmpty()) {
            for (String stock : hasSoldOrderMap.keySet()) {
                int hasSoldOrder = MapUtils.getInteger(hasSoldOrderMap, stock, NOT_EXIST);
                if (hasSoldOrder == NOT_EXIST) { // 不在soldOrder里，说明还没有卖出订单不需要监听
                    continue;
                }
                if (hasSoldSuccess.contains(stock)) { // 已经全部卖完，不需要监听
                    continue;
                }

                Double count = orderCountMap.get(stock);

                String callAndPut = canTradeOptionForFutuMap.get(stock);
                String[] split = callAndPut.split("\\|");
                String call = split[0];
                String put = split[1];

                Long sellCallOrderId = sellOrderIdMap.get(call);
                Long sellPutOrderId = sellOrderIdMap.get(put);
                Order sellCallOrder = tradeApi.getOrder(sellCallOrderId);
                Order sellPutOrder = tradeApi.getOrder(sellPutOrderId);

                boolean callSuccess = sellCallOrder != null && sellCallOrder.getOrderStatus() == TrdCommon.OrderStatus.OrderStatus_Filled_All_VALUE;
                boolean putSuccess = sellPutOrder != null && sellPutOrder.getOrderStatus() == TrdCommon.OrderStatus.OrderStatus_Filled_All_VALUE;
                if (callSuccess && putSuccess) {
                    ReadWriteOptionTradeInfo.writeHasSoldSuccess(stock);
                    hasSoldSuccess.add(stock);
                    log.info("{} sell trade success. call={}\torderId={}\tput={}\torderId={}", stock, call, sellCallOrderId, put, sellPutOrderId);
                } else if (System.currentTimeMillis() - sellOrderTimeMap.get(stock) > ORDER_INTERVAL_TIME_MILLI) {
                    if (!callSuccess) {
                        double hasTradeCount = 0;
                        if (sellCallOrder != null) {
                            hasTradeCount = sellCallOrder.getCount();
                        }

                        double tradePrice = calQuoteMidPrice(call);
                        if (tradePrice == 0d) {
                            log.warn("modify sell call order price is 0: orderId={}\tcall={}", sellCallOrderId, call);
                            continue;
                        }

                        if (!lastTradePriceMap.containsKey(call) || lastTradePriceMap.get(call).compareTo(tradePrice) != 0) {
                            long modifyOrderId = tradeApi.upOrderPrice(sellCallOrderId, count, tradePrice);
                            log.info("modify sell call order: orderId={}\tcall={}\ttradePrice={}\tcount={}\thasTradeCount={}", modifyOrderId, call, tradePrice, count, hasTradeCount);
                        }
                        lastTradePriceMap.put(call, tradePrice);
                    }
                    if (!putSuccess) {
                        double hasTradeCount = 0;
                        if (sellPutOrder != null) {
                            hasTradeCount = sellPutOrder.getCount();
                        }

                        double tradePrice = calQuoteMidPrice(put);
                        if (tradePrice == 0d) {
                            log.warn("modify sell put order price is 0: orderId={}\tput={}", sellPutOrderId, put);
                            continue;
                        }

                        if (!lastTradePriceMap.containsKey(put) || lastTradePriceMap.get(put).compareTo(tradePrice) != 0) {
                            long modifyOrderId = tradeApi.upOrderPrice(sellPutOrderId, count, tradePrice);
                            log.info("modify sell put order: orderId={}\tput={}\ttradePrice={}\tcount={}\thasTradeCount={}", modifyOrderId, put, tradePrice, count, hasTradeCount);
                        }
                        lastTradePriceMap.put(put, tradePrice);
                    }
                    long curTime = System.currentTimeMillis();
                    sellOrderTimeMap.put(stock, curTime);
                    ReadWriteOptionTradeInfo.writeSellOrderTime(stock, curTime);
                }
                // todo 收盘卖出为了避免长时间无法成交，需要按照最新的挂单价-当时的买一价，差价除以10次（即5秒一次改单），然后乘以当前降低的次数（总共10次）得到每次改单需要降低的价格，每五秒用最新挂单价减去一次降低的价格，尽快卖出
                // 比如：
                // 1、当前买一1.3，卖一1.8，挂单价(1.3+1.8)/2=1.55，初次挂单不降价
                // 2、5秒后，当前买一1.3，卖一1.8，挂单价1.55，第一次降价，降价幅度=(1.55-1.3)/10*1=0.025≈0.03（四舍五入），实际挂单价=1.55-0.03=1.52
                // 3、5秒后，当前买一1，卖一1.6，挂单价(1+1.6)/2=1.3，第二次降价，降价幅度=(1.3-1)/10*2=0.06，实际挂单价=1.3-0.06=1.24
                // 4、5秒后，当前买一1，卖一1.6，挂单价(1+1.6)/2=1.3，第三次降价，降价幅度=(1.3-1)/10*3=0.09，实际挂单价=1.3-0.09=1.21
                // 以此类推，10个5秒也就是50秒以后，实际挂单价=买一价，一定会卖出
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                log.error("ignore2", e);
            }
            // 如果可交易数量=买入成功数量，且买入成功数量=卖出成功数量
            if (hasFinishBuying && CollectionUtils.intersection(hasBoughtSuccess, hasSoldSuccess).size() == hasBoughtSuccess.size()) {
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
        long openTime = client.getOpenTime();
        long monitorTime = openTime + STOP_LOSS_GAIN_INTERVAL_TIME_MILLI;
        long stopGainTime = openTime + STOP_GAIN_INTERVAL_TIME_LINE;
        long cur = System.currentTimeMillis();
        long delay = monitorTime - cur;
        if (cur > monitorTime || delay > STOP_LOSS_GAIN_INTERVAL_TIME_MILLI) {
            delay = 0;
        }
        int NO_GAIN_SELL = 0, GAIN_SELLING = 1, GAIN_SOLD = 2;
        Map<String/* stock */, Integer/* 上涨卖出状态 */> gainSellStatusMap = Maps.newHashMap();
        Set<String/* futuOptionCode */> gainSellOption = Sets.newHashSet();
        Map<String/* futuOptionCode */, Double> lastTradePriceMap = Maps.newHashMap();
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {

            int showtimes = 20, showCount = 0;
            Map<String/* code */, StockPosition> positions = Maps.newHashMap();

            private StockPosition getPosistion(String optionCode) {
                //                if (positions.containsKey(optionCode)) {
                //                    return positions.get(optionCode);
                //                }

                Map<String, StockPosition> positionMap = tradeApi.getPositionMap(optionCode);
                StockPosition stockPosition = positionMap.get(optionCode);
                if (stockPosition != null) {
                    positions.put(optionCode, stockPosition);
                }

                return positions.get(optionCode);
            }

            @Override
            public void run() {
                try {
                    showCount++;
                    long currentTime = System.currentTimeMillis();
                    for (String stock : hasBoughtSuccess) {
                        int hasSoldOrder = MapUtils.getInteger(hasSoldOrderMap, stock, NOT_EXIST);
                        if (hasSoldOrder == EXIST) { // 在soldOrder里，说明都已经下单卖出，不需要监听
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

                        /** 根据已成交的订单查看买入价，不断以最新报价计算是否触发止损价（不能以当前市价止损，因为流动性不够偏差会很大）*/

                        StockPosition callPosition = getPosistion(call);
                        StockPosition putPosition = getPosistion(put);
                        // 测试用 start ---------------
                        //                    callPosition = new StockPosition();
                        //                    callPosition.setCostPrice(1.03);
                        //                    callPosition.setCanSellQty(54);
                        //                    putPosition = new StockPosition();
                        //                    putPosition.setCostPrice(0.78);
                        //                    putPosition.setCanSellQty(54);
                        // 测试用 end -----------------
                        if (callPosition == null || putPosition == null) {
                            log.info("{} position is null, call={}, put={}. retry", stock, callPosition, putPosition);
                            continue;
                        }
                        double callOpen = callPosition.getCostPrice();
                        double putOpen = putPosition.getCostPrice();
                        double callDiff = callMidPrice - callOpen;
                        double putDiff = putMidPrice - putOpen;
                        double allDiff = BigDecimal.valueOf(callDiff + putDiff).setScale(2, RoundingMode.HALF_UP).doubleValue();
                        double diffRatio = BigDecimal.valueOf(allDiff / (callOpen + putOpen)).setScale(4, RoundingMode.HALF_UP).doubleValue();
                        double callCount = callPosition.getCanSellQty();
                        double putCount = putPosition.getCanSellQty();
                        if (showCount == showtimes) {
                            log.info("monitor loss and gain. call={}\tcallOpen={}\tcallBid={}\tcallAsk={}\tcallMid={}\tput={}\tputOpen={}\tputBid={}\tputAsk={}\tputMid={}\tcallDiff={}\tputDiff={}\tallDiff={}\tdiffRatio={}",
                              call, callOpen, callBidPrice, callAskPrice, callMidPrice, put, putOpen, putBidPrice, putAskPrice, putMidPrice, callDiff, putDiff, allDiff, diffRatio);
                        }

                        long curTime = System.currentTimeMillis();
                        Integer gainSellStatus = MapUtils.getInteger(gainSellStatusMap, stock, NO_GAIN_SELL);
                        if (gainSellStatus == NO_GAIN_SELL) { // 无上涨卖出 继续执行止盈或止损或收盘卖出判断
                            if (diffRatio < STOP_LOSS_RATIO || currentTime > closeTime) { // 止损+收盘都同时卖出，尽快止损和收盘卖出
                                long sellCallOrderId = tradeApi.placeNormalSellOrder(call, callCount, callMidPrice);
                                long sellPutOrderId = tradeApi.placeNormalSellOrder(put, putCount, putMidPrice);

                                sellOrderTimeMap.put(stock, curTime);
                                sellOrderIdMap.put(call, sellCallOrderId);
                                sellOrderIdMap.put(put, sellPutOrderId);
                                optionAdjustPriceTimestampMap.put(call, curTime);
                                optionAdjustPriceTimestampMap.put(put, curTime);
                                optionAdjustPriceTimesMap.put(call, 0);
                                optionAdjustPriceTimesMap.put(put, 0);
                                ReadWriteOptionTradeInfo.writeSellOrderTime(stock, curTime);
                                ReadWriteOptionTradeInfo.writeSellOrderId(call, sellCallOrderId);
                                ReadWriteOptionTradeInfo.writeSellOrderId(put, sellPutOrderId);
                                ReadWriteOptionTradeInfo.writeHasSoldOrder(stock);
                                hasSoldOrderMap.put(stock, EXIST);

                                log.info("stop loss and gain order. call={}\tcallOpen={}\tcallBid={}\tcallAsk={}\tcallMid={}\tput={}\tputOpen={}\tputBid={}\tputAsk={}\tputMid={}\tcallDiff={}\tputDiff={}\tallDiff={}\tdiffRatio={}\tsellCallOrder={}\tsellPutOrder={}",
                                  call, callOpen, callBidPrice, callAskPrice, callMidPrice, put, putOpen, putBidPrice, putAskPrice, putMidPrice, callDiff, putDiff, allDiff, diffRatio, sellCallOrderId, sellPutOrderId);
                            } else if ((currentTime < stopGainTime && diffRatio > STOP_GAIN_RATIO_1) || (currentTime > stopGainTime && diffRatio > STOP_GAIN_RATIO_2)) { // 止盈先卖出上涨的期权，卖完后再卖下跌的期权，尽量保证盈利不减少
                                if (callDiff > 0) {
                                    long sellCallOrderId = tradeApi.placeNormalSellOrder(call, callCount, callMidPrice);
                                    sellOrderTimeMap.put(stock, curTime);
                                    sellOrderIdMap.put(call, sellCallOrderId);
                                    ReadWriteOptionTradeInfo.writeSellOrderTime(stock, curTime);
                                    ReadWriteOptionTradeInfo.writeSellOrderId(call, sellCallOrderId);
                                    lastTradePriceMap.put(call, callMidPrice);
                                    gainSellStatusMap.put(stock, GAIN_SELLING);
                                    gainSellOption.add(call);
                                    log.info("gain sell call order: orderId={}\tcall={}\ttradePrice={}\tcount={}", sellCallOrderId, call, callMidPrice, callCount);
                                } else if (putDiff > 0) {
                                    long sellPutOrderId = tradeApi.placeNormalSellOrder(put, putCount, putMidPrice);
                                    sellOrderTimeMap.put(stock, curTime);
                                    sellOrderIdMap.put(put, sellPutOrderId);
                                    ReadWriteOptionTradeInfo.writeSellOrderTime(stock, curTime);
                                    ReadWriteOptionTradeInfo.writeSellOrderId(put, sellPutOrderId);
                                    lastTradePriceMap.put(put, putMidPrice);
                                    gainSellStatusMap.put(stock, GAIN_SELLING);
                                    gainSellOption.add(put);
                                    log.info("gain sell put order: orderId={}\tcall={}\ttradePrice={}\tcount={}", sellPutOrderId, put, putMidPrice, putCount);
                                }
                            }
                        } else if (gainSellStatus == GAIN_SELLING) { // 上涨卖出中
                            if (gainSellOption.contains(call)) {
                                Long sellCallOrderId = sellOrderIdMap.get(call);
                                Order sellCallOrder = tradeApi.getOrder(sellCallOrderId);
                                boolean callSuccess = sellCallOrder != null && sellCallOrder.getOrderStatus() == TrdCommon.OrderStatus.OrderStatus_Filled_All_VALUE;
                                if (callSuccess) { // 全部成交 写入上涨卖出完成状态
                                    gainSellStatusMap.put(stock, GAIN_SOLD);
                                } else { // 未全部成交
                                    if ((currentTime < stopGainTime && diffRatio > STOP_GAIN_RATIO_1) || (currentTime > stopGainTime && diffRatio > STOP_GAIN_RATIO_2)) { // 检查报价高于止盈，改单
                                        double hasTradeCount = 0;
                                        if (sellCallOrder != null) {
                                            hasTradeCount = sellCallOrder.getCount();
                                        }
                                        if (!lastTradePriceMap.containsKey(call) || lastTradePriceMap.get(call).compareTo(callMidPrice) != 0) {
                                            long modifyOrderId = tradeApi.upOrderPrice(sellCallOrderId, callCount, callMidPrice);
                                            log.info("modify gain sell call order: orderId={}\tcall={}\ttradePrice={}\tcount={}\thasTradeCount={}", modifyOrderId, call, callMidPrice, callCount, hasTradeCount);
                                        }
                                        lastTradePriceMap.put(call, callMidPrice);
                                    } else { // 检查报价低于止盈，撤单，清除上涨卖出中状态，清除上涨卖出的期权
                                        long cancelResp = tradeApi.cancelOrder(sellCallOrderId);
                                        if (cancelResp != TRADE_PROHIBT_CODE && cancelResp != TRADE_ERROR_CODE) {
                                            gainSellStatusMap.put(stock, NO_GAIN_SELL);
                                            gainSellOption.remove(call);
                                            log.info("gain selling stop success. stock={}, option={}", stock, call);
                                        } else { // 撤单失败，记录日志（可能已经全部成交就无法撤单）
                                            log.info("gain selling stop failed. stock={}, option={}", stock, call);
                                        }
                                    }
                                }
                            } else if (gainSellOption.contains(put)) {
                                Long sellPutOrderId = sellOrderIdMap.get(put);
                                Order sellPutOrder = tradeApi.getOrder(sellPutOrderId);
                                boolean putSuccess = sellPutOrder != null && sellPutOrder.getOrderStatus() == TrdCommon.OrderStatus.OrderStatus_Filled_All_VALUE;
                                if (putSuccess) {
                                    gainSellStatusMap.put(stock, GAIN_SOLD);
                                } else {
                                    if ((currentTime < stopGainTime && diffRatio > STOP_GAIN_RATIO_1) || (currentTime > stopGainTime && diffRatio > STOP_GAIN_RATIO_2)) {
                                        double hasTradeCount = 0;
                                        if (sellPutOrder != null) {
                                            hasTradeCount = sellPutOrder.getCount();
                                        }
                                        if (!lastTradePriceMap.containsKey(put) || lastTradePriceMap.get(put).compareTo(putMidPrice) != 0) {
                                            long modifyOrderId = tradeApi.upOrderPrice(sellPutOrderId, putCount, putMidPrice);
                                            log.info("modify gain sell put order: orderId={}\tput={}\ttradePrice={}\tcount={}\thasTradeCount={}", modifyOrderId, put, putMidPrice, putCount, hasTradeCount);
                                        }
                                        lastTradePriceMap.put(put, putMidPrice);
                                    } else {
                                        long cancelResp = tradeApi.cancelOrder(sellPutOrderId);
                                        if (cancelResp != TRADE_PROHIBT_CODE && cancelResp != TRADE_ERROR_CODE) {
                                            gainSellStatusMap.put(stock, NO_GAIN_SELL);
                                            gainSellOption.remove(put);
                                            log.info("gain selling stop success. stock={}, option={}", stock, put);
                                        } else {
                                            log.info("gain selling stop failed. stock={}, option={}", stock, put);
                                        }
                                    }
                                }
                            } else {
                                log.error("gain selling, but no gain sell option. stock={}", stock);
                            }
                        } else if (gainSellStatus == GAIN_SOLD) {
                            if (gainSellOption.contains(call)) {
                                long sellPutOrderId = tradeApi.placeNormalSellOrder(put, putCount, putMidPrice);
                                sellOrderTimeMap.put(stock, curTime);
                                sellOrderIdMap.put(put, sellPutOrderId);
                                ReadWriteOptionTradeInfo.writeSellOrderTime(stock, curTime);
                                ReadWriteOptionTradeInfo.writeSellOrderId(put, sellPutOrderId);
                                ReadWriteOptionTradeInfo.writeHasSoldOrder(stock);
                                hasSoldOrderMap.put(stock, EXIST);
                            } else if (gainSellOption.contains(put)) {
                                long sellCallOrderId = tradeApi.placeNormalSellOrder(call, callCount, callMidPrice);
                                sellOrderTimeMap.put(stock, curTime);
                                sellOrderIdMap.put(call, sellCallOrderId);
                                ReadWriteOptionTradeInfo.writeSellOrderTime(stock, curTime);
                                ReadWriteOptionTradeInfo.writeSellOrderId(call, sellCallOrderId);
                                ReadWriteOptionTradeInfo.writeHasSoldOrder(stock);
                                hasSoldOrderMap.put(stock, EXIST);
                            } else {
                                log.error("gain sold, but no gain sell option. stock={}", stock);
                            }
                        } else {
                            log.error("gain sell status error. stock={}, status={}", stock, gainSellStatus);
                        }
                        /**
                         * 1.全交易时段止损都是-20%
                         * 2.开盘三小时前止盈是20%
                         * 3.开盘三小时后止盈是10%
                         * 4.如果未到止盈线，收盘前会卖出
                         * 这么做的目的是三小时前交易活跃波动较大，所以止盈可以设置高一些。三小时之后则尽快止盈退出避免损失
                         */
                        //                        if (diffRatio < STOP_LOSS_RATIO || (currentTime < stopGainTime && diffRatio > STOP_GAIN_RATIO_1) || (currentTime > stopGainTime && diffRatio > STOP_GAIN_RATIO_2) || currentTime > closeTime) {
                        //                            long sellCallOrderId = tradeApi.placeNormalSellOrder(call, callCount, callMidPrice);
                        //                            long sellPutOrderId = tradeApi.placeNormalSellOrder(put, putCount, putMidPrice);
                        //
                        //                            sellOrderTimeMap.put(stock, curTime);
                        //                            sellOrderIdMap.put(call, sellCallOrderId);
                        //                            sellOrderIdMap.put(put, sellPutOrderId);
                        //                            ReadWriteOptionTradeInfo.writeSellOrderTime(stock, curTime);
                        //                            ReadWriteOptionTradeInfo.writeSellOrderId(call, sellCallOrderId);
                        //                            ReadWriteOptionTradeInfo.writeSellOrderId(put, sellPutOrderId);
                        //                            ReadWriteOptionTradeInfo.writeHasSoldOrder(stock);
                        //                            hasSoldOrder.add(stock);
                        //
                        //                            log.info("stop loss and gain order. call={}\tcallOpen={}\tcallBid={}\tcallAsk={}\tcallMid={}\tput={}\tputOpen={}\tputBid={}\tputAsk={}\tputMid={}\tcallDiff={}\tputDiff={}\tallDiff={}\tdiffRatio={}\tsellCallOrder={}\tsellPutOrder={}",
                        //                              call, callOpen, callBidPrice, callAskPrice, callMidPrice, put, putOpen, putBidPrice, putAskPrice, putMidPrice, callDiff, putDiff, allDiff, diffRatio, sellCallOrderId, sellPutOrderId);
                        //                        }
                    }

                    if (showCount == showtimes) {
                        showCount = 0;
                    }

                    if (currentTime > closeTime || CollectionUtils.intersection(canTradeStocks, hasSoldSuccess).size() == canTradeStocks.size()) {
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                            log.error("ignore4 ", e);
                        }
                        log.info("time is close to the close time or all stock has sold. stop monitor");
                        timer.cancel();
                    }
                } catch (Exception e) {
                    log.error("stopLossAndGain error", e);
                }
            }
        }, delay, 1000);
    }

    public Map<String, StockPosition> getAllPosition() {
        return tradeApi.getPositionMap(null);
    }

    public void reSendOpenPrice() {
        List<StockEvent> stockEvents = ReadWriteOptionTradeInfo.readStockOpenPrice();
        for (StockEvent stockEvent : stockEvents) {
            try {
                optionStockListener.cal(stockEvent.getStock(), stockEvent.getPrice());
            } catch (Exception e) {
                log.error("resend stock event error. event={}", stockEvent, e);
            }
        }
    }

    public void restart() {
        Set<String> hasBoughtOrderStocks = ReadWriteOptionTradeInfo.readHasBoughtOrder();
        for (String canTradeStock : canTradeStocks) {
            if (hasBoughtOrderStocks.contains(canTradeStock)) {
                hasBoughtOrderMap.put(canTradeStock, EXIST);
            } else {
                hasBoughtOrderMap.put(canTradeStock, NOT_EXIST);
            }
        }
        Set<String> hasSoldOrderStocks = ReadWriteOptionTradeInfo.readHasSoldOrder();
        for (String canTradeStock : canTradeStocks) {
            if (hasSoldOrderStocks.contains(canTradeStock)) {
                hasSoldOrderMap.put(canTradeStock, EXIST);
            } else {
                hasSoldOrderMap.put(canTradeStock, NOT_EXIST);
            }
        }
        hasBoughtSuccess = ReadWriteOptionTradeInfo.readHasBoughtSuccess();
        hasSoldSuccess = ReadWriteOptionTradeInfo.readHasSoldSuccess();
        buyOrderIdMap = ReadWriteOptionTradeInfo.readBuyOrderId();
        sellOrderIdMap = ReadWriteOptionTradeInfo.readSellOrderId();
        buyOrderTimeMap = ReadWriteOptionTradeInfo.readBuyOrderTime();
        sellOrderTimeMap = ReadWriteOptionTradeInfo.readSellOrderTime();
        orderCountMap = ReadWriteOptionTradeInfo.readOrderCount();

        hasBoughtSuccess = hasBoughtSuccess.stream().filter(s -> canTradeStocks.contains(s)).collect(Collectors.toSet());
        hasSoldSuccess = hasSoldSuccess.stream().filter(s -> canTradeStocks.contains(s)).collect(Collectors.toSet());

        try {
            beginTrade();
        } catch (InterruptedException e) {
            log.error("re begin trade error", e);
        }
    }

    public void invalidTradeStock(String stock) {
        hasBoughtOrderMap.put(stock, EXIST);
        hasBoughtSuccess.add(stock);
        hasSoldOrderMap.put(stock, EXIST);
        hasSoldSuccess.add(stock);
        ReadWriteOptionTradeInfo.writeHasBoughtOrder(stock);
        ReadWriteOptionTradeInfo.writeHasBoughtSuccess(stock);
        ReadWriteOptionTradeInfo.writeHasSoldOrder(stock);
        ReadWriteOptionTradeInfo.writeHasSoldSuccess(stock);
        invalidStocks.add(stock);

        log.info("invalid stock: {}", invalidStocks);
        Set<String> hasBoughtOrderStocks = hasBoughtOrderMap.entrySet().stream().filter(e -> e.getValue().intValue() == EXIST).map(e -> e.getKey()).collect(Collectors.toSet());
        if (CollectionUtils.subtract(hasBoughtOrderStocks, invalidStocks).isEmpty()) {
            int size = canTradeStocks.size() - invalidStocks.size();
            if (size == 0) {
                avgFund = funds;
            } else {
                avgFund = (int) funds / size;
            }
            log.info("change avg fund. avgFund={}", avgFund);
        }
    }

    public void monitorQuote(String optionCode) {
        futuQuote.subOrderBook(optionCode);
        log.info("monitor option quote: {}", optionCode);
    }

    public void monitorIV(String optionCode) {
        futuQuote.subBasicQuote(optionCode);
        log.info("monitor option iv: {}", optionCode);
    }

    public void addBuyOrder(String stock) {
        hasBoughtOrderMap.put(stock, NOT_EXIST);
    }

    public void addSellOrder(String stock) {
        hasSoldOrderMap.put(stock, NOT_EXIST);
    }

    public void delayUnsubscribeQuote() {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (CollectionUtils.isNotEmpty(invalidStocks)) {
                    for (String s : tradeStock) {
                        futuQuote.unSubBasicQuote(s);
                    }
                }
                log.info("unsubscribe quote. invalid stocks: {}", invalidStocks);
                timer.cancel();
            }
        }, 1000 * 60 * 1);
    }

    public static void main(String[] args) throws InterruptedException {
        long callTime = LocalDateTime.parse("2024-07-18 16:00:00", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).toInstant(ZoneOffset.of("-4")).toEpochMilli();
        System.out.println(callTime);
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
