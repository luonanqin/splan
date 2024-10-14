package luonq.polygon;

import bean.NodeList;
import bean.Order;
import bean.StockEvent;
import com.futu.openapi.pb.TrdCommon;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.ib.client.OrderStatus;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import luonq.execute.LoadOptionTradeData;
import luonq.execute.ReadWriteOptionTradeInfo;
import luonq.futu.BasicQuote;
import luonq.futu.GetOptionChain;
import luonq.ibkr.TradeApi;
import luonq.listener.OptionStockListener2;
import luonq.listener.OptionStockListener5;
import luonq.strategy.Strategy37;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import util.BaseUtils;
import util.Constants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
 * 末日期权价差策略
 * Created by Luonanqin on 2023/5/9.
 */
@Component
@Data
@Slf4j
public class OptionTradeExecutor5 {

    private NodeList list;
    private BasicQuote futuQuote;
    private GetOptionChain getOptionChain;
    private int cut = 990000;
    private List<String> tradeStock = Lists.newArrayList();
    private RealTimeDataWS_DB2 client;
    private boolean realTrade = true;
    private OptionStockListener5 optionStockListener;
    private TradeApi tradeApi;

    private static long CANCEL_BUY_ORDER_TIME_MILLI = 10 * 60 * 1000; // 开盘后10分钟撤销买入订单
    private static long STOP_MONITORY_BUY_ORDER_TIME_MILLI = 11 * 60 * 1000; // 开盘后11分钟停止监听买入订单的成交
    private static long STOP_MONITORY_SELL_ORDER_TIME_MILLI = 12 * 60 * 1000; // 开盘后12分钟如果没有买入成交的订单，则停止监听卖出订单的成交
    private static long STOP_LOSS_GAIN_INTERVAL_TIME_MILLI = 2 * 60 * 1000; // 开盘后2分钟才开始监听止损，止盈从买入完成即开始，不用等待两分钟
    private static long ORDER_INTERVAL_TIME_MILLI = 1 * 1000; // 下单后检查间隔时间
    private static String CALL_TYPE = "C";
    private static String PUT_TYPE = "P";
    public static final double STOP_LOSS_RATIO = 0.5d; // 全交易时段的止损比例
    public static final double STOP_GAIN_RATIO_1 = 0.2d; // 三小时前的止盈比例
    public static final double STOP_GAIN_RATIO_2 = 0.1d; // 三小时后的止盈比例
    public static final long STOP_GAIN_INTERVAL_TIME_LINE = 3 * 60 * 60 * 1000L; // 三小时止盈时间点
    public static final int ADJUST_SELL_TRADE_PRICE_TIMES = 10; // 卖出挂单价调价次数上限
    public static final int ADJUST_BUY_TRADE_PRICE_TIMES = 5; // 买入挂单价调价次数上限
    public static final long ADJUST_SELL_PRICE_TIME_INTERVAL = 4 * 1000L; // 卖出挂单价调价间隔4秒
    public static final long ADJUST_BUY_PRICE_TIME_INTERVAL = 3 * 1000L; // 买入挂单价调价间隔2秒

