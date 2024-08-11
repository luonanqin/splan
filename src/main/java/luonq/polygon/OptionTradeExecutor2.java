package luonq.polygon;

import bean.NodeList;
import bean.Order;
import bean.StockEvent;
import bean.StockPosition;
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
import luonq.ibkr.TradeApi;
import luonq.listener.OptionStockListener2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import util.BaseUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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

import static util.Constants.TRADE_ERROR_CODE;
import static util.Constants.TRADE_PROHIBT_CODE;

/**
 * Created by Luonanqin on 2023/5/9.
 */
@Component
@Data
@Slf4j
public class OptionTradeExecutor2 {

    private NodeList list;
    private BasicQuote futuQuote;
    private GetOptionChain getOptionChain;
    private int cut = 990000;
    private List<String> tradeStock = Lists.newArrayList();
    private RealTimeDataWS_DB2 client;
    private boolean realTrade = true;
    private OptionStockListener2 optionStockListener;
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
    public static final long ADJUST_SELL_PRICE_TIME_INTERVAL = 4 * 1000L; // 卖出挂单价调价间隔4秒
    public static final long ADJUST_BUY_PRICE_TIME_INTERVAL = 4 * 1000L; // 买入挂单价调价间隔4秒

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
    private Map<String/* ikbr option */, Double/* buy price */> lastBuyPriceMap = Maps.newHashMap(); // 最新一次买入挂单价
    private Map<String/* ikbr option */, Double/* sell price */> lastSellPriceMap = Maps.newHashMap(); // 最新一次卖出挂单价
    private Map<String/* futu option */, Double/* sell price */> adjustSellInitPriceMap = Maps.newHashMap(); // 卖出调价的原始挂单价，用于调价计算
    private Map<String/* futu option */, Double/* buy price */> adjustBuyInitPriceMap = Maps.newHashMap(); // 买入调价的原始挂单价，用于调价计算

