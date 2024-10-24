package luonq.polygon;

import bean.NodeList;
import bean.Order;
import bean.StockEvent;
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
import luonq.listener.OptionStockListener5;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import util.Constants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Iterator;
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
    private RealTimeDataWS_DB5 client;
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
    public static Set<String> piorityStocks = Sets.newHashSet("AAPL", "TSM", "GOOG");
    public static Map<String, Double> priorityStocksWithStrikeDiff = Maps.newHashMap();
    public static final double STOP_LOSS_RATIO = -1d; // 全交易时段的止损比例
    public static final double STOP_GAIN_RATIO = 1d; // 三小时前的止盈比例
    public static final long STOP_GAIN_INTERVAL_TIME_LINE = 3 * 60 * 60 * 1000L; // 三小时止盈时间点
    public static final int ADJUST_SELL_TRADE_PRICE_TIMES = 10; // 卖出挂单价调价次数上限
    public static final int ADJUST_BUY_TRADE_PRICE_TIMES = 5; // 买入挂单价调价次数上限
    public static final long ADJUST_SELL_PRICE_TIME_INTERVAL = 4 * 1000L; // 卖出挂单价调价间隔4秒
    public static final long ADJUST_BUY_PRICE_TIME_INTERVAL = 3 * 1000L; // 买入挂单价调价间隔2秒
    public static final List<Long> checkPoints = Lists.newArrayList(1800000L, 3600000L, 7200000L, 10800000L, 14400000L, 18000000L, 21600000L);

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
    private Map<String/* futu option */, Integer/* orderId */> buyOrderIdMap = Maps.newHashMap(); // 下单买入的订单id
    private Map<String/* futu option */, Integer/* orderId */> sellOrderIdMap = Maps.newHashMap(); // 下单卖出的订单id
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
    // poloygon对应futu code
    private Map<String, String> optionForFutuMap = Maps.newHashMap();
    // poloygon对应ibkr code
    private Map<String, String> optionForIbkrMap = Maps.newHashMap();
    // 实时iv code对应ikbr code
    private Map<String/* rt iv code */, String/* ikbr code */> rtForIkbrMap = Maps.newHashMap();
    // 富途code对应ikbr code
    private Map<String/* futu code */, String/* ikbr code */> futuForIkbrMap = Maps.newHashMap();
    // ibkr code对应conid
    private Map<String/* ibkr code */, Integer/* conid */> ibkrForConIdMap = Maps.newHashMap();
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
    private int limitCount = 5; // 限制股票数量
    private int limitWaitTradeCount = 50;
    private long openTime;
    private long closeTime;
    private long invalidTime;
    private long buyTime; // 开始交易时间
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
        optionForFutuMap = optionStockListener.getOptionForFutuMap();
        optionForIbkrMap = optionStockListener.getOptionForIbkrMap();
        closeTime = client.getCloseCheckTime().getTime();
        openTime = client.getOpenTime();
        //        rtForIkbrMap = optionStockListener.getRtForIkbrMap();
        futuForIkbrMap = optionStockListener.getFutuForIbkrMap();
        buyTime = openTime + 60000;
        invalidTime = openTime + 15000;
        stopBuyTime = openTime + 60000;
        funds = client.getFunds();
    }

    public void beginTrade() throws Exception {
        tradeApi.reqPosition();

        // 能交易的股票
        if (CollectionUtils.isEmpty(canTradeStocks)) {
            log.info("there is no stock can be traded");
            hasFinishBuying = true;
            client.stopListen();
            return;
        }
        log.info("there are stock can be traded. stock: {}", stockToOpenStrikeDiffMap);
        canTradeStocks.clear();

        List<String> stockToOpenStrikeDiff = Lists.newArrayList(stockToOpenStrikeDiffMap.values());
        int size = 0;
        for (String stock : stockToOpenStrikeDiff) {
            Integer upDown = secStockUpDownMap.get(stock);
            if (upDown < 0) {
                continue;
            }

            canTradeStocks.add(stock);
            String callAndPut = canTradeOptionMap.get(stock);
            String callAndPut2 = canTradeOption2Map.get(stock);
            String[] split = callAndPut.split("\\|");
            String[] split2 = callAndPut2.split("\\|");
            String call = split[0];
            String call2 = split2[0];
            String futuCall = optionForFutuMap.get(call);
            String futuCall2 = optionForFutuMap.get(call2);
            monitorFutuDeep(futuCall);
            monitorFutuDeep(futuCall2);

            String ibkrCall = optionForIbkrMap.get(call);
            String ibkrCall2 = optionForIbkrMap.get(call2);
            int callConId = tradeApi.getOptionConId(ibkrCall);
            int call2ConId = tradeApi.getOptionConId(ibkrCall2);
            ibkrForConIdMap.put(ibkrCall, callConId);
            ibkrForConIdMap.put(ibkrCall2, call2ConId);

            size++;
            if (size == limitWaitTradeCount) {
                break;
            }
        }

        Map<String, Double> strikePriceDiffSortedMap = Maps.newHashMap();
        while (true) {
            if (System.currentTimeMillis() > buyTime) {
                break;
            }

            for (Iterator<String> itr = canTradeStocks.iterator(); itr.hasNext(); ) {
                String stock = itr.next();
                Integer upDown = secStockUpDownMap.get(stock);
                if (upDown < 0) { // todo 因为现金账户不能对call进行熊市价差，所以暂时过滤，等恢复保证金账户后再放开
                    continue;
                }

                String callAndPut = canTradeOptionMap.get(stock);
                String callAndPut2 = canTradeOption2Map.get(stock);
                String[] split = callAndPut.split("\\|");
                String[] split2 = callAndPut2.split("\\|");
                String call = split[0];
                String call2 = split2[0];
                Double callStrikePrice = optionStrikePriceMap.get(call);
                Double callStrikePrice2 = optionStrikePriceMap.get(call2);
                String futuCall = optionForFutuMap.get(call);
                String futuCall2 = optionForFutuMap.get(call2);
                double callMid = calculateMidPrice(futuCall);
                double call2Mid = calculateMidPrice(futuCall2);

                // 如果任何一个期权当前摆盘=0，或者二档期权大于一档期权的，都认为数据无效
                if (callMid == 0d || call2Mid == 0d || call2Mid > callMid) {
                    strikePriceDiffSortedMap.remove(stock);
                    continue;
                }

                double midDiff = callMid - call2Mid;
                double strikePriceDiff = callStrikePrice2 - callStrikePrice;
                double diffRatio = BigDecimal.valueOf(midDiff / strikePriceDiff * 100).setScale(2, RoundingMode.HALF_UP).doubleValue();

                if (piorityStocks.contains(stock)) {
                    priorityStocksWithStrikeDiff.put(stock, diffRatio);
                } else {
                    strikePriceDiffSortedMap.put(stock, diffRatio);
                }
            }
        }
        log.info("strikePriceDiffSortedMap: {}", strikePriceDiffSortedMap);
        log.info("priorityStocksWithStrikeDiff: {}", priorityStocksWithStrikeDiff);

        // 按前日的总成交量倒排，并过滤掉无效stock
        //        List<String> sortedCanTradeStock = stockLastOptionVolMap.entrySet().stream().sorted((o1, o2) -> o2.getValue().compareTo(o1.getValue())).map(kv -> kv.getKey()).collect(Collectors.toList());
        //        List<String> cutCanTradeStock = Lists.newArrayList(stockToOpenStrikeDiffMap.values()).subList(0, 3);

        int tradeCount = 0;
        List<String> sortedCanTradeStock = Lists.newArrayList();

        List<String> priorityTradeStock = priorityStocksWithStrikeDiff.entrySet().stream().sorted((o1, o2) -> o2.getValue().compareTo(o1.getValue())).map(v -> v.getKey()).collect(Collectors.toList());
        List<String> strikePriceDiffSorted = strikePriceDiffSortedMap.entrySet().stream().sorted((o1, o2) -> o2.getValue().compareTo(o1.getValue())).map(v -> v.getKey()).collect(Collectors.toList());
        log.info("priority stock: {}", priorityTradeStock);
        for (String stock : priorityTradeStock) {
            if (hasBoughtSuccess.contains(stock)) {
                continue;
            }
            if (placeOrder(stock)) {
                tradeCount++;
                hasBoughtSuccess.add(stock);
            } else {
                continue;
            }
            if (tradeCount == 2) {
                break;
            }
        }

        for (String stock : strikePriceDiffSorted) {
            if (hasBoughtSuccess.contains(stock)) {
                continue;
            }

            if (placeOrder(stock)) {
                tradeCount++;
                hasBoughtSuccess.add(stock);
            } else {
                continue;
            }

            if (tradeCount == limitCount) {
                break;
            }
        }
        for (String stock : strikePriceDiffSorted) {
            if (!hasBoughtSuccess.contains(stock)) {
                delayUnsubscribeQuote(stock);
            }
        }

        checkStopLoss();
    }

    private void checkStopLoss() throws InterruptedException {
        int index = 0;
        while (true) {
            if (System.currentTimeMillis() > closeTime) {
                break;
            }
            if (hasSoldSuccess.containsAll(hasBoughtSuccess) || index >= checkPoints.size()) {
                break;
            }
            if (System.currentTimeMillis() >= openTime + checkPoints.get(index)) {
                for (String stock : hasBoughtSuccess) {
                    if (hasSoldSuccess.contains(stock)) {
                        continue;
                    }

                    String callAndPut = canTradeOptionMap.get(stock);
                    String callAndPut2 = canTradeOption2Map.get(stock);
                    String[] split = callAndPut.split("\\|");
                    String[] split2 = callAndPut2.split("\\|");
                    String call = split[0];
                    String call2 = split2[0];
                    String ibkrCall = optionForIbkrMap.get(call);
                    String futuCall = optionForFutuMap.get(call);
                    String futuCall2 = optionForFutuMap.get(call2);
                    double callMidPrice = calculateMidPrice(futuCall);
                    double call2MidPrice = calculateMidPrice(futuCall2);
                    double diffPrice = call2MidPrice - callMidPrice;

                    Integer orderId = buyOrderIdMap.get(ibkrCall);
                    Order order = tradeApi.getOrder((long) orderId);
                    double cost = order.getAvgPrice();
                    double diffRatio = BigDecimal.valueOf((cost - diffPrice) / cost).setScale(3, RoundingMode.HALF_UP).doubleValue();
                    //                    double stopLossPrice = BigDecimal.valueOf(cost * (1 + STOP_LOSS_RATIO)).setScale(2, RoundingMode.HALF_UP).doubleValue();
                    log.info("check loss for spread. call={}\tcallPrice={}\tcall2={}\tcall2Price={}\tcost={}\tdiffPrice={}\tdiffRatio={}\torderId={}", futuCall, callMidPrice, futuCall2, call2MidPrice, cost, diffPrice, diffRatio, orderId);
                    if (diffRatio < STOP_LOSS_RATIO) {
                        tradeApi.stopForBuySpread_mkt(orderId);
                        log.info("stop loss for spread. call={}\tcallPrice={}\tcall2={}\tcall2Price={}\tcost={}\tdiffPrice={}\tdiffRatio={}\torderId={}", futuCall, callMidPrice, futuCall2, call2MidPrice, cost, diffPrice, diffRatio, orderId);
                        hasSoldSuccess.add(stock);
                    }
                }
                index++;
            } else {
                TimeUnit.SECONDS.sleep(1);
            }
        }
    }

    public boolean placeOrder(String stock) throws Exception {
        String callAndPut = canTradeOptionMap.get(stock);
        String callAndPut2 = canTradeOption2Map.get(stock);
        String[] split = callAndPut.split("\\|");
        String[] split2 = callAndPut2.split("\\|");
        String call = split[0];
        String call2 = split2[0];
        String ibkrCall = optionForIbkrMap.get(call);
        String ibkrCall2 = optionForIbkrMap.get(call2);
        String futuCall = optionForFutuMap.get(call);
        String futuCall2 = optionForFutuMap.get(call2);
        double callMid = calculateMidPrice(futuCall);
        double call2Mid = calculateMidPrice(futuCall2);
        double midDiff = callMid - call2Mid;
        if (midDiff < 0) {
            return false;
        }

        double count = avgFund / midDiff / 100;
        count = 1d; // todo 模拟盘线尝试1张期权
        int callConId = ibkrForConIdMap.get(ibkrCall);
        int call2ConId = ibkrForConIdMap.get(ibkrCall2);
        int orderId = tradeApi.buySpread(stock, call2ConId, callConId, count);
        if (orderId == -1) {
            log.info("failed trade: buyCallOrder={}\tcall={}\tcall2={}\tcount={}", orderId, futuCall, futuCall2, count);
            return false;
        }

        Order order = tradeApi.getOrder(orderId);
        double avgPrice = order.getAvgPrice();
        double stopGainPrice = -0.01d;
        tradeApi.stopForBuySpread_lmt(orderId, stopGainPrice);
        log.info("finish trade: buyCallOrder={}\tcall={}\tcall2={}\tcount={}\tcost={}", orderId, futuCall, futuCall2, count, avgPrice);

        futuQuote.addUserSecurity(futuCall);
        futuQuote.addUserSecurity(futuCall2);

        buyOrderIdMap.put(ibkrCall, orderId);
        ReadWriteOptionTradeInfo.writeBuyOrderId(ibkrCall, (long) orderId);
        ReadWriteOptionTradeInfo.writeOrderCount(stock, count);
        ReadWriteOptionTradeInfo.writeBuyOrderTime(stock, System.currentTimeMillis());
        ReadWriteOptionTradeInfo.writeHasBoughtOrder(stock);
        hasBoughtOrderMap.put(stock, EXIST);
        return true;
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
        hasBoughtSuccess = ReadWriteOptionTradeInfo.readHasBoughtSuccess();
        hasSoldSuccess = ReadWriteOptionTradeInfo.readHasSoldSuccess();
        buyOrderTimeMap = ReadWriteOptionTradeInfo.readBuyOrderTime();
        sellOrderTimeMap = ReadWriteOptionTradeInfo.readSellOrderTime();
        orderCountMap = ReadWriteOptionTradeInfo.readOrderCount();

        hasBoughtSuccess = hasBoughtSuccess.stream().filter(s -> canTradeStocks.contains(s)).collect(Collectors.toSet());
        hasSoldSuccess = hasSoldSuccess.stream().filter(s -> canTradeStocks.contains(s)).collect(Collectors.toSet());

        Map<String, Double> buyOrderCostMap = ReadWriteOptionTradeInfo.readBuyOrderCost();
        for (String option : buyOrderIdMap.keySet()) {
            int orderId = buyOrderIdMap.get(option);
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
            checkStopLoss();
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
                    futuQuote.unSubBasicQuote(call);
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
                    futuQuote.unSubOrderBook(call);
                    log.info("unsubscribe futu quote. stock={}\toption={}", stock, callAndPut);
                }
                String callAndPut2 = MapUtils.getString(canTradeOptionForFutu2Map, stock, "");
                if (StringUtils.isNotBlank(callAndPut2)) {
                    String[] split = callAndPut2.split("\\|");
                    String call = split[0];
                    futuQuote.unSubOrderBook(call);
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
        double bidPrice = MapUtils.getDouble(codeToBidMap, futu, 0d);
        double askPrice = MapUtils.getDouble(codeToAskMap, futu, 0d);
        if (bidPrice == 0d || askPrice == 0) {
            return 0d;
        }

        double midPrice = BigDecimal.valueOf((bidPrice + askPrice) / 2).setScale(2, RoundingMode.DOWN).doubleValue();
        return midPrice;
    }

    public void close() {
        tradeApi.end();
    }

    public static void main(String[] args) throws Exception {
    }
}