    private static Integer EXIST = 1;
    private static Integer NOT_EXIST = 0;
    private Map<String, Integer> hasBoughtOrderMap = Maps.newHashMap(); // 已下单买入
    private Map<String, Integer> hasSoldOrderMap = Maps.newHashMap(); // 已下单卖出
    private Set<String> hasBoughtSuccess = Sets.newHashSet(); // 已成交买入
    private Set<String> hasSoldSuccess = Sets.newHashSet(); // 已成交卖出
    private Set<String> cancelStocks = Sets.newHashSet(); // 已取消买入
    private Set<String> invalidStocks = Sets.newHashSet(); // 实际不能成交的股票，不用写入文件
    private Map<Double/* open strike diff */, String/* stock */> stockToOpenStrikeDiffMap = Maps.newHashMap();
    private Map<String/* stock */, Integer/* 1=up -1=down */> secStockUpDownMap = Maps.newHashMap();
    private boolean hasFinishBuying = false; // 已经完成买入
    private Map<String/* futu option */, Long/* orderId */> buyOrderIdMap = Maps.newHashMap(); // 下单买入的订单id
    private Map<String/* futu option */, Long/* orderId */> sellOrderIdMap = Maps.newHashMap(); // 下单卖出的订单id
    private Map<String/* stock */, Long/* order time */> buyOrderTimeMap = Maps.newHashMap(); // 下单买入时的时间
    private Map<String/* stock */, Long/* order time */> sellOrderTimeMap = Maps.newHashMap(); // 下单卖出时的时间
    private Map<String/* stock */, Double/* order count */> orderCountMap = Maps.newHashMap(); // 下单买入的数量，卖出可以直接使用
    private Map<String/* futu option */, Integer/* adjust trade price times */> adjustSellPriceTimesMap = Maps.newHashMap(); // 卖出挂单价调价次数
    private Map<String/* futu option */, Long/* adjust trade price timestamp */> adjustSellPriceTimestampMap = Maps.newHashMap(); // 卖出挂单价调价的最新时间
    private Map<String/* futu option */, Integer/* adjust trade price times */> adjustBuyPriceTimesMap = Maps.newHashMap(); // 买入挂单价调价次数
    private Map<String/* futu option */, Long/* adjust trade price timestamp */> adjustBuyPriceTimestampMap = Maps.newHashMap(); // 买入挂单价调价的最新时间
    private Map<String/* ikbr option */, Double/* buy price */> lastBuyPriceMap = Maps.newHashMap(); // 最新一次买入挂单价
    private Map<String/* ikbr option */, Double/* sell price */> lastSellPriceMap = Maps.newHashMap(); // 最新一次卖出挂单价
    private Map<String/* futu option */, Double/* sell price */> adjustSellInitPriceMap = Maps.newHashMap(); // 卖出调价的原始挂单价，用于调价计算
    private Map<String/* futu option */, Double/* buy price */> adjustBuyInitPriceMap = Maps.newHashMap(); // 买入调价的原始挂单价，用于调价计算
    private Map<String/* futu option */, Integer/* over buy price times */> overBuyPriceTimesMap = Maps.newHashMap(); // bid超过买入价的次数