    public ThreadPoolExecutor threadPool = new ThreadPoolExecutor(10, 10, 3600, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

    // listener计算出能交易的股票，待过滤无效和排序截取
    private Set<String> canTradeStocks = Sets.newHashSet();
    // listener计算出能交易的股票及对应前日期权成交量
    private Map<String, Long> stockLastOptionVolMap = Maps.newHashMap();
    // 股票对应polygon code
    private Map<String/* stock */, String/* call and put*/> canTradeOptionMap = Maps.newHashMap();
    // code的实时iv
    private Map<String, Double> optionRtIvMap = Maps.newHashMap();
    // 股票对应实时iv code
    private Map<String, String> canTradeOptionForRtIVMap = Maps.newHashMap();
    // 实时iv code对应富途code
    private Map<String, String> optionCodeMap = Maps.newHashMap();
    // 股票对应富途code
    private Map<String, String> canTradeOptionForFutuMap = Maps.newHashMap();
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
    // 无风险利率
    // 平均到每个股票的交易金额
    private double avgFund;
    private double funds = 1400; // todo 测试用要删
    private long openTime;
    private long closeTime;
    private long invalidTime;

    public void init() {
        FTAPI.init();
        futuQuote = new BasicQuote();
        futuQuote.start();
        tradeApi = new TradeApi();
        //        tradeApi.useSimulateEnv();
        //        tradeApi.setAccountId(TradeApi.simulateUsOptionAccountId);
        //        realTrade = false;
        tradeApi.useRealEnv();
        tradeApi.start();
        tradeApi.unlock();
        //        tradeApi.clearStopLossStockSet();

        canTradeOptionForFutuMap = optionStockListener.getCanTradeOptionForFutuMap();
        optionStrikePriceMap = optionStockListener.getOptionStrikePriceMap();
        optionExpireDateMap = optionStockListener.getOptionExpireDateMap();
        optionCodeMap = optionStockListener.getOptionCodeMap();
        canTradeOptionForRtIVMap = optionStockListener.getCanTradeOptionForRtIVMap();
        realtimeQuoteForOptionMap = RealTimeDataWS_DB2.realtimeQuoteForOptionMap;
        riskFreeRate = LoadOptionTradeData.riskFreeRate;
        currentTradeDate = LoadOptionTradeData.currentTradeDate;
        canTradeStocks = optionStockListener.getCanTradeStocks();
        stockLastOptionVolMap = optionStockListener.getStockLastOptionVolMap();
        codeToBidMap = futuQuote.getCodeToBidMap();
        codeToAskMap = futuQuote.getCodeToAskMap();
        canTradeOptionMap = optionStockListener.getCanTradeOptionMap();
        closeTime = client.getCloseCheckTime().getTime();
        openTime = client.getOpenTime();
        rtForIkbrMap = optionStockListener.getRtForIkbrMap();
        futuForIkbrMap = optionStockListener.getFutuForIkbrMap();
        invalidTime = openTime + 15000;
    }

    public void beginTrade() throws InterruptedException {
        // 能交易的股票
        if (CollectionUtils.isEmpty(canTradeStocks)) {
            log.info("there is no stock can be traded");
            hasFinishBuying = true;
            return;
        }
        log.info("there are stock can be traded. stock: {}", canTradeStocks);

        // 按前日的总成交量倒排，并过滤掉无效stock
        List<String> sortedCanTradeStock = stockLastOptionVolMap.entrySet().stream().sorted((o1, o2) -> o2.getValue().compareTo(o1.getValue())).map(kv -> kv.getKey()).collect(Collectors.toList());
        sortedCanTradeStock.removeAll(invalidStocks);
        if (CollectionUtils.isEmpty(sortedCanTradeStock)) {
            log.info("except invalid stock, there is no stock can be traded");
            hasFinishBuying = true;
            return;
        }

        //        getOrder();
        threadPool.execute(() -> monitorBuyOrder());
        threadPool.execute(() -> monitorSellOrder());
        stopLossAndGain();
        //        delayUnsubscribeIv();

        // 开盘后15s再开始交易逻辑
        while (System.currentTimeMillis() < invalidTime) {
            Thread.sleep(500);
            log.info("wait trade...");
        }

        canTradeStocks = Sets.newHashSet(sortedCanTradeStock);
        int size = sortedCanTradeStock.size() > 3 ? 3 : sortedCanTradeStock.size();
        avgFund = (int) funds / size;
        //        List<String> tempCanTradeStock = Lists.newArrayList();
        // 如果已排序后的股票超过1只，则从size=3开始计算哪些股票价格符合条件（size根据本金大小进行调整）
        //        if (size > 1) {
        //            while (true) {
        //                double avgFund = funds / size;
        //                List<String> tempInvalid = Lists.newArrayList();
        //                for (String stock : sortedCanTradeStock) {
        //                    String callAndPut = canTradeOptionForFutuMap.get(stock);
        //                    String callAndPutRt = canTradeOptionForRtIVMap.get(stock);
        //
        //                    String[] splitRt = callAndPutRt.split("\\|");
        //                    String callRt = splitRt[0];
        //                    String putRt = splitRt[1];
        //                    Double callIv = optionRtIvMap.get(callRt);
        //                    Double putIv = optionRtIvMap.get(putRt);
        //
        //                    String[] split = callAndPut.split("\\|");
        //                    String callFutu = split[0];
        //                    String putFutu = split[1];
        //                    Double callBidPrice = codeToBidMap.get(callFutu);
        //                    Double callAskPrice = codeToAskMap.get(callFutu);
        //                    Double putBidPrice = codeToBidMap.get(putFutu);
        //                    Double putAskPrice = codeToAskMap.get(putFutu);
        //
        //                    // 如果已经有iv但是没有摆盘，则不交易该股票
        //                    if (callIv != null && putIv != null && (callBidPrice == null || callAskPrice == null || putBidPrice == null || putAskPrice == null)) {
        //                        log.info("there is iv but no quote. call and put={}", callAndPut);
        //                        invalidTradeStock(stock);
        //                        tempInvalid.add(stock);
        //                        continue;
        //                    }
        //
        //                    if ((callBidPrice == null || callAskPrice == null || putBidPrice == null || putAskPrice == null)) {
        //                    }
        //                    double callMidPrice = BigDecimal.valueOf((callBidPrice + callAskPrice) / 2).setScale(2, RoundingMode.UP).doubleValue();
        //                    double putMidPrice = BigDecimal.valueOf((putBidPrice + putAskPrice) / 2).setScale(2, RoundingMode.UP).doubleValue();
        //                    if (100 * (callMidPrice + putMidPrice) < avgFund) {
        //                        tempCanTradeStock.add(stock);
        //                    }
        //                }
        //                sortedCanTradeStock.removeAll(tempInvalid);
        //                if (tempCanTradeStock.size() >= size || size < 2) {
        //                    break;
        //                } else {
        //                    tempCanTradeStock.clear();
        //                    size--;
        //                }
        //            }
        //            log.info("after calucate by size {}, sortedCanTrade={}\ttempCanTrade={}", size, sortedCanTradeStock, tempCanTradeStock);
        //            for (Iterator<String> iter = sortedCanTradeStock.iterator(); iter.hasNext(); ) {
        //                String next = iter.next();
        //                if (!tempCanTradeStock.contains(next)) {
        //                    invalidTradeStock(next);
        //                    iter.remove();
        //                }
        //            }
        //        }

        //        if (sortedCanTradeStock.size() > size) {
        //            canTradeStocks = Sets.newHashSet(sortedCanTradeStock.subList(0, size));
        //            List<String> inteceptStock = sortedCanTradeStock.subList(size, sortedCanTradeStock.size());
        //            inteceptStock.forEach(s -> invalidTradeStock(s));
        //            log.info("can trade stock size > {}. after intercept, the stocks are {}", size, canTradeStocks);
        //        } else {
        //            canTradeStocks = Sets.newHashSet(sortedCanTradeStock);
        //            log.info("can trade stock size <= {}. don't intercept, the stocks are {}", size, canTradeStocks);
        //        }

        //        int actualSize = canTradeStocks.size();
        //        for (String stock : canTradeStocks) {
        //            String callAndPut = canTradeOptionForRtIVMap.get(stock);
        //            String[] split = callAndPut.split("\\|");
        //            String callRt = split[0];
        //            String putRt = split[1];
        //            Double callIv = optionRtIvMap.get(callRt);
        //            Double putIv = optionRtIvMap.get(putRt);
        //            if (callIv == null || putIv == null) {
        //                continue;
        //            }
        //
        //            String callFutu = optionCodeMap.get(callRt);
        //            String putFutu = optionCodeMap.get(putRt);
        //            boolean hasNoDeep = checkHasNoDeep(callFutu, putFutu);
        //            if (hasNoDeep) {
        //                invalidTradeStock(stock);
        //                actualSize--;
        //                log.info("before trade, check no option quote. stock={}", stock);
        //            }
        //        }

        // 均分账户资金
        //        double funds = tradeApi.getFunds();
        //        int actualSize = canTradeStocks.size() - invalidStocks.size();
        //        if (actualSize == 0) {
        //            log.info("all stock has been sold. exit");
        //            return;
        //        } else if (actualSize < 0) {
        //            log.info("can trade stock size is illegal. exit");
        //            return;
        //        } else {
        //            avgFund = (int) funds / actualSize;
        //        }
        //        log.info("stock size: {}, funds: {}, avgFund: {}", actualSize, funds, avgFund);

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
            for (String stock : canTradeStocks) {
                int hasBoughtOrder = MapUtils.getInteger(hasBoughtOrderMap, stock, NOT_EXIST);
                if (hasBoughtOrder == EXIST) {
                    continue;
                }

                // 2.4.挂单数量除以2（包含call和put）等于限制数量后，剩下的全部失效
                if (buyOrderIdMap.size() / 2 == size) {
                    invalidTradeStock(stock);
                    continue;
                }

                // 2.3.当没有股票下单，且剩余股票数量小于限制数量时，重新计算avgFund。
                if (MapUtils.isEmpty(buyOrderIdMap) && canTradeStocks.size() - tempInvalidStocks.size() < size) {
                    size = canTradeStocks.size() - tempInvalidStocks.size();
                    avgFund = (int) funds / size;
                }

                long curTime = System.currentTimeMillis();

                // 2.1.没有iv的 和 有iv没有摆盘的 继续循环判断
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

                String callIkbr = rtForIkbrMap.get(callRt);
                String putIkbr = rtForIkbrMap.get(putRt);
                String callFutu = optionCodeMap.get(callRt);
                String putFutu = optionCodeMap.get(putRt);
                boolean hasNoDeep = checkHasNoDeep(callFutu, putFutu);
                if (hasNoDeep) {
                    //                    invalidTradeStock(stock);
                    if ((curTime / 1000) % 10 == 0) { // 每整十秒打印一次日志避免日志过多
                        log.info("there is no legal option quote. stock={}", stock);
                    }
                    continue;
                }

                double callCalcPrice = calTradePrice(stock, callRt, CALL_TYPE);
                double putCalcPrice = calTradePrice(stock, putRt, PUT_TYPE);

                double countDouble = avgFund / (callCalcPrice + putCalcPrice) / 100;
                // 2.2.判断摆盘数据即callTrade+putTrade是否大于avgFund，如果大于则过滤。
                if (countDouble < 1) {
                    invalidTradeStock(stock);
                    log.info("the price is greater than avgFund. callAndPut={}", callAndPut);
                    tempInvalidStocks.add(stock);
                    continue;
                }

                // 如果下单数量小数位小于等于0.5，取整要减一，如果小数位大于0.5，则不变。目的是为了后面如果改价避免成交数量超过限制。但是如果count=1则不减一
                String countStr = String.valueOf(countDouble);
                int dotIndex = countStr.indexOf(".");
                String dotStr = countStr.substring(dotIndex + 1, dotIndex + 2);
                double count = (int) countDouble;
                if (Integer.valueOf(dotStr) <= 5 && count > 1) {
                    count = (int) countDouble - 1;
                }

                if (count <= 0) {
                    invalidTradeStock(stock);
                    log.info("count is illegal. stock={}, count={}", stock, count);
                    tempInvalidStocks.add(stock);
                    continue;
                }

                // 买入下单也需要判断差价决定下单顺序并检查下单结果
                Double callTrade = futuQuote.getOptionTrade(callFutu);
                Double putTrade = futuQuote.getOptionTrade(putFutu);
                if (callTrade == null || putTrade == null) { // 如果报价数据不全则统一报价后再计算
                    log.warn("buy order can't get option trade. callTrade={}\tputTrade={}", callTrade, putTrade);
                    callTrade = 1d;
                    putTrade = 1d;
                }
                double callDiffRatio = (callCalcPrice - callTrade) / callTrade;
                double putDiffRatio = (putCalcPrice - putTrade) / putTrade;
                long buyCallOrderId, buyPutOrderId;
                // 先计算哪个偏离当前报价最多，最多的就先下单避免下单失败，如果失败了重新判断
                if (callDiffRatio > putDiffRatio) {
                    buyCallOrderId = tradeApi.placeNormalBuyOrder(callIkbr, count, callCalcPrice);
                    if (buyCallOrderId == -1) { // -1表示下单失败
                        log.info("retry buy call. code={}\ttradePrice={}\tcalcPrice={}", callIkbr, callTrade, callCalcPrice);
                        continue;
                    }

                    // 如果买入call下单成功之后，put下单又失败，则不断重试直到成功下单，反之一样处理
                    while (true) {
                        buyPutOrderId = tradeApi.placeNormalBuyOrder(putIkbr, count, putCalcPrice);
                        if (buyPutOrderId == -1) {
                            putCalcPrice = calTradePrice(stock, putRt, PUT_TYPE);
                            log.info("after buy call, retry buy put. code={}\ttradePrice={}\tcalcPrice=", putIkbr, putTrade, putCalcPrice);
                        } else {
                            break;
                        }
                    }
                } else {
                    buyPutOrderId = tradeApi.placeNormalBuyOrder(putIkbr, count, putCalcPrice);
                    if (buyPutOrderId == -1) {
                        log.info("retry buy put. code={}\ttradePrice={}\tcalcPrice", putIkbr, putTrade, putCalcPrice);
                        continue;
                    }

                    while (true) {
                        buyCallOrderId = tradeApi.placeNormalBuyOrder(callIkbr, count, callCalcPrice);
                        if (buyCallOrderId == -1) {
                            callCalcPrice = calTradePrice(stock, callRt, CALL_TYPE);
                            log.info("after buy put, retry buy call. code={}\ttradePrice={}\tcalcPrice=", callIkbr, callTrade, callCalcPrice);
                        } else {
                            break;
                        }
                    }
                }
                //                buyCallOrderId = tradeApi.placeNormalBuyOrder(callIkbr, count, callCalcPrice);
                //                buyPutOrderId = tradeApi.placeNormalBuyOrder(putIkbr, count, putCalcPrice);
                futuQuote.addUserSecurity(putFutu);
                futuQuote.addUserSecurity(callFutu);
                futuQuote.addUserSecurity(stock);
                log.info("begin trade: buyCallOrder={}\tcall={}\tcallPrice={}\tbuyPutOrder={}\tput={}\tputPrice={}\tcount={}", buyCallOrderId, call, callCalcPrice, buyPutOrderId, put, putCalcPrice, count);

                lastBuyPriceMap.put(callIkbr, callCalcPrice);
                lastBuyPriceMap.put(putIkbr, putCalcPrice);
                orderCountMap.put(stock, count);
                buyOrderTimeMap.put(stock, curTime);
                buyOrderIdMap.put(callIkbr, buyCallOrderId);
                buyOrderIdMap.put(putIkbr, buyPutOrderId);
                adjustBuyInitPriceMap.put(callFutu, callCalcPrice);
                adjustBuyInitPriceMap.put(putFutu, putCalcPrice);
                optionAdjustPriceTimesMap.put(callFutu, 0);
                optionAdjustPriceTimesMap.put(putFutu, 0);
                optionAdjustPriceTimestampMap.put(callFutu, curTime);
                optionAdjustPriceTimestampMap.put(putFutu, curTime);
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

    public void getOrder() {
        List<Long> orderIds = Lists.newArrayList();
        buyOrderIdMap.values().forEach(id -> orderIds.add(id));
        sellOrderIdMap.values().forEach(id -> orderIds.add(id));
        try {
            //            tradeApi.getOrderList(orderIds);
        } catch (Exception e) {
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
        //        log.info("calculate predicate price. stock={}\toptionCode={}\tprice={}\tStrikePrice={}\tiv={}\tpredPrice={}", stock, option, stockPrice, strikePrice, iv, predPrice);

        String futu = optionCodeMap.get(ivRt);
        double bidPrice = codeToBidMap.get(futu);
        double askPrice = codeToAskMap.get(futu);
        double midPrice = BigDecimal.valueOf((bidPrice + askPrice) / 2).setScale(2, RoundingMode.UP).doubleValue();
        //        log.info("monitor option quote detail: optionCode={}\toptionBid={}\toptionAsk={}\toptionMid={}", futu, bidPrice, askPrice, midPrice);

        double tradePrice;
        if (predPrice <= bidPrice || predPrice > midPrice) {
            tradePrice = midPrice;
        } else {
            tradePrice = predPrice;
        }
        log.info("calculate trade price. option={}\tstockPrice={}\tstrikePrice={}\tiv={}\tpredPrice={}\tbid={}\task={}\tmid={}\ttradePrice={}",
          option, stockPrice, strikePrice, iv, predPrice, bidPrice, askPrice, midPrice, tradePrice);
        return tradePrice;
    }

    /**
     * 为了快速买进，需要按照最新的卖一价-最初的挂单价，差价除以10，然后乘以当前提高的次数（总共10次）得到每次改单需要提高的价格，每五秒用最初的挂单价加上一次提高的价格，尽快买进
     * 比如：
     * 1、当前买一1.3，卖一1.8，挂单价（根据iv计算）=1.5，初次挂单不提价
     * 2、4秒后，当前买一1.5，卖一1.8，最初挂单价1.5，第一次提价，提价幅度=(1.8-1.5)/10*1=0.03≈0.03（三位小数四舍五入），实际挂单价=1.5+0.03=1.53
     * 3、4秒后，当前买一1.53，卖一1.8，最初挂单价1.5，第二次提价，提价幅度=(1.8-1.5)/10*2=0.06，实际挂单价=1.5+0.04=1.56
     * 4、4秒后，当前买一1.6，卖一1.9（买一价大于挂单价，不影响），最初挂单价1.5，第三次提价，提价幅度=(1.9-1.5)/10*3=0.12，实际挂单价=1.5+0.12=1.62
     * 以此类推，10个4秒也就是40秒以后，实际挂单价=卖一价，一定会买进
     */
    public double calQuickBuyPrice(String futu) {
        long current = System.currentTimeMillis();
        Long lastTimestamp = MapUtils.getLong(optionAdjustPriceTimestampMap, futu, null);
        if (lastTimestamp == null) {
            optionAdjustPriceTimestampMap.put(futu, current);
            return Double.MIN_VALUE;
        }
        boolean touchOffAdjust = current - lastTimestamp > ADJUST_BUY_PRICE_TIME_INTERVAL;
        if (!touchOffAdjust) {
            return Double.MIN_VALUE;
        }

        Double bidPrice = codeToBidMap.get(futu);
        Double askPrice = codeToAskMap.get(futu);
        if (bidPrice == null || askPrice == null) {
            return Double.MIN_VALUE;
        }
        Double buyInitPrice = adjustBuyInitPriceMap.get(futu);
        BigDecimal buyInitPriceDecimal = BigDecimal.valueOf(buyInitPrice);

        // 记录调价次数，并计算每间隔5秒一次调价幅度，同时更新最新调价时间和调价次数
        Integer lastAdjustTimes = MapUtils.getInteger(optionAdjustPriceTimesMap, futu, 0);
        int adjustTimes = lastAdjustTimes + 1;
        optionAdjustPriceTimesMap.put(futu, adjustTimes);

        BigDecimal adjustPriceDecimal = BigDecimal.valueOf((askPrice - buyInitPrice) / ADJUST_TRADE_PRICE_TIMES * adjustTimes).setScale(3, RoundingMode.HALF_UP);
        double adjustPrice = buyInitPriceDecimal.add(adjustPriceDecimal).setScale(2, RoundingMode.HALF_UP).doubleValue();
        optionAdjustPriceTimestampMap.put(futu, current);

        log.info("adjust buy price: option={}\tbidPrice={}\taskPrice={}\tadjustTimes={}\tadjustPrice={}", futu, bidPrice, askPrice, adjustTimes, adjustPrice);
        return adjustPrice;
    }

    /**
     * 为了快速卖出，需要按照最初的挂单价-最新的买一价，差价除以10，然后乘以当前降低的次数（总共10次）得到每次改单需要降低的价格，每五秒用最初的挂单价减去一次降低的价格，尽快卖出
     * 比如：
     * 1、当前买一1.3，卖一1.8，挂单价（根据iv计算）=1.5，初次挂单不提价
     * 2、4秒后，当前买一1.3，卖一1.5，最初挂单价1.5，第一次降价，降价幅度=(1.5-1.3)/10*1=0.02≈0.02（三位小数四舍五入），实际挂单价=1.5-0.02=1.48
     * 3、4秒后，当前买一1.3，卖一1.48，最初挂单价1.5，第二次降价，降价幅度=(1.5-1.3)/10*2=0.04，实际挂单价=1.5-0.04=1.46
     * 4、4秒后，当前买一1.2，卖一1.42（卖一价小于挂单价，不影响），最初挂单价1.5，第三次降价，降价幅度=(1.5-1.2)/10*3=0.09，实际挂单价=1.5+0.09=1.41
     * 以此类推，10个4秒也就是40秒以后，实际挂单价=卖一价，一定会买进
     */
    public double calQuickSellPrice(String futu) {
        long current = System.currentTimeMillis();
        Long lastTimestamp = MapUtils.getLong(optionAdjustPriceTimestampMap, futu, null);
        if (lastTimestamp == null) {
            optionAdjustPriceTimestampMap.put(futu, current);
            return Double.MAX_VALUE;
        }
        boolean touchOffAdjust = current - lastTimestamp > ADJUST_SELL_PRICE_TIME_INTERVAL;
        if (!touchOffAdjust) {
            return Double.MAX_VALUE;
        }

        Double bidPrice = codeToBidMap.get(futu);
        Double askPrice = codeToAskMap.get(futu);
        if (bidPrice == null || askPrice == null) {
            return Double.MAX_VALUE;
        }
        bidPrice = askPrice > bidPrice ? bidPrice : askPrice; // 兼容错误数据，选最小的作为买一
        Double sellInitPrice = adjustSellInitPriceMap.get(futu);
        BigDecimal sellInitPriceDecimal = BigDecimal.valueOf(sellInitPrice);

        // 记录调价次数，并计算每间隔5秒一次调价幅度，同时更新最新调价时间和调价次数
        Integer lastAdjustTimes = MapUtils.getInteger(optionAdjustPriceTimesMap, futu, 0);
        int adjustTimes = lastAdjustTimes + 1;
        optionAdjustPriceTimesMap.put(futu, adjustTimes);

        BigDecimal adjustPriceDecimal = BigDecimal.valueOf((sellInitPrice - bidPrice) / ADJUST_TRADE_PRICE_TIMES * adjustTimes).setScale(3, RoundingMode.HALF_UP);
        double adjustPrice = sellInitPriceDecimal.subtract(adjustPriceDecimal).setScale(2, RoundingMode.HALF_UP).doubleValue();
        optionAdjustPriceTimestampMap.put(futu, current);

        log.info("adjust sell price: option={}\tbidPrice={}\taskPrice={}\tadjustTimes={}\tadjustPrice={}", futu, bidPrice, askPrice, adjustTimes, adjustPrice);
        return adjustPrice;
    }

    public void getFutuRealTimeIV() {
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
                            log.info("get futu iv is empty. call={}\tcallIvTime={}, put={}\tputIvTime={}", callFutu, callIvTime, putFutu, putIvTime);
                            continue;
                        }
                        String[] callSplit = callIvTime.split("\\|");
                        String[] putSplit = putIvTime.split("\\|");

                        long callTime = LocalDateTime.parse(callSplit[1].substring(0, pattern.length()), formatter).toInstant(ZoneOffset.of("-4")).toEpochMilli();
                        long putTime = LocalDateTime.parse(putSplit[1].substring(0, pattern.length()), formatter).toInstant(ZoneOffset.of("-4")).toEpochMilli();
                        if (callTime < openTime || putTime < openTime) {
                            log.info("iv time is illegal. call={} {}\tput={} {}", callFutu, callIvTime, putFutu, putIvTime);
                            continue;
                        }

                        double callIv = Double.parseDouble(callSplit[0]);
                        double putIv = Double.parseDouble(putSplit[0]);
                        optionRtIvMap.put(callRt, callIv);
                        optionRtIvMap.put(putRt, putIv);
                        log.info("rt iv data: call={} {}\tput={} {}", callFutu, callIvTime, putFutu, putIvTime);
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
                String callIkbr = rtForIkbrMap.get(callRt);
                String putIkbr = rtForIkbrMap.get(putRt);
                String callFutu = optionCodeMap.get(callRt);
                String putFutu = optionCodeMap.get(putRt);

                Long buyCallOrderId = buyOrderIdMap.get(callIkbr);
                Long buyPutOrderId = buyOrderIdMap.get(putIkbr);
                Order buyCallOrder = tradeApi.getOrder(buyCallOrderId);
                Order buyPutOrder = tradeApi.getOrder(buyPutOrderId);

                boolean callSuccess = buyCallOrder != null && buyCallOrder.getOrderStatus() == TrdCommon.OrderStatus.OrderStatus_Filled_All_VALUE;
                boolean putSuccess = buyPutOrder != null && buyPutOrder.getOrderStatus() == TrdCommon.OrderStatus.OrderStatus_Filled_All_VALUE;
                if (callSuccess && putSuccess) {
                    ReadWriteOptionTradeInfo.writeHasBoughtSuccess(stock);
                    //                    tradeApi.setPositionAvgCost(callRt.replaceAll("\\+", ""), buyCallOrder.getAvgPrice());
                    //                    tradeApi.setPositionAvgCost(putRt.replaceAll("\\+", ""), buyPutOrder.getAvgPrice());
                    hasBoughtSuccess.add(stock);
                    log.info("{} buy trade success. call={}\torderId={}\tput={}\torderId={}\tcount={}", stock, callIkbr, buyCallOrderId, putIkbr, buyPutOrderId, count);
                    delayUnsubscribeIv(stock);
                    //                    unmonitorPolygonQuote(stock);
                } else if (System.currentTimeMillis() - buyOrderTimeMap.get(stock) > ORDER_INTERVAL_TIME_MILLI) {
                    /**
                     * 改单只有在计算价比挂单价高的时候才进行，如果改低价会导致买入成交更困难
                     */
                    if (!callSuccess && buyCallOrder.getOrderStatus() == TrdCommon.OrderStatus.OrderStatus_Submitted_VALUE) {
                        double hasTradeCount = 0;
                        if (buyCallOrder != null) {
                            hasTradeCount = buyCallOrder.getTradeCount();
                        }
                        double tradePrice;
                        if (putSuccess) { // 如果put已经成交，则需要尽快成交call
                            tradePrice = calQuickBuyPrice(callFutu);
                        } else {
                            tradePrice = calTradePrice(stock, callRt, CALL_TYPE);
                        }

                        if (!lastBuyPriceMap.containsKey(callIkbr) || lastBuyPriceMap.get(callIkbr).compareTo(tradePrice) < 0) {
                            long modifyOrderId = tradeApi.upOrderPrice(buyCallOrderId, count, tradePrice);
                            lastBuyPriceMap.put(callIkbr, tradePrice);
                            log.info("modify buy call order: orderId={}\tcall={}\ttradePrice={}\tcount={}\thasTradeCount={}", modifyOrderId, callIkbr, tradePrice, count, hasTradeCount);
                        }
                    }
                    if (!putSuccess && buyPutOrder.getOrderStatus() == TrdCommon.OrderStatus.OrderStatus_Submitted_VALUE) {
                        double hasTradeCount = 0;
                        if (buyPutOrder != null) {
                            hasTradeCount = buyPutOrder.getTradeCount();
                        }
                        double tradePrice;
                        if (callSuccess) { // 如果call已经成交，则需要尽快成交put
                            tradePrice = calQuickBuyPrice(putFutu);
                        } else {
                            tradePrice = calTradePrice(stock, putRt, PUT_TYPE);
                        }

                        if (!lastBuyPriceMap.containsKey(putIkbr) || lastBuyPriceMap.get(putIkbr).compareTo(tradePrice) < 0) {
                            long modifyOrderId = tradeApi.upOrderPrice(buyPutOrderId, count, tradePrice);
                            lastBuyPriceMap.put(putIkbr, tradePrice);
                            log.info("modify buy put order: orderId={}\tput={}\ttradePrice={}\tcount={}\thasTradeCount={}", modifyOrderId, putIkbr, tradePrice, count, hasTradeCount);
                        }
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
                RealTimeDataWS_DB2.getRealtimeQuoteForOption = false;
                return;
            }
        }
    }

    public void monitorSellOrder() {
        log.info("monitor sell order");
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
                String callFutu = split[0];
                String putFutu = split[1];
                String callIkbr = futuForIkbrMap.get(callFutu);
                String putIkbr = futuForIkbrMap.get(putFutu);

                Long sellCallOrderId = sellOrderIdMap.get(callIkbr);
                Long sellPutOrderId = sellOrderIdMap.get(putIkbr);
                Order sellCallOrder = tradeApi.getOrder(sellCallOrderId);
                Order sellPutOrder = tradeApi.getOrder(sellPutOrderId);

                boolean callSuccess = sellCallOrder != null && sellCallOrder.getOrderStatus() == TrdCommon.OrderStatus.OrderStatus_Filled_All_VALUE;
                boolean putSuccess = sellPutOrder != null && sellPutOrder.getOrderStatus() == TrdCommon.OrderStatus.OrderStatus_Filled_All_VALUE;
                if (callSuccess && putSuccess) {
                    ReadWriteOptionTradeInfo.writeHasSoldSuccess(stock);
                    hasSoldSuccess.add(stock);
                    delayUnsubscribeQuote(stock);
                    //                    unmonitorPolygonQuote(stock);
                    log.info("{} sell trade success. call={}\torderId={}\tput={}\torderId={}", stock, callFutu, sellCallOrderId, putFutu, sellPutOrderId);
                } else if (System.currentTimeMillis() - sellOrderTimeMap.get(stock) > ORDER_INTERVAL_TIME_MILLI) {
                    /**
                     * 改单只有在计算价比挂单价低的时候才进行，如果改高价会导致卖出成交更困难
                     */
                    if (!callSuccess && sellCallOrder.getOrderStatus() == TrdCommon.OrderStatus.OrderStatus_Submitted_VALUE) {
                        double hasTradeCount = 0;
                        if (sellCallOrder != null) {
                            hasTradeCount = sellCallOrder.getTradeCount();
                        }

                        double tradePrice = calQuickSellPrice(callFutu);
                        if (!lastSellPriceMap.containsKey(callIkbr) || lastSellPriceMap.get(callIkbr).compareTo(tradePrice) > 0) {
                            long modifyOrderId = tradeApi.upOrderPrice(sellCallOrderId, count, tradePrice);
                            log.info("modify sell call order: orderId={}\tcall={}\ttradePrice={}\tcount={}\thasTradeCount={}", modifyOrderId, callFutu, tradePrice, count, hasTradeCount);
                            lastSellPriceMap.put(callIkbr, tradePrice);
                        }
                    }
                    if (!putSuccess && sellPutOrder.getOrderStatus() == TrdCommon.OrderStatus.OrderStatus_Submitted_VALUE) {
                        double hasTradeCount = 0;
                        if (sellPutOrder != null) {
                            hasTradeCount = sellPutOrder.getTradeCount();
                        }

                        double tradePrice = calQuickSellPrice(putFutu);
                        if (!lastSellPriceMap.containsKey(putIkbr) || lastSellPriceMap.get(putIkbr).compareTo(tradePrice) > 0) {
                            long modifyOrderId = tradeApi.upOrderPrice(sellPutOrderId, count, tradePrice);
                            log.info("modify sell put order: orderId={}\tput={}\ttradePrice={}\tcount={}\thasTradeCount={}", modifyOrderId, putFutu, tradePrice, count, hasTradeCount);
                            lastSellPriceMap.put(putIkbr, tradePrice);
                        }
                    }
                    long curTime = System.currentTimeMillis();
                    sellOrderTimeMap.put(stock, curTime);
                    ReadWriteOptionTradeInfo.writeSellOrderTime(stock, curTime);
                }
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
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {

            int showtimes = 20, showCount = 0;
            Map<String/* code */, StockPosition> positions = Maps.newHashMap();

            //            private StockPosition getPosistion(String optionCode) {
            //                Map<String, StockPosition> positionMap = tradeApi.getPositionMap(optionCode);
            //                StockPosition stockPosition = positionMap.get(optionCode);
            //                if (stockPosition != null) {
            //                    positions.put(optionCode, stockPosition);
            //                }
            //
            //                return positions.get(optionCode);
            //            }

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

                        Integer gainSellStatus = MapUtils.getInteger(gainSellStatusMap, stock, NO_GAIN_SELL);
                        String callAndPut = canTradeOptionForFutuMap.get(stock);
                        String[] split = callAndPut.split("\\|");
                        String callFutu = split[0];
                        String putFutu = split[1];
                        String callIkbr = futuForIkbrMap.get(callFutu);
                        String putIkbr = futuForIkbrMap.get(putFutu);

                        Double callBidPrice = codeToBidMap.get(callFutu);
                        Double callAskPrice = codeToAskMap.get(callFutu);
                        Double putBidPrice = codeToBidMap.get(putFutu);
                        Double putAskPrice = codeToAskMap.get(putFutu);
                        if (callBidPrice == null || callAskPrice == null || putBidPrice == null || putAskPrice == null) {
                            continue;
                        }

                        // 如果卖一价小于之前的挂单价，则mid一定小于之前的挂单价，后续可以直接使用
                        double callMidPrice = BigDecimal.valueOf((callBidPrice + callAskPrice) / 2).setScale(2, RoundingMode.DOWN).doubleValue();
                        double putMidPrice = BigDecimal.valueOf((putBidPrice + putAskPrice) / 2).setScale(2, RoundingMode.DOWN).doubleValue();
                        /**
                         * 只针对止盈卖出
                         * 真实交易时，卖出挂单会出现在报价列表里，如果后续卖一一直都是这个报价，则不需要重新计算中间价等待成交就好（中间价=卖一价）。
                         * 只有低于卖一的新报价出现时，才需要重新计算中间价用于后面的逻辑计算。理论上高于卖一的报价会排在后面，不影响成交
                         * ps: 模拟交易不执行这段逻辑，仍然按照实际卖一计算中间价
                         */
                        if (gainSellStatus == GAIN_SELLING) {
                            if (lastSellPriceMap.containsKey(callIkbr)) {
                                Double tradePrice = lastSellPriceMap.get(callIkbr);
                                // 暂时不考虑卖一数量小于5的价格，所以注释掉
                                //                                try {
                                //                                    callAskPrice = Double.parseDouble(allCodeToQuoteMap.get(callFutu).split("\\|")[1]);
                                //                                } catch (Exception e) {
                                //                                    log.error("gain selling get call ask price error. call={}\tquote={}", callIkbr, allCodeToQuoteMap.get(callFutu), e);
                                //                                }
                                if (tradePrice.compareTo(callAskPrice) == 0) { // 如果最新卖一价和之前的挂单价一样，则mid就等于卖一价，也就是挂单价不变，下同
                                    callMidPrice = callAskPrice;
                                } else {
                                    log.info("change call mid price. call={}\tlastPrice={}\tmidPrice={}", callIkbr, tradePrice, callMidPrice);
                                }
                            }
                            if (lastSellPriceMap.containsKey(putIkbr)) {
                                Double tradePrice = lastSellPriceMap.get(putIkbr);
                                //                                try {
                                //                                    putAskPrice = Double.parseDouble(allCodeToQuoteMap.get(putFutu).split("\\|")[1]);
                                //                                } catch (Exception e) {
                                //                                    log.error("gain selling get put ask price error. quote={}", allCodeToQuoteMap.get(putFutu), e);
                                //                                }
                                if (tradePrice.compareTo(putAskPrice) == 0) {
                                    putMidPrice = putAskPrice;
                                } else {
                                    log.info("change put mid price. put={}\tlastPrice={}\tmidPrice={}", putIkbr, tradePrice, putMidPrice);
                                }
                            }
                        }

                        /** 根据已成交的订单查看买入价，不断以最新报价计算是否触发止损价（不能以当前市价止损，因为流动性不够偏差会很大）*/
                        //                        StockPosition callPosition = getPosistion(callIkbr);
                        //                        StockPosition putPosition = getPosistion(putIkbr);
                        Long buyCallOrderId = buyOrderIdMap.get(callIkbr);
                        Long buyPutOrderId = buyOrderIdMap.get(putIkbr);
                        Order buyCallOrder = tradeApi.getOrder(buyCallOrderId);
                        Order buyPutOrder = tradeApi.getOrder(buyPutOrderId);
                        double callOpen = buyCallOrder.getAvgPrice();
                        double putOpen = buyPutOrder.getAvgPrice();
                        double callDiff = callMidPrice - callOpen;
                        double putDiff = putMidPrice - putOpen;
                        double allDiff = BigDecimal.valueOf(callDiff + putDiff).setScale(2, RoundingMode.HALF_UP).doubleValue();
                        double diffRatio = BigDecimal.valueOf(allDiff / (callOpen + putOpen)).setScale(4, RoundingMode.HALF_UP).doubleValue();
                        double callCount = buyCallOrder.getTradeCount();
                        double putCount = buyPutOrder.getTradeCount();
                        if (showCount == showtimes) {
                            log.info("monitor loss and gain. call={}\tcallOpen={}\tcallBid={}\tcallAsk={}\tcallMid={}\tput={}\tputOpen={}\tputBid={}\tputAsk={}\tputMid={}\tcallDiff={}\tputDiff={}\tallDiff={}\tdiffRatio={}",
                              callFutu, callOpen, callBidPrice, callAskPrice, callMidPrice, putFutu, putOpen, putBidPrice, putAskPrice, putMidPrice, callDiff, putDiff, allDiff, diffRatio);
                        }

                        long curTime = System.currentTimeMillis();
                        if (gainSellStatus == NO_GAIN_SELL) { // 无上涨卖出 继续执行止盈或止损或收盘卖出判断
                            if (diffRatio < STOP_LOSS_RATIO || currentTime > closeTime) { // 止损+收盘都同时卖出，尽快止损和收盘卖出
                                Double callTrade = futuQuote.getOptionTrade(callFutu);
                                Double putTrade = futuQuote.getOptionTrade(putFutu);
                                if (callTrade == null || putTrade == null) {
                                    log.error("can't get option trade. callTrade={}\tputTrade={}", callTrade, putTrade);
                                    continue;
                                }

                                long sellCallOrderId, sellPutOrderId;
                                double callDiffRatio = (callTrade - callMidPrice) / callTrade;
                                double putDiffRatio = (putTrade - putMidPrice) / putTrade;
                                // 先计算哪个偏离当前报价最多，最多的就先下单避免下单失败，如果失败了重新判断
                                if (callDiffRatio > putDiffRatio) {
                                    sellCallOrderId = tradeApi.placeNormalSellOrder(callIkbr, callCount, callMidPrice);
                                    if (sellCallOrderId == -1) { // -1表示下单失败
                                        log.info("retry sell call. stoploss. gainSellStatus={}\tcode={}", gainSellStatus, callIkbr);
                                        continue;
                                    }

                                    // 如果卖出call下单成功之后，put下单又失败，则不断重试直到成功下单，反之一样处理
                                    while (true) {
                                        sellPutOrderId = tradeApi.placeNormalSellOrder(putIkbr, putCount, putMidPrice);
                                        if (sellPutOrderId == -1) {
                                            putMidPrice = calculateMidPrice(putFutu);
                                            log.info("after sell call, retry sell put. stoploss. gainSellStatus={}\tcode={}\tprice=", gainSellStatus, putIkbr, putMidPrice);
                                        } else {
                                            break;
                                        }
                                    }
                                } else {
                                    sellPutOrderId = tradeApi.placeNormalSellOrder(putIkbr, putCount, putMidPrice);
                                    if (sellPutOrderId == -1) {
                                        log.info("retry sell put. stoploss. gainSellStatus={}\tcode={}", gainSellStatus, putIkbr);
                                        continue;
                                    }

                                    while (true) {
                                        sellCallOrderId = tradeApi.placeNormalSellOrder(callIkbr, callCount, callMidPrice);
                                        if (sellCallOrderId == -1) {
                                            callMidPrice = calculateMidPrice(callFutu);
                                            log.info("after sell put, retry sell call. stoploss. gainSellStatus={}\tcode={}\tprice=", gainSellStatus, callIkbr, callMidPrice);
                                        } else {
                                            break;
                                        }
                                    }
                                }

                                //                                long sellCallOrderId = tradeApi.placeNormalSellOrder(callIkbr, callCount, callMidPrice);
                                //                                long sellPutOrderId = tradeApi.placeNormalSellOrder(putIkbr, putCount, putMidPrice);

                                adjustSellInitPriceMap.put(callFutu, callMidPrice);
                                adjustSellInitPriceMap.put(putFutu, putMidPrice);
                                lastSellPriceMap.put(callIkbr, callMidPrice);
                                lastSellPriceMap.put(putIkbr, putMidPrice);
                                sellOrderTimeMap.put(stock, curTime);
                                sellOrderIdMap.put(callIkbr, sellCallOrderId);
                                sellOrderIdMap.put(putIkbr, sellPutOrderId);
                                optionAdjustPriceTimestampMap.put(callFutu, curTime);
                                optionAdjustPriceTimestampMap.put(putFutu, curTime);
                                optionAdjustPriceTimesMap.put(callFutu, 0);
                                optionAdjustPriceTimesMap.put(putFutu, 0);
                                ReadWriteOptionTradeInfo.writeSellOrderTime(stock, curTime);
                                ReadWriteOptionTradeInfo.writeSellOrderId(callIkbr, sellCallOrderId);
                                ReadWriteOptionTradeInfo.writeSellOrderId(putIkbr, sellPutOrderId);
                                ReadWriteOptionTradeInfo.writeHasSoldOrder(stock);
                                hasSoldOrderMap.put(stock, EXIST);

                                log.info("stop loss and gain order. call={}\tcallOpen={}\tcallBid={}\tcallAsk={}\tcallMid={}\tput={}\tputOpen={}\tputBid={}\tputAsk={}\tputMid={}\tcallDiff={}\tputDiff={}\tallDiff={}\tdiffRatio={}\tsellCallOrder={}\tsellPutOrder={}",
                                  callFutu, callOpen, callBidPrice, callAskPrice, callMidPrice, putFutu, putOpen, putBidPrice, putAskPrice, putMidPrice, callDiff, putDiff, allDiff, diffRatio, sellCallOrderId, sellPutOrderId);
                            } else if ((currentTime < stopGainTime && diffRatio > STOP_GAIN_RATIO_1) || (currentTime > stopGainTime && diffRatio > STOP_GAIN_RATIO_2)) { // 止盈先卖出上涨的期权，卖完后再卖下跌的期权，尽量保证盈利不减少
                                // 如果止盈卖出下单失败，则重新判断重新下单
                                if (callDiff > 0) {
                                    long sellCallOrderId = tradeApi.placeNormalSellOrder(callIkbr, callCount, callMidPrice);
                                    if (sellCallOrderId == -1) {
                                        log.info("retry sell call. stopgain. gainSellStatus={}\tcode={}", gainSellStatus, callIkbr);
                                        continue;
                                    }
                                    sellOrderTimeMap.put(stock, curTime);
                                    sellOrderIdMap.put(callIkbr, sellCallOrderId);
                                    ReadWriteOptionTradeInfo.writeSellOrderTime(stock, curTime);
                                    ReadWriteOptionTradeInfo.writeSellOrderId(callIkbr, sellCallOrderId);
                                    lastSellPriceMap.put(callIkbr, callMidPrice);
                                    gainSellStatusMap.put(stock, GAIN_SELLING);
                                    gainSellOption.add(callIkbr);
                                    log.info("gain sell call order: orderId={}\tcall={}\ttradePrice={}\tcount={}", sellCallOrderId, callFutu, callMidPrice, callCount);
                                } else if (putDiff > 0) {
                                    long sellPutOrderId = tradeApi.placeNormalSellOrder(putIkbr, putCount, putMidPrice);
                                    if (sellPutOrderId == -1) {
                                        log.info("retry sell put. stopgain. gainSellStatus={}\tcode={}", gainSellStatus, putIkbr);
                                        continue;
                                    }
                                    sellOrderTimeMap.put(stock, curTime);
                                    sellOrderIdMap.put(putIkbr, sellPutOrderId);
                                    ReadWriteOptionTradeInfo.writeSellOrderTime(stock, curTime);
                                    ReadWriteOptionTradeInfo.writeSellOrderId(putIkbr, sellPutOrderId);
                                    lastSellPriceMap.put(putIkbr, putMidPrice);
                                    gainSellStatusMap.put(stock, GAIN_SELLING);
                                    gainSellOption.add(putIkbr);
                                    log.info("gain sell put order: orderId={}\tcall={}\ttradePrice={}\tcount={}", sellPutOrderId, putFutu, putMidPrice, putCount);
                                }
                            }
                        } else if (gainSellStatus == GAIN_SELLING) { // 上涨卖出中
                            if (gainSellOption.contains(callIkbr)) {
                                Long sellCallOrderId = sellOrderIdMap.get(callIkbr);
                                Order sellCallOrder = tradeApi.getOrder(sellCallOrderId);
                                boolean callSuccess = sellCallOrder != null && sellCallOrder.getOrderStatus() == TrdCommon.OrderStatus.OrderStatus_Filled_All_VALUE;
                                if (callSuccess) { // 全部成交 写入上涨卖出完成状态
                                    gainSellStatusMap.put(stock, GAIN_SOLD);
                                } else { // 未全部成交。如果都未成交，则改单或直接撤单（改单失败则重新下单）。如果部分成交，则要根据成交数量，改单或撤单（改单失败重新下单要用剩余未成交数量）todo 记录订单数量
                                    if ((currentTime < stopGainTime && diffRatio > STOP_GAIN_RATIO_1) || (currentTime > stopGainTime && diffRatio > STOP_GAIN_RATIO_2)) { // 检查报价高于止盈，改单
                                        double hasTradeCount = 0;
                                        if (sellCallOrder != null) {
                                            hasTradeCount = sellCallOrder.getTradeCount();
                                        }
                                        // 修改订单失败，则重置状态，todo 回去查看一下部分成交的状态
                                        if (!lastSellPriceMap.containsKey(callIkbr) || lastSellPriceMap.get(callIkbr).compareTo(callMidPrice) > 0) {
                                            long modifyOrderId = tradeApi.upOrderPrice(sellCallOrderId, callCount, callMidPrice); // todo 这里修改挂单价可能会失败，怎么处理？下同
                                            lastSellPriceMap.put(callIkbr, callMidPrice);
                                            log.info("modify gain sell call order: orderId={}\tcall={}\ttradePrice={}\tcount={}\thasTradeCount={}", modifyOrderId, callIkbr, callMidPrice, callCount, hasTradeCount);
                                        }
                                    } else { // 检查报价低于止盈，撤单，清除上涨卖出中状态，清除上涨卖出的期权
                                        long cancelResp = tradeApi.cancelOrder(sellCallOrderId);
                                        if (cancelResp != TRADE_PROHIBT_CODE && cancelResp != TRADE_ERROR_CODE) {
                                            gainSellStatusMap.put(stock, NO_GAIN_SELL);
                                            gainSellOption.remove(callIkbr);
                                            log.info("gain selling stop success. stock={}, option={}", stock, callIkbr);
                                        } else { // 撤单失败，记录日志（可能已经全部成交就无法撤单）
                                            log.info("gain selling stop failed. stock={}, option={}", stock, callIkbr);
                                        }
                                    }
                                }
                            } else if (gainSellOption.contains(putIkbr)) {
                                Long sellPutOrderId = sellOrderIdMap.get(putIkbr);
                                Order sellPutOrder = tradeApi.getOrder(sellPutOrderId);
                                boolean putSuccess = sellPutOrder != null && sellPutOrder.getOrderStatus() == TrdCommon.OrderStatus.OrderStatus_Filled_All_VALUE;
                                if (putSuccess) {
                                    gainSellStatusMap.put(stock, GAIN_SOLD);
                                } else {
                                    if ((currentTime < stopGainTime && diffRatio > STOP_GAIN_RATIO_1) || (currentTime > stopGainTime && diffRatio > STOP_GAIN_RATIO_2)) {
                                        double hasTradeCount = 0;
                                        if (sellPutOrder != null) {
                                            hasTradeCount = sellPutOrder.getTradeCount();
                                        }
                                        if (!lastSellPriceMap.containsKey(putIkbr) || lastSellPriceMap.get(putIkbr).compareTo(putMidPrice) > 0) {
                                            long modifyOrderId = tradeApi.upOrderPrice(sellPutOrderId, putCount, putMidPrice);
                                            lastSellPriceMap.put(putIkbr, putMidPrice);
                                            log.info("modify gain sell put order: orderId={}\tput={}\ttradePrice={}\tcount={}\thasTradeCount={}", modifyOrderId, putIkbr, putMidPrice, putCount, hasTradeCount);
                                        }
                                    } else {
                                        long cancelResp = tradeApi.cancelOrder(sellPutOrderId);
                                        if (cancelResp != TRADE_PROHIBT_CODE && cancelResp != TRADE_ERROR_CODE) {
                                            gainSellStatusMap.put(stock, NO_GAIN_SELL);
                                            gainSellOption.remove(putIkbr);
                                            log.info("gain selling stop success. stock={}, option={}", stock, putIkbr);
                                        } else {
                                            log.info("gain selling stop failed. stock={}, option={}", stock, putIkbr);
                                        }
                                    }
                                }
                            } else {
                                log.error("gain selling, but no gain sell option. stock={}", stock);
                            }
                        } else if (gainSellStatus == GAIN_SOLD) {
                            if (gainSellOption.contains(callIkbr)) {
                                long sellPutOrderId = tradeApi.placeNormalSellOrder(putIkbr, putCount, putMidPrice);
                                if (sellPutOrderId == -1) {
                                    log.info("retry sell put. gainSellStatus={}\tcode={}", gainSellStatus, putIkbr);
                                    continue;
                                }

                                adjustSellInitPriceMap.put(putFutu, putMidPrice);
                                optionAdjustPriceTimestampMap.put(putFutu, curTime);
                                optionAdjustPriceTimesMap.put(putFutu, 0);
                                lastSellPriceMap.put(putIkbr, putMidPrice);
                                sellOrderTimeMap.put(stock, curTime);
                                sellOrderIdMap.put(putIkbr, sellPutOrderId);
                                ReadWriteOptionTradeInfo.writeSellOrderTime(stock, curTime);
                                ReadWriteOptionTradeInfo.writeSellOrderId(putIkbr, sellPutOrderId);
                                ReadWriteOptionTradeInfo.writeHasSoldOrder(stock);
                                hasSoldOrderMap.put(stock, EXIST);
                            } else if (gainSellOption.contains(putIkbr)) {
                                long sellCallOrderId = tradeApi.placeNormalSellOrder(callIkbr, callCount, callMidPrice);
                                if (sellCallOrderId == -1) {
                                    log.info("retry sell put. gainSellStatus={}\tcode={}", gainSellStatus, putIkbr);
                                    continue;
                                }

                                adjustSellInitPriceMap.put(callFutu, callMidPrice);
                                optionAdjustPriceTimestampMap.put(callFutu, curTime);
                                optionAdjustPriceTimesMap.put(callFutu, 0);
                                lastSellPriceMap.put(callIkbr, callMidPrice);
                                sellOrderTimeMap.put(stock, curTime);
                                sellOrderIdMap.put(callIkbr, sellCallOrderId);
                                ReadWriteOptionTradeInfo.writeSellOrderTime(stock, curTime);
                                ReadWriteOptionTradeInfo.writeSellOrderId(callIkbr, sellCallOrderId);
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
        //        unmonitorPolygonQuote(stock);

        log.info("invalid stock: {}", stock);
        //        Set<String> hasBoughtOrderStocks = hasBoughtOrderMap.entrySet().stream().filter(e -> e.getValue().intValue() == EXIST).map(e -> e.getKey()).collect(Collectors.toSet());
        //        if (CollectionUtils.subtract(hasBoughtOrderStocks, invalidStocks).isEmpty()) {
        //            int size = canTradeStocks.size() - invalidStocks.size();
        //            if (size == 0) {
        //                avgFund = funds;
        //            } else {
        //                avgFund = (int) funds / size;
        //            }
        //            log.info("change avg fund. avgFund={}", avgFund);
        //        }

        delayUnsubscribeIv(stock);
        delayUnsubscribeQuote(stock);
    }

    public void monitorFutuDeep(String optionCode) {
        futuQuote.subOrderBook(optionCode);
        log.info("monitor futu option deep: {}", optionCode);
    }

    public void monitorPolygonDeep(String optionCode) {
        client.subscribeQuoteForOption(optionCode);
        log.info("monitor polygon option deep: {}", optionCode);
    }

    public void unmonitorPolygonQuote(String stock) {
        String callAndPut = MapUtils.getString(canTradeOptionMap, stock, "");
        if (callAndPut.contains("|")) {
            String call = callAndPut.split("\\|")[0];
            String put = callAndPut.split("\\|")[1];
            client.unsubscribeQuoteForOption(call);
            client.unsubscribeQuoteForOption(put);
            log.info("unmonitor polygon option quote: {}", callAndPut);
        }
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

    public void delayUnsubscribeIv() {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (CollectionUtils.isNotEmpty(invalidStocks)) {
                    for (String stock : invalidStocks) {
                        String callAndPut = MapUtils.getString(canTradeOptionForFutuMap, stock, "");
                        if (StringUtils.isBlank(callAndPut)) {
                            continue;
                        }
                        String[] split = callAndPut.split("\\|");
                        String call = split[0];
                        String put = split[1];
                        futuQuote.unSubBasicQuote(call);
                        futuQuote.unSubBasicQuote(put);
                    }
                }
                log.info("unsubscribe quote. invalid={}\tbuy success={}", invalidStocks, hasBoughtSuccess);
                timer.cancel();
            }
        }, 1000 * 60 * 1);
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

    public static void main(String[] args) throws InterruptedException {
        List<Integer> l1 = Lists.newArrayList(1, 2, 3, 4, 5);
        List<Integer> l2 = Lists.newArrayList(3, 4, 5);
        //        l1.retainAll(l2);
        System.out.println(l1);
        for (Iterator<Integer> iter = l1.iterator(); iter.hasNext(); ) {
            Integer next = iter.next();
            if (!l2.contains(next)) {
                iter.remove();
            }
        }
        System.out.println(l1);
    }
}
