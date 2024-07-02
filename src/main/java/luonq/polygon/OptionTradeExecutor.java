package luonq.polygon;

import bean.Node;
import bean.NodeList;
import bean.OptionRT;
import bean.OptionRTResp;
import bean.OrderFill;
import com.alibaba.fastjson.JSON;
import com.futu.openapi.FTAPI;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import luonq.execute.LoadOptionTradeData;
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

    private Set<String> hasBoughtOrder = Sets.newHashSet(); // 已下单买入
    private Set<String> hasSoldOrder = Sets.newHashSet(); // 已下单卖出
    private Set<String> hasBoughtSuccess = Sets.newHashSet(); // 已成交买入
    private Set<String> hasSoldSuccess = Sets.newHashSet(); // 已成交卖出
    private Map<String/* futu option */, Long/* orderId */> buyOrderMap = Maps.newHashMap(); // 下单买入的订单id
    private Map<String/* futu option */, Long/* orderId */> sellOrderMap = Maps.newHashMap(); // 下单卖出的订单id

    public ThreadPoolExecutor threadPool = new ThreadPoolExecutor(10, 10, 3600, TimeUnit.SECONDS, new LinkedBlockingQueue<>());


    // code的实时iv
    private Map<String, Double> optionRtIvMap = Maps.newHashMap();
    // 股票对应实时iv code
    private Map<String, String> canTradeOptionForRtIVMap;
    // 实时iv code对应富途code
    private Map<String, String> optionCodeMap;
    // 股票对应富途code
    private Map<String, String> canTradeOptionForFutuMap;

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
    }

    public void beginTrade() throws InterruptedException {
        String today = LocalDate.now().format(Constants.DB_DATE_FORMATTER);
        // 能交易的股票
        Set<String> canTradeStocks = optionStockListener.getCanTradeStocks();
        if (CollectionUtils.isEmpty(canTradeStocks)) {
            log.info("there is no stock can be traded");
            return;
        }
        log.info("there are stock can be traded. stock: {}", canTradeStocks);
        // 股票对应实时iv code
        canTradeOptionForRtIVMap = optionStockListener.getCanTradeOptionForRtIVMap();
        double riskFreeRate = LoadOptionTradeData.riskFreeRate;
        log.info("riskFreeRate: {}", riskFreeRate);
        // 股票的实时报价
        Map<String, Double> realtimeQuoteForOptionMap = RealTimeDataWS_DB.realtimeQuoteForOptionMap;
        //        realtimeQuoteForOptionMap.put("AAPL", 219.07); // todo 测试用要删
        // 期权对应行权价
        Map<String, Double> optionStrikePriceMap = optionStockListener.getOptionStrikePriceMap();
        // 期权对应行到期日
        Map<String, String> optionExpireDateMap = optionStockListener.getOptionExpireDateMap();
        // 实时iv code对应富途code
        optionCodeMap = optionStockListener.getOptionCodeMap();
        // 监听实时iv
        getRealTimeIV(canTradeOptionForRtIVMap); // todo 正式运行时要去掉注释

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
                Double callStrikePrice = optionStrikePriceMap.get(call);
                Double putStrikePrice = optionStrikePriceMap.get(put);
                String callExpireDate = optionExpireDateMap.get(call);
                String putExpireDate = optionExpireDateMap.get(put);
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
                    log.info("there is no legal option quote. stock={}", stock);
                    continue;
                }

                double callPredPrice = BaseUtils.getCallPredictedValue(stockPrice, callStrikePrice, riskFreeRate, callIv, today, callExpireDate);
                double putPredPrice = BaseUtils.getPutPredictedValue(stockPrice, putStrikePrice, riskFreeRate, putIv, today, putExpireDate);
                log.info("call predicate price. stock={}\tcallStrikePrice={}\tcallIv={}\tpredPrice={}", stock, callStrikePrice, callIv, callPredPrice);
                log.info("put predicate price. stock={}\tputStrikePrice={}\tputIv={}\tpredPrice={}", stock, putStrikePrice, putIv, putPredPrice);

                String[] callQuoteSplit = callQuote.split("\\|");
                String[] putQuoteSplit = putQuote.split("\\|");
                double callBidPrice = Double.parseDouble(callQuoteSplit[0]);
                double callAskPrice = Double.parseDouble(callQuoteSplit[1]);
                double callMidPrice = BigDecimal.valueOf((callBidPrice + callAskPrice) / 2).setScale(2, RoundingMode.UP).doubleValue();
                double putBidPrice = Double.parseDouble(putQuoteSplit[0]);
                double putAskPrice = Double.parseDouble(putQuoteSplit[1]);
                double putMidPrice = BigDecimal.valueOf((putBidPrice + putAskPrice) / 2).setScale(2, RoundingMode.UP).doubleValue();
                log.info("monitor option quote detail: call={}\tcallBid={}\tcallAsk={}\tcallMid={}\tput={}\tputBid={}\tputAsk={}\tputMid={}", callFutu, callBidPrice, callAskPrice, callMidPrice, putFutu, putBidPrice, putAskPrice, putMidPrice);

                double callTradePrice = 0d, putTradePrice = 0d; // todo 重点测试各种价格
                if (callPredPrice < callBidPrice || callPredPrice > callMidPrice) {
                    callTradePrice = callMidPrice;
                } else {
                    callTradePrice = callPredPrice;
                }
                if (putPredPrice < putBidPrice || putPredPrice > putMidPrice) {
                    putTradePrice = putMidPrice;
                } else {
                    putTradePrice = putPredPrice;
                }
                double count = (int) (avgFund / (callTradePrice + putTradePrice) / 100);

                long buyCallOrderId = tradeApi.placeNormalBuyOrder(callFutu, count, callTradePrice);
                long buyPutOrderId = tradeApi.placeNormalBuyOrder(putFutu, count, putTradePrice);
                log.info("simulate trade: buyCallOrder={}\tcall={}\tcallPrice={}\tbuyPutOrder={}\tput={}\tputPrice{}\tcount={}", buyCallOrderId, call, callTradePrice, buyPutOrderId, put, putTradePrice, count);

                hasBoughtOrder.add(stock);
                buyOrderMap.put(callFutu, buyCallOrderId);
                buyOrderMap.put(putFutu, buyPutOrderId);
            }
            if (canTradeStocks.size() == hasBoughtOrder.size()) {
                RealTimeDataWS_DB.getRealtimeQuoteForOption = false;
                break;
            }
        }

        Thread.sleep(5000);
    }

    public void getRealTimeIV(Map<String, String> canTradeOptionForRtIVMap) {
        if (MapUtils.isEmpty(canTradeOptionForRtIVMap)) {
            log.info("there is no canTradeOptionForRtIVMap");
            return;
        }

        HttpClient httpClient = new HttpClient();
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {

            @Override
            public void run() {
                String options = canTradeOptionForRtIVMap.values().stream().flatMap(o -> Arrays.stream(o.split("\\|"))).collect(Collectors.joining(","));
                String url = "https://restapi.ivolatility.com/equities/rt/options-rawiv?apiKey=5uO8Wid7AY945OJ2&symbols=" + options;

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
                            //                            String line = symbol + "\t" + timestamp + "\t" + iv + "\t" + curent;
                            //                            log.info("rt iv: {}", line);
                            if (timestamp.contains("T09:2")) {
                                continue;
                            }
                            optionRtIvMap.put(symbol.replaceAll(" ", "+"), iv);
                        }
                        log.info("rt iv Map: {}", optionRtIvMap);
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

                String callAndPut = canTradeOptionForFutuMap.get(stock);
                String[] split = callAndPut.split("\\|");
                String call = split[0];
                String put = split[1];

                Long buyCallOrderId = buyOrderMap.get(call);
                Long buyPutOrderId = buyOrderMap.get(put);
                OrderFill buyCallOrder = tradeApi.getOrderFill(buyCallOrderId);
                OrderFill buyPutOrder = tradeApi.getOrderFill(buyPutOrderId);
                if (buyCallOrder != null && buyPutOrder != null) {
                    hasBoughtSuccess.add(stock);
                    log.info("{} buy trade success. call={}\torderId={}\tput={}\torderId={}", stock, call, buyCallOrderId, put, buyPutOrderId);
                }
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
            }

            // 11分钟后监听结束直接返回
            long current = System.currentTimeMillis();
            if (current - openTime >= STOP_MONITORY_BUY_ORDER_TIME_MILLI) {
                log.info("time is over 11min. stop monitor buy order");
                return;
            }
        }
    }

    public void monitorSellOrder() {
        log.info("monitor sell order");
        long openTime = client.getOpenTime();
        while (true) {
            long current = System.currentTimeMillis();
            if (!hasBoughtSuccess.isEmpty()) {
                for (String stock : hasSoldOrder) {
                    if (hasSoldSuccess.contains(stock)) {
                        continue;
                    }

                    String callAndPut = canTradeOptionForFutuMap.get(stock);
                    String[] split = callAndPut.split("\\|");
                    String call = split[0];
                    String put = split[1];

                    Long sellCallOrderId = sellOrderMap.get(call);
                    Long sellPutOrderId = sellOrderMap.get(put);
                    OrderFill sellCallOrder = tradeApi.getOrderFill(sellCallOrderId);
                    OrderFill sellPutOrder = tradeApi.getOrderFill(sellPutOrderId);
                    if (sellCallOrder != null && sellPutOrder != null) {
                        hasSoldSuccess.add(stock);
                        log.info("{} buy trade success. call={}\torderId={}\tput={}\torderId={}", stock, call, sellCallOrderId, put, sellPutOrderId);
                    }
                }

                // 12分钟后有买入成交，当卖出订单数量等于买入成交数量时，监听结束直接返回
                if (current - openTime >= STOP_MONITORY_SELL_ORDER_TIME_MILLI) {
                    if (hasSoldOrder.size() == hasBoughtSuccess.size()) {
                        log.info("time is over 12min. all buy order has been sold");
                        return;
                    }
                }
            } else {
                // 12分钟后没有买入成交，监听结束直接返回
                if (current - openTime >= STOP_MONITORY_SELL_ORDER_TIME_MILLI) {
                    log.info("time is over 12min. there is no buy order. stop monitor sell order ");
                    return;
                }
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
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
        long cur = System.currentTimeMillis();
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {

            @Override
            public void run() {
                while (true) {
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
                        String[] callQuoteSplit = callQuote.split("\\|");
                        String[] putQuoteSplit = putQuote.split("\\|");
                        double callBidPrice = Double.parseDouble(callQuoteSplit[0]);
                        double callAskPrice = Double.parseDouble(callQuoteSplit[1]);
                        double callMidPrice = BigDecimal.valueOf((callBidPrice + callAskPrice) / 2).setScale(2, RoundingMode.DOWN).doubleValue();
                        double putBidPrice = Double.parseDouble(putQuoteSplit[0]);
                        double putAskPrice = Double.parseDouble(putQuoteSplit[1]);
                        double putMidPrice = BigDecimal.valueOf((putBidPrice + putAskPrice) / 2).setScale(2, RoundingMode.DOWN).doubleValue();

                        // 根据已成交的订单查看买入价，不断以最新报价计算是否触发止损价（不能以当前市价止损，因为流动性不够偏差会很大）
                        Long callOrderId = buyOrderMap.get(call);
                        Long putOrderId = buyOrderMap.get(put);
                        OrderFill callOrder = tradeApi.getOrderFill(callOrderId);
                        OrderFill putOrder = tradeApi.getOrderFill(putOrderId);
                        double callOpen = callOrder.getAvgPrice();
                        double putOpen = putOrder.getAvgPrice();
                        double callDiff = callMidPrice - callOpen;
                        double putDiff = putMidPrice - putOpen;
                        double allDiff = BigDecimal.valueOf(callDiff + putDiff).setScale(2, RoundingMode.HALF_UP).doubleValue();
                        double diffRatio = BigDecimal.valueOf(allDiff / (callOpen + putOpen)).setScale(2, RoundingMode.HALF_UP).doubleValue();
                        double callCount = callOrder.getCount();
                        double putCount = putOrder.getCount();
                        if (Math.abs(diffRatio) > RealTimeDataWS_DB.OPTION_LOSS_RATIO) {
                            long sellCallOrderId = tradeApi.placeNormalSellOrder(call, callCount, callMidPrice);
                            long sellPutOrderId = tradeApi.placeNormalSellOrder(put, putCount, putMidPrice);

                            hasSoldOrder.add(stock);
                            sellOrderMap.put(call, sellCallOrderId);
                            sellOrderMap.put(put, sellPutOrderId);

                            log.info("stop loss and gain order. callOrderId={}\tcallOpen={}\tcallBid={}\tcallAsk={}\tcallMid={}\tputOrderId={}\tputOpen={}\tputBid={}\tputAsk={}\tputMid={}\tcallDiff={}\tputDiff={}\tallDiff={}\tdiffRatio={}\tsellCallOrder={}\tsellPutOrder={}",
                              callOrderId, callOpen, callBidPrice, callAskPrice, callMidPrice, putOrderId, putOpen, putBidPrice, putAskPrice, putMidPrice, callDiff, putDiff, allDiff, diffRatio, sellCallOrderId, sellPutOrderId);
                        }
                    }
                    // 11分钟后没有买入成功的，监听结束直接返回
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - openTime > STOP_MONITORY_BUY_ORDER_TIME_MILLI && hasBoughtSuccess.isEmpty()) {
                        log.info("time is over 11min. there is no buy trade to monitor stop loss.");
                        return;
                    }

                    if (currentTime > closeTime) {
                        // todo 反注册报价监听
                        log.info("time is close to the close time. stop monitor");
                        return;
                    }
                }
            }
        }, cur - openTime);
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

    public void backup() {
        List<Node> nodes = list.getNodes();
        if (CollectionUtils.isEmpty(nodes)) {
            log.info("option stock is empty");
            return;
        }

        String show = list.show();
        System.out.println("option stock: " + show);
        for (Node node : nodes) {
            String code = node.getName();
            double price = node.getPrice();
            List<String> chainList = getOptionChain.getChainList(code);
            if (CollectionUtils.isEmpty(chainList)) {
                System.out.println(code + " has no option chain");
                continue;
            }

            String optionCode = getOptionCode(chainList, price);
            int c_index = optionCode.lastIndexOf("C");
            StringBuffer sb = new StringBuffer(optionCode);
            String putOptionCode = sb.replace(c_index, c_index + 1, "P").toString();

            subQuote(optionCode);
            subOrder(optionCode);

            subQuote(putOptionCode);
            subOrder(putOptionCode);
        }
        System.out.println("finish option subcribe");
    }

    public void subQuote(String optionStock) {
        futuQuote.subBasicQuote(optionStock);
    }

    public void subOrder(String optionStock) {
        futuQuote.subOrderBook(optionStock);
    }

    public void setTradeStock(List<String> stocks) {
        tradeStock = stocks;
    }

    public String getOptionCode(List<String> chainList, double price) {
        // 找出当前价前后的行权价及等于当前价的行权价
        double priceDiff = Double.MAX_VALUE;
        String optionCode = null;
        for (String chain : chainList) {
            String[] split = chain.split("\t");
            String code = split[0];
            Double strikePrice = Double.valueOf(split[2]);
            if (strikePrice < price) {
                if (priceDiff > price - strikePrice) {
                    priceDiff = price - strikePrice;
                    optionCode = code;
                }
            } else if (strikePrice == price) {
                optionCode = code;
                break;
            } else if (strikePrice > price) {
                if (priceDiff > strikePrice - price) {
                    optionCode = code;
                }
                break;
            }
        }

        return optionCode;
    }

    public static void main(String[] args) throws InterruptedException {
        double v = BigDecimal.valueOf(0.47).setScale(2, RoundingMode.DOWN).doubleValue();
        System.out.println(v);
    }
}