    public ThreadPoolExecutor threadPool = new ThreadPoolExecutor(10, 10, 3600, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

    // listener计算出能交易的股票，待过滤无效和排序截取
    private Set<String> canTradeStocks = Sets.newHashSet();
    // listener计算出能交易的股票及对应前日期权成交量
    //    private Map<String, Long> stockLastOptionVolMap = Maps.newHashMap();
    // 股票对应polygon code
    private Map<String/* stock */, String/* call and put*/> canTradeOptionMap = Maps.newHashMap();
    private Map<String/* stock */, String/* call and put*/> canTradeOption2Map = Maps.newHashMap();
    // code的实时iv
    //    private Map<String, Double> optionRtIvMap = Maps.newHashMap();
    // 股票对应实时iv code
    //    private Map<String, String> canTradeOptionForRtIVMap = Maps.newHashMap();
    // 实时iv code对应富途code
    private Map<String, String> optionCodeMap = Maps.newHashMap();
    // 股票对应富途code
    private Map<String, String> canTradeOptionForFutuMap = Maps.newHashMap();
    private Map<String, String> canTradeOptionForFutu2Map = Maps.newHashMap();
    // 实时iv code对应ikbr code
    private Map<String/* rt iv code */, String/* ikbr code */> rtForIkbrMap = Maps.newHashMap();
    // 富途code对应ikbr code
    private Map<String/* futu code */, String/* ikbr code */> futuForIkbrMap = Maps.newHashMap();
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
    // futu bid price
    private Map<String, Double> codeToBidMap = Maps.newHashMap();
    // futu ask price
    private Map<String, Double> codeToAskMap = Maps.newHashMap();
    // 全部报价
    private Map<String, String> allCodeToQuoteMap = Maps.newHashMap();
    // 成交数据
    private Map<String, Double> codeToTradeMap = Maps.newHashMap();
    // 最新盈亏比例
    private Map<String/* stock */, Double/* ratio */> lastDiffRatioMap = Maps.newHashMap();
    // 无风险利率
    // 平均到每个股票的交易金额
    private double avgFund;
    private double funds = Constants.INIT_CASH;
    private int limitCount = 2; // 限制股票数量
    private long openTime;
    private long closeTime;
    private long invalidTime;
    private long stopBuyTime; // 停止交易时间为开盘后一分钟，但是如果其中一个已经交易则不终止

    public void init() {
        //        FTAPI.init();
        //        futuQuote = new BasicQuote();
        //        futuQuote.start();
        //        tradeApi = new TradeApi();
        //        tradeApi.useSimulateEnv();
        //        tradeApi.setAccountId(TradeApi.simulateUsOptionAccountId);
        //        realTrade = false;
        //        tradeApi.useRealEnv();
        //        tradeApi.start();

        canTradeOptionForFutuMap = optionStockListener.getCanTradeOptionForFutuMap();
        canTradeOptionForFutu2Map = optionStockListener.getCanTradeOptionForFutu2Map();
        optionStrikePriceMap = optionStockListener.getOptionStrikePriceMap();
        optionExpireDateMap = optionStockListener.getOptionExpireDateMap();
        optionCodeMap = optionStockListener.getOptionCodeMap();
        //        canTradeOptionForRtIVMap = optionStockListener.getCanTradeOptionForRtIVMap();
        realtimeQuoteForOptionMap = RealTimeDataWS_DB2.realtimeQuoteForOptionMap;
        riskFreeRate = LoadOptionTradeData.riskFreeRate;
        currentTradeDate = LoadOptionTradeData.currentTradeDate;
        canTradeStocks = optionStockListener.getCanTradeStocks();
        secStockUpDownMap = optionStockListener.getSecStockUpDownMap();
        //        stockLastOptionVolMap = optionStockListener.getStockLastOptionVolMap();
        stockToOpenStrikeDiffMap = optionStockListener.getStockToOpenStrikeDiffMap();
        codeToBidMap = futuQuote.getCodeToBidMap();
        codeToAskMap = futuQuote.getCodeToAskMap();
        canTradeOptionMap = optionStockListener.getCanTradeOptionMap();
        canTradeOption2Map = optionStockListener.getCanTradeOption2Map();
        closeTime = client.getCloseCheckTime().getTime();
        openTime = client.getOpenTime();
        //        rtForIkbrMap = optionStockListener.getRtForIkbrMap();
        futuForIkbrMap = optionStockListener.getFutuForIkbrMap();
        invalidTime = openTime + 15000;
        stopBuyTime = openTime + 60000;
        funds = client.getFunds();
    }

    public void beginTrade() throws InterruptedException {
        tradeApi.reqPosition();

        // 能交易的股票
        if (CollectionUtils.isEmpty(canTradeStocks)) {
            log.info("there is no stock can be traded");
            hasFinishBuying = true;
            client.stopListen();
            return;
        }
        log.info("there are stock can be traded. stock: {}", canTradeStocks);

        // 按前日的总成交量倒排，并过滤掉无效stock
        //        List<String> sortedCanTradeStock = stockLastOptionVolMap.entrySet().stream().sorted((o1, o2) -> o2.getValue().compareTo(o1.getValue())).map(kv -> kv.getKey()).collect(Collectors.toList());
        List<String> cutCanTradeStock = Lists.newArrayList(stockToOpenStrikeDiffMap.values()).subList(0, 3);
        List<String> sortedCanTradeStock = Lists.newArrayList();
        for (String stock : cutCanTradeStock) {
            String callAndPut = canTradeOptionMap.get(stock);
            String[] split = callAndPut.split("\\|");
            String call = split[0];
            String put = split[1];

            boolean canTrade;
            Integer upDown = secStockUpDownMap.get(stock);
            if (upDown > 0) {
                List<Double> callIvList = LoadOptionTradeData.optionToIvListMap.get(call);
                canTrade = callIvList.get(0) < 0.6;
            } else {
                List<Double> putIvList = LoadOptionTradeData.optionToIvListMap.get(put);
                canTrade = putIvList.get(0) < 0.6;
            }
            if (canTrade) {
                sortedCanTradeStock.add(stock);
            }
        }

        if (CollectionUtils.isEmpty(sortedCanTradeStock)) {
            log.info("except invalid stock, there is no stock can be traded");
            hasFinishBuying = true;
            client.stopListen();
            return;
        }

        threadPool.execute(() -> monitorBuyOrder());
        threadPool.execute(() -> monitorSellOrder());
        threadPool.execute(() -> checkCancel());
        stopLossAndGain();

        //        canTradeStocks = Sets.newHashSet(sortedCanTradeStock);
        int size = sortedCanTradeStock.size() > limitCount ? limitCount : sortedCanTradeStock.size();
        avgFund = (int) funds / size / 2; // 该策略每次只用一半资金
        log.info("init avgFund is {}", avgFund);

        /**
         * 2.循环判断canTradeStocks：
         *  2.1.没有iv的 和 有iv没有摆盘的 继续循环判断，直到有摆盘后，执行2.2
         *  2.2.判断摆盘数据即callMid+putMid是否大于avgFund，如果大于则过滤。
         *      如果小于等于，则开始下单，且直到成功后，加入挂单列表，方便后续已下单数量的判断
         *  2.3.当没有股票下单，且剩余股票数量小于限制数量时，重新计算avgFund。
         *      比如初始限制数量=3，初始股票5支，多次循环后剩余股票2支，并且没有股票下单，则限制数量可以调整至2，并重新计算avgFund。
         *      直到限制数量调整至1时，不再调整，保留最后一只股票循环判断直到成交
         *  2.4.当下单数量等于限制数量后，剩下的股票失效
         */
        Set<String> tempInvalidStocks = Sets.newHashSet();
        while (true) {
            for (String stock : sortedCanTradeStock) {
                int hasBoughtOrder = MapUtils.getInteger(hasBoughtOrderMap, stock, NOT_EXIST);
                if (hasBoughtOrder == EXIST) {
                    continue;
                }

                // 2.4.挂单数量除以2（包含call和put）等于限制数量后，剩下的全部失效
                if (buyOrderIdMap.size() / 2 == size) {
                    invalidTradeStock(stock);
                    log.info("trade stock over size={}. stop {} trade", size, stock);
                    continue;
                }

                // 2.3.当没有股票下单，且剩余股票数量小于限制数量时，重新计算avgFund。
                if (MapUtils.isEmpty(buyOrderIdMap) && canTradeStocks.size() - tempInvalidStocks.size() < size) {
                    size = canTradeStocks.size() - tempInvalidStocks.size();
                    avgFund = (int) funds / size;
                    log.info("adjust avgFund is {}, size={}", avgFund, size);
                }

                long curTime = System.currentTimeMillis();
                if (curTime > stopBuyTime) {
                    invalidTradeStock(stock);
                    log.info("trade time out 1 min. stop {} trade", stock);
                    continue;
                }

                // 测试阶段用预定限额控制下单股票
                //                if (funds < 0) {
                //                    invalidTradeStock(stock);
                //                    log.info("over fund. stop {} trade", stock);
                //                    continue;
                //                }
                // 测试阶段用预定限额控制下单股票

                // 2.1.没有iv的 和 有iv没有摆盘的 继续循环判断
                String callAndPut = canTradeOptionMap.get(stock);
                String[] split = callAndPut.split("\\|");
                String call = split[0];
                String put = split[1];

                String callAndPutFutu = canTradeOptionForFutuMap.get(stock);
                String[] splitFutu = callAndPutFutu.split("\\|");
                String callFutu = splitFutu[0];
                String putFutu = splitFutu[1];
                String callAndPutFutu2 = canTradeOptionForFutu2Map.get(stock);
                String[] splitFutu2 = callAndPutFutu2.split("\\|");
                String call2Futu = splitFutu2[0];
                String put2Futu = splitFutu2[1];

                String callIkbr = futuForIkbrMap.get(callFutu);
                String putIkbr = futuForIkbrMap.get(putFutu);
                String call2Ikbr = futuForIkbrMap.get(call2Futu);
                String put2Ikbr = futuForIkbrMap.get(put2Futu);

                double callMidPrice = calculateMidPrice(callFutu);
                double putMidPrice = calculateMidPrice(putFutu);
                double call2MidPrice = calculateMidPrice(call2Futu);
                double put2MidPrice = calculateMidPrice(put2Futu);

                double tradeTotal;
                Integer upDown = secStockUpDownMap.get(stock);
                if (upDown > 0) {
                    if (callMidPrice < call2MidPrice) {
                        invalidTradeStock(stock);
                        log.info("trade stock option less than option2. call={}\tcallMid={}\tcall2={}\tcall2Mid={}. stop {} trade", callFutu, callMidPrice, call2Futu, call2MidPrice, stock);
                        continue;
                    }
                    tradeTotal = callMidPrice - call2MidPrice;
                } else {
                    if (putMidPrice < put2MidPrice) {
                        invalidTradeStock(stock);
                        log.info("trade stock option less than option2. put={}\tputMid={}\tput2={}\tput2Mid={}. stop {} trade", putFutu, putMidPrice, put2Futu, put2MidPrice, stock);
                        continue;
                    }
                    tradeTotal = putMidPrice - put2MidPrice;
                }

                double countDouble = avgFund / tradeTotal / 100;
                // 测试阶段，临时限制每次交易数量
                //                if (tradeTotal < 0.5) {
                //                    countDouble = 2d;
                //                } else {
                //                    countDouble = 1d;
                //                }
                // 测试阶段，临时限制每次交易数量

                // 2.2.判断摆盘数据即callTrade+putTrade是否大于avgFund，如果大于则过滤。
                if (countDouble < 1) {
                    invalidTradeStock(stock);
                    log.info("the price is greater than avgFund. callAndPut={}", callAndPut);
                    tempInvalidStocks.add(stock);
                    continue;
                }

                // （勿删！！！！）仅在满仓投入时才使用以下逻辑：如果下单数量小数位小于等于0.5，取整要减一，如果小数位大于0.5，则不变。目的是为了后面如果改价避免成交数量超过限制。但是如果count=1则不减一
                //                String countStr = String.valueOf(countDouble);
                //                int dotIndex = countStr.indexOf(".");
                //                String dotStr = countStr.substring(dotIndex + 1, dotIndex + 2);
                //                double count = (int) countDouble;
                //                if (Integer.valueOf(dotStr) <= 5 && count > 1) {
                //                    count = (int) countDouble - 1;
                //                }
                //                if (count <= 0) {
                //                    invalidTradeStock(stock);
                //                    log.info("count is illegal. stock={}, count={}", stock, count);
                //                    tempInvalidStocks.add(stock);
                //                    continue;
                //                }
                double count = (int) countDouble;
                // todo 价差交易提交，然后确认交易成功后，提交止损单
                if (upDown > 0) {
                    int callConId = tradeApi.getOptionConId(callIkbr);
                    int call2ConId = tradeApi.getOptionConId(call2Ikbr);
                    int orderId = tradeApi.buySpread(stock, call2ConId, callConId, count);
                    Order order = tradeApi.getOrder(orderId);
                    double avgPrice = order.getAvgPrice();
                    double stopLossPrice = BigDecimal.valueOf(avgPrice * (1 + STOP_LOSS_RATIO)).setScale(2, RoundingMode.HALF_UP).doubleValue();
                    tradeApi.stopLossForBuySpread(orderId, stopLossPrice);
                }else{
                    int putConId = tradeApi.getOptionConId(putIkbr);
                    int put2ConId = tradeApi.getOptionConId(put2Ikbr);
                    int orderId = tradeApi.buySpread(stock, put2ConId, putConId, count);
                    Order order = tradeApi.getOrder(orderId);
                    double avgPrice = order.getAvgPrice();
                    double stopLossPrice = BigDecimal.valueOf(avgPrice * (1 + STOP_LOSS_RATIO)).setScale(2, RoundingMode.HALF_UP).doubleValue();
                    tradeApi.stopLossForBuySpread(orderId, stopLossPrice);
                }

                if (upDown > 0) {
                    futuQuote.addUserSecurity(callFutu);
                    futuQuote.addUserSecurity(call2Futu);
                } else {
                    futuQuote.addUserSecurity(putFutu);
                    futuQuote.addUserSecurity(put2Futu);
                }
                futuQuote.addUserSecurity(stock);
                log.info("begin trade: buyCallOrder={}\tcall={}\tcallPrice={}\tbuyPutOrder={}\tput={}\tputPrice={}\tcount={}", buyCallOrderId, call, callMidPrice, buyPutOrderId, put, putMidPrice, count);

                // 测试阶段用预定限额控制下单股票
                //                funds = funds - tradeTotal * 100;
                // 测试阶段用预定限额控制下单股票

                lastBuyPriceMap.put(callIkbr, callMidPrice);
                lastBuyPriceMap.put(putIkbr, putMidPrice);
                orderCountMap.put(stock, count);
                buyOrderTimeMap.put(stock, curTime);
                buyOrderIdMap.put(callIkbr, buyCallOrderId);
                buyOrderIdMap.put(putIkbr, buyPutOrderId);
                adjustBuyInitPriceMap.put(callFutu, callMidPrice);
                adjustBuyInitPriceMap.put(putFutu, putMidPrice);
                adjustBuyPriceTimesMap.put(callFutu, 0);
                adjustBuyPriceTimesMap.put(putFutu, 0);
                adjustBuyPriceTimestampMap.put(callFutu, curTime);
                adjustBuyPriceTimestampMap.put(putFutu, curTime);
                ReadWriteOptionTradeInfo.writeOrderCount(stock, count);
                ReadWriteOptionTradeInfo.writeBuyOrderTime(stock, curTime);
                ReadWriteOptionTradeInfo.writeBuyOrderId(callIkbr, buyCallOrderId);
                ReadWriteOptionTradeInfo.writeBuyOrderId(putIkbr, buyPutOrderId);
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

        Map<String, Double> buyOrderCostMap = ReadWriteOptionTradeInfo.readBuyOrderCost();
        for (String option : buyOrderIdMap.keySet()) {
            Long orderId = buyOrderIdMap.get(option);
            Double cost = buyOrderCostMap.get(option);
            if (cost == null) {
                log.warn("restart buy order cost is null. option={}", option);
                continue;
            }

            int index = option.indexOf(" ");
            String stock = option;
            if (index > 0) {
                stock = option.substring(0, index);
            }
            Double count = MapUtils.getDouble(orderCountMap, stock, 0d);

            tradeApi.rebuildOrderHandler(orderId, cost, count, OrderStatus.Filled);
        }

        try {
            beginTrade();
        } catch (Exception e) {
            log.error("re begin trade error", e);
        }
    }

    public void invalidTradeStock(String stock) {
        hasBoughtSuccess.add(stock);
        hasBoughtOrderMap.put(stock, EXIST);
        hasSoldSuccess.add(stock);
        hasSoldOrderMap.put(stock, EXIST);
        ReadWriteOptionTradeInfo.writeHasBoughtOrder(stock);
        ReadWriteOptionTradeInfo.writeHasBoughtSuccess(stock);
        ReadWriteOptionTradeInfo.writeHasSoldOrder(stock);
        ReadWriteOptionTradeInfo.writeHasSoldSuccess(stock);
        invalidStocks.add(stock);

        log.info("invalid stock: {}", stock);

        delayUnsubscribeIv(stock);
        delayUnsubscribeQuote(stock);
        client.unsubscribe(stock);
    }

    public void monitorFutuDeep(String optionCode) {
        futuQuote.subOrderBook(optionCode);
        log.info("monitor futu option deep: {}", optionCode);
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

    public void delayUnsubscribeIv(String stock) {
        if (StringUtils.isBlank(stock)) {
            log.info("delayUnsubscribeIv error. stock is blank");
            return;
        }

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                String callAndPut = MapUtils.getString(canTradeOptionForFutuMap, stock, "");
                if (StringUtils.isNotBlank(callAndPut)) {
                    String[] split = callAndPut.split("\\|");
                    String call = split[0];
                    String put = split[1];
                    futuQuote.unSubBasicQuote(call);
                    futuQuote.unSubBasicQuote(put);
                    log.info("unsubscribe futu iv. stock={}\toption={}", stock, callAndPut);
                }
                timer.cancel();
            }
        }, 1000 * 60 * 1);
    }

    public void delayUnsubscribeQuote(String stock) {
        if (StringUtils.isBlank(stock)) {
            log.info("delayUnsubscribeQuote error. stock is blank");
            return;
        }

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                String callAndPut = MapUtils.getString(canTradeOptionForFutuMap, stock, "");
                if (StringUtils.isNotBlank(callAndPut)) {
                    String[] split = callAndPut.split("\\|");
                    String call = split[0];
                    String put = split[1];
                    futuQuote.unSubOrderBook(call);
                    futuQuote.unSubOrderBook(put);
                    log.info("unsubscribe futu quote. stock={}\toption={}", stock, callAndPut);
                }
                timer.cancel();
            }
        }, 1000 * 60 * 1);
    }

    public void disableShowIv(String callFutu, String putFutu) {
        futuQuote.setShowTradePrice(callFutu);
        futuQuote.setShowTradePrice(putFutu);
    }

    public boolean checkHasNoDeep(String callFutu, String putFutu) {
        Double callBidPrice = codeToBidMap.get(callFutu);
        Double callAskPrice = codeToAskMap.get(callFutu);
        Double putBidPrice = codeToBidMap.get(putFutu);
        Double putAskPrice = codeToAskMap.get(putFutu);
        if (callBidPrice == null || callAskPrice == null || putBidPrice == null || putAskPrice == null) {
            return true;
        }
        return false;
    }

    public double calculateMidPrice(String futu) {
        double bidPrice = codeToBidMap.get(futu);
        double askPrice = codeToAskMap.get(futu);
        double midPrice = BigDecimal.valueOf((bidPrice + askPrice) / 2).setScale(2, RoundingMode.DOWN).doubleValue();
        return midPrice;
    }

    public void close() {
        tradeApi.end();
    }

    public static void main(String[] args) throws InterruptedException {
        String option = "NVDA  240816C00115000";
        int index = option.indexOf(" ");
        String stock = option;
        if (index > 0) {
            stock = option.substring(0, index);
        }
        System.out.println(stock);
    }
}
