package luonq.strategy;

import bean.NearlyOptionData;
import bean.OptionCode;
import bean.OptionDaily;
import bean.StockKLine;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import luonq.ivolatility.GetAggregateImpliedVolatility;
import luonq.ivolatility.GetDailyImpliedVolatility;
import luonq.polygon.GetHistoricalSecAggregateTrade;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.LoggerFactory;
import util.BaseUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static util.Constants.*;

/**
 * 垂直套利策略
 */
public class Strategy37 {

    public static BlockingQueue<HttpClient> queue;
    public static ThreadPoolExecutor cachedThread;
    public static String apiKey = "&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY";
    public static Map<String/* today */, List<String>/* last5Days*/> last5DaysMap = Maps.newHashMap();
    public static Map<String, Map<String, Double>> ivMap = Maps.newHashMap();
    public static Map<String/* date */, Double/* rate */> riskFreeRateMap = Maps.newHashMap();
    public static Map<String/* date */, Map<String/* stock */, Double/* last close*/>> dateToLastClose = Maps.newHashMap();
    public static LocalDateTime summerTime = BaseUtils.getSummerTime(year);
    public static LocalDateTime winterTime = BaseUtils.getWinterTime(year);
    public static Map<String/* date */, Map<String/* stock */, Integer/* 1=up -1=down */>> secStockUpDownMap = Maps.newHashMap();

    public static void init() throws Exception {
        int threadCount = 10;
        int corePoolSize = threadCount;
        int maximumPoolSize = corePoolSize;
        long keepAliveTime = 60L;
        LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
        cachedThread = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS, workQueue);
        queue = new LinkedBlockingQueue<>(threadCount);
        for (int i = 0; i < threadCount; i++) {
            queue.offer(new HttpClient());
        }

        List<StockKLine> stockKLines = BaseUtils.loadDataToKline(HIS_BASE_PATH + "merge/AAPL", 2024, 2020);
        List<String> dateList = stockKLines.stream().map(StockKLine::getFormatDate).collect(Collectors.toList());
        for (int i = 0; i < dateList.size() - 6; i++) {
            last5DaysMap.put(dateList.get(i), Lists.newArrayList(dateList.get(i + 1), dateList.get(i + 2), dateList.get(i + 3), dateList.get(i + 4), dateList.get(i + 5)));
        }

        loadIv();
        riskFreeRateMap = BaseUtils.loadRiskFreeRate();
    }

    private static void loadIv() throws Exception {
        Map<String, String> ivDirMap = BaseUtils.getFileMap(USER_PATH + "optionData/IV/");
        for (String stock : ivDirMap.keySet()) {
            if (StringUtils.equalsAny(stock, "2022", "2023", "2024")) {
                continue;
            }
            String ivDirPath = ivDirMap.get(stock);
            Map<String, String> ivFileMap = BaseUtils.getFileMap(ivDirPath);
            for (String optionCode : ivFileMap.keySet()) {
                List<String> lines = BaseUtils.readFile(ivFileMap.get(optionCode));
                if (!ivMap.containsKey(optionCode)) {
                    ivMap.put(optionCode, Maps.newTreeMap(Comparator.comparing(BaseUtils::formatDateToInt)));
                }
                for (String line : lines) {
                    String[] split = line.split("\t");
                    ivMap.get(optionCode).put(split[0], Double.valueOf(split[1]));
                }
            }
        }
    }

    // 获取开盘相对于前日收盘波动超过3%且开盘价大于7的kline
    public static Map<String/* date */, Map<String/* stock */, StockKLine>> getDateToStockKlineMap() throws Exception {
        Set<String> pennyOptionStock = BaseUtils.getPennyOptionStock();
        Map<String/* date */, Map<String/* stock */, StockKLine>> dateToStockKline = Maps.newTreeMap(Comparator.comparing(BaseUtils::formatDateToInt));
        for (String stock : pennyOptionStock) {
            List<StockKLine> stockKLines = BaseUtils.loadDataToKline(HIS_BASE_PATH + "merge/" + stock, year, year - 1);
            for (int i = 0; i < stockKLines.size() - 2; i++) {
                StockKLine stockKLine = stockKLines.get(i);
                StockKLine lastKLine = stockKLines.get(i + 1);
                double open = stockKLine.getOpen();
                double lastClose = lastKLine.getClose();
                double ratio = Math.abs(open - lastClose) / lastClose * 100;
                if (ratio >= 1 || open < 7) {
                    continue;
                }
                String date = stockKLine.getFormatDate();
                if (!dateToStockKline.containsKey(date)) {
                    dateToStockKline.put(date, Maps.newHashMap());
                }

                dateToStockKline.get(date).put(stock, stockKLine);

                if (!dateToLastClose.containsKey(date)) {
                    dateToLastClose.put(date, Maps.newHashMap());
                }
                dateToLastClose.get(date).put(stock, lastClose);

                if (!secStockUpDownMap.containsKey(date)) {
                    secStockUpDownMap.put(date, Maps.newHashMap());
                }
                if (open > lastClose) {
                    secStockUpDownMap.get(date).put(stock, 1);
                } else {
                    secStockUpDownMap.get(date).put(stock, -1);
                }
            }
        }

        return dateToStockKline;
    }

    // 获取开盘相对于前日收盘波动小于1%且开盘价大于7的kline
    public static Map<String/* date */, Map<String/* stock */, StockKLine>> getDateToStockKlineMap(Map<String, Map<String, List<Double>>> secToStockPriceMap) throws Exception {
        Set<String> pennyOptionStock = BaseUtils.getPennyOptionStock();
        Map<String/* date */, Map<String/* stock */, StockKLine>> dateToStockKline = Maps.newTreeMap(Comparator.comparing(BaseUtils::formatDateToInt));
        for (String stock : pennyOptionStock) {
            List<StockKLine> stockKLines = BaseUtils.loadDataToKline(HIS_BASE_PATH + "merge/" + stock, year, year - 1);
            for (int i = 0; i < stockKLines.size() - 2; i++) {
                StockKLine stockKLine = stockKLines.get(i);
                StockKLine lastKLine = stockKLines.get(i + 1);
                String date = stockKLine.getFormatDate();
                double open = stockKLine.getOpen();
                double lastClose = lastKLine.getClose();
                double ratio = Math.abs(open - lastClose) / lastClose * 100;
                if (ratio >= 1 || open < 7) {
                    continue;
                }
                Map<String, List<Double>> dateToSecPriceMap = secToStockPriceMap.get(stock);
                if (MapUtils.isEmpty(dateToSecPriceMap)) {
                    continue;
                }
                List<Double> secPriceList = dateToSecPriceMap.get(date);
                if (CollectionUtils.isEmpty(secPriceList)) {
                    continue;
                }
                Double secPrice = secPriceList.get(secPriceList.size() - 1);
                double secPriceRatio = Math.abs(secPrice - lastClose) / lastClose * 100;
                if (secPriceRatio > 1) {
                    continue;
                }
                if (!dateToStockKline.containsKey(date)) {
                    dateToStockKline.put(date, Maps.newHashMap());
                }

                dateToStockKline.get(date).put(stock, stockKLine);

                if (!dateToLastClose.containsKey(date)) {
                    dateToLastClose.put(date, Maps.newHashMap());
                }
                dateToLastClose.get(date).put(stock, lastClose);

                if (!secStockUpDownMap.containsKey(date)) {
                    secStockUpDownMap.put(date, Maps.newHashMap());
                }
                if (secPrice > lastClose) {
                    secStockUpDownMap.get(date).put(stock, 1);
                } else {
                    secStockUpDownMap.get(date).put(stock, -1);
                }
            }
        }

        return dateToStockKline;
    }

    public static Map<String/* stock */, Map<String/* Date */, List<Double/* price */>>> getSecToStockPriceMap() throws Exception {
        Map<String, Map<String, List<Double>>> map = Maps.newHashMap();
        Set<String> pennyOptionStock = BaseUtils.getPennyOptionStock();
        int sec = 1800;

        for (String stock : pennyOptionStock) {
            String dirPath = HIS_BASE_PATH + "sec" + sec + "Aggregate/" + stock;
            Map<String, String> fileMap = BaseUtils.getFileMap(dirPath);
            map.put(stock, Maps.newHashMap());
            List<StockKLine> stockKLines = BaseUtils.loadDataToKline(HIS_BASE_PATH + "merge/" + stock, year, year - 1);
            for (StockKLine stockKLine : stockKLines) {
                String date = stockKLine.getFormatDate();
                String filePah = fileMap.get(date);
                if (StringUtils.isBlank(filePah)) {
                    GetHistoricalSecAggregateTrade.getDataSync(stock, date, sec);
                    filePah = dirPath + "/" + date;
                }
                List<String> lines = BaseUtils.readFile(filePah);
                LocalDateTime day = LocalDate.parse(date, DB_DATE_FORMATTER).atTime(0, 0);
                if (CollectionUtils.isNotEmpty(lines)) {
                    List<Double> priceList = Lists.newArrayList();
                    for (String line : lines) {
                        String[] split = line.split("\t");
                        String dateTime = split[0];
                        int time = Integer.parseInt(dateTime.substring(11, 19).replaceAll("\\:", ""));
                        if (day.isAfter(summerTime) && day.isBefore(winterTime)) {
                            if (time > 213500) {
                                break;
                            }
                        } else {
                            if (time > 223500) {
                                break;
                            }
                        }
                        Double price = Double.valueOf(split[5]);
                        priceList.add(price);
                    }
                    map.get(stock).put(date, priceList);
                }
            }
            //            System.out.println("load " + stock + " finish");
        }

        return map;
    }

    public static NearlyOptionData calOpenStrikePrice(String date, String stock, double open) throws Exception {
        List<String> callAndPuts = Strategy32.getCallAndPutsWithoutFix(date, stock);
        return calOpenStrikePrice(date, stock, open, callAndPuts);
    }

    public static NearlyOptionData calOpenStrikePrice(String date, String stock, double open, List<String> callAndPuts) {
        if (CollectionUtils.isEmpty(callAndPuts)) {
            return null;
        }

        String openPrice = String.format("%.2f", open);
        int decade = (int) open;
        int count = String.valueOf(decade).length();

        int standardCount = count + 3;
        String priceStr = openPrice.replace(".", "");
        int lastCount = standardCount - priceStr.length();
        int digitalPrice = Integer.valueOf(priceStr) * (int) Math.pow(10, lastCount);

        // 计算开盘价和行权价的差值
        int priceDiff = Integer.MAX_VALUE;
        String callOption = "";
        List<String> callList = Lists.newArrayList();
        for (int i = 0; i < callAndPuts.size(); i++) {
            String callAndPut = callAndPuts.get(i);
            String code = callAndPut.split("\\|")[0];
            callList.add(code);
        }
        for (int i = 0; i < callAndPuts.size(); i++) {
            String callAndPut = callAndPuts.get(i);
            String code = callAndPut.split("\\|")[0];
            int strikePrice = Integer.parseInt(code.substring(code.length() - 8));

            int tempDiff = Math.abs(strikePrice - digitalPrice);
            if (priceDiff >= tempDiff) {
                priceDiff = tempDiff;
                if (i + 1 == callAndPuts.size()) {
                    break;
                }
                callOption = code;
            } else {
                break;
            }
        }
        if (StringUtils.isBlank(callOption)) {
            System.out.println(stock + " has no option to calculate");
            return null;
        }

        Collections.sort(callList, (o1, o2) -> {
            int strikePrice1 = Integer.parseInt(o1.substring(o1.length() - 8));
            int strikePrice2 = Integer.parseInt(o2.substring(o2.length() - 8));
            return strikePrice1 - strikePrice2;
        });
        String lower = "", higher = "";
        String lower_2 = "", higher_2 = "";
        for (int i = 2; i < callList.size() - 2; i++) {
            if (StringUtils.equalsIgnoreCase(callList.get(i), callOption)) {
                lower = callList.get(i - 1);
                higher = callList.get(i + 1);
                lower_2 = callList.get(i - 2);
                higher_2 = callList.get(i + 2);
            }
        }
        if (StringUtils.isAnyBlank(higher, lower)) {
            //            System.out.println("there is no higher and lower option to calculate option. stock=" + stock);
            return null;
        }

        int lowerPrice = Integer.valueOf(lower.substring(lower.length() - 8));
        int higherPrice = Integer.valueOf(higher.substring(higher.length() - 8));

        int strikePrice = Integer.parseInt(callOption.substring(callOption.length() - 8));
        String upStrike = "";
        String downStrike = "";
        String upStrike_2 = "", downStrike_2 = "";
        if (digitalPrice != strikePrice) {
            if (digitalPrice < strikePrice) {
                upStrike = callOption;
                downStrike = lower;
                upStrike_2 = higher;
                downStrike_2 = lower_2;
                double downDiffRatio = (double) (strikePrice - digitalPrice) / (double) (strikePrice - lowerPrice);
                if (downDiffRatio < 0.25) {
                    upStrike = higher;
                    upStrike_2 = higher_2;
                }
            } else {
                upStrike = higher;
                downStrike = callOption;
                upStrike_2 = higher_2;
                downStrike_2 = lower;
                double downDiffRatio = (double) (digitalPrice - strikePrice) / (double) (higherPrice - strikePrice);
                if (downDiffRatio < 0.25) {
                    downStrike = lower;
                    downStrike_2 = lower_2;
                }
            }
        } else if (digitalPrice == strikePrice) {
            upStrike = higher;
            downStrike = lower;
            upStrike_2 = higher_2;
            downStrike_2 = lower_2;
        }

        String call = upStrike;
        String put = BaseUtils.getOptionPutCode(downStrike);
        String call_2 = upStrike_2;
        String put_2 = BaseUtils.getOptionPutCode(downStrike_2);

        NearlyOptionData nearlyOptionData = new NearlyOptionData();
        nearlyOptionData.setOpenPrice(open);
        nearlyOptionData.setDate(date);
        nearlyOptionData.setStock(stock);
        nearlyOptionData.setOutPriceCallOptionCode_1(call);
        nearlyOptionData.setOutPricePutOptionCode_1(put);
        nearlyOptionData.setOutPriceCallOptionCode_2(call_2);
        nearlyOptionData.setOutPricePutOptionCode_2(put_2);
        return nearlyOptionData;
    }

    public static String calTrade(OptionDaily call, OptionDaily put, NearlyOptionData nearlyOptionData) throws Exception {
        String date = call.getFrom();

        double call1StrikePrice = nearlyOptionData.getOutCall1StrikePrice();
        double put1StrikePrice = nearlyOptionData.getOutPut1StrikePrice();
        double call2StrikePrice = nearlyOptionData.getOutCall2StrikePrice();
        double put2StrikePrice = nearlyOptionData.getOutPut2StrikePrice();

        String callSymbol = call.getSymbol();
        String putSymbol = put.getSymbol();
        String callCode = callSymbol.substring(2);
        String putCode = putSymbol.substring(2);

        String call2Symbol = nearlyOptionData.getOutPriceCallOptionCode_2();
        String put2Symbol = nearlyOptionData.getOutPricePutOptionCode_2();
        String call2Code = call2Symbol.substring(2);
        String put2Code = put2Symbol.substring(2);

        int _2_index = callCode.indexOf("2");
        String stock = callCode.substring(0, _2_index);

        String callFilePath = USER_PATH + "optionData/optionQuote/" + stock + "/" + date + "/" + callCode;
        Strategy28.getOptionQuoteList(new OptionCode(callSymbol), date);
        List<String> callQuoteList = BaseUtils.readFile(callFilePath);
        String putFilePath = USER_PATH + "optionData/optionQuote/" + stock + "/" + date + "/" + putCode;
        Strategy28.getOptionQuoteList(new OptionCode(putSymbol), date);
        List<String> putQuoteList = BaseUtils.readFile(putFilePath);
        if (CollectionUtils.isEmpty(callQuoteList) || CollectionUtils.isEmpty(putQuoteList)) {
            return "noData";
        }

        List<String> dayAllSeconds = Strategy28.getDayAllSeconds(date);

        //        getOptionTradeData(stock, call.getSymbol(), put.getSymbol(), dayAllSeconds, date);
        Strategy32.getOption1MinTradeData(stock, callSymbol, putSymbol, dayAllSeconds, date);
        boolean canTrade1Min = Strategy32.calCanTrade1Min(stock, date, callSymbol, putSymbol, dayAllSeconds);
        if (!canTrade1Min) {
            return "empty";
        }

        Map<Long, Double> callQuotePriceMap = Strategy32.calQuoteListForSeconds(callQuoteList);
        Map<Long, Double> putQuotePriceMap = Strategy32.calQuoteListForSeconds(putQuoteList);

        Map<Long, Double> callBidPriceMap = Strategy32.calQuoteBidForSeconds(callQuoteList);
        Map<Long, Double> putBidPriceMap = Strategy32.calQuoteBidForSeconds(putQuoteList);

        Map<Long, Double> callTradePriceMap = Maps.newHashMap();
        Map<Long, Double> putTradePriceMap = Maps.newHashMap();
        Map<Long, Double> call2TradePriceMap = Maps.newHashMap();
        Map<Long, Double> put2TradePriceMap = Maps.newHashMap();
        Map<Long, Double> callBidTradePriceMap = Maps.newHashMap();
        Map<Long, Double> putBidTradePriceMap = Maps.newHashMap();

        double tempCallPrice = 0, tempPutPrice = 0;
        double tempCallBidPrice = 0, tempPutBidPrice = 0;
        for (int i = 0; i < dayAllSeconds.size() - 1; i++) {
            Long seconds = Long.valueOf(dayAllSeconds.get(i)) / 1000000000;
            Double callPrice = callQuotePriceMap.get(seconds);
            Double putPrice = putQuotePriceMap.get(seconds);
            if (callPrice != null) {
                tempCallPrice = callPrice;
            }
            if (putPrice != null) {
                tempPutPrice = putPrice;
            }
            callTradePriceMap.put(seconds, tempCallPrice);
            putTradePriceMap.put(seconds, tempPutPrice);

            Double callBidPrice = callBidPriceMap.get(seconds);
            Double putBidPrice = putBidPriceMap.get(seconds);
            if (callBidPrice != null) {
                tempCallBidPrice = callBidPrice;
            }
            if (putBidPrice != null) {
                tempPutBidPrice = putBidPrice;
            }
            callBidTradePriceMap.put(seconds, tempCallBidPrice);
            putBidTradePriceMap.put(seconds, tempPutBidPrice);
        }

        Double callOpen = 0d;
        Double putOpen = 0d;
        Long buySeconds = 0l;
        int min = 1;
        int sec = min * 60;
        Long openSeconds = Long.valueOf(dayAllSeconds.get(sec)) / 1000000000;
        if (callTradePriceMap.get(openSeconds) != 0) {
            callOpen = callTradePriceMap.get(openSeconds);
        }
        if (putTradePriceMap.get(openSeconds) != 0) {
            putOpen = putTradePriceMap.get(openSeconds);
        }
        if (callOpen == 0d || putOpen == 0d) {
            return "empty";
        }

        String call2FilePath = USER_PATH + "optionData/optionQuote/" + stock + "/" + date + "/" + call2Code;
        Strategy28.getOptionQuoteList(new OptionCode(call2Symbol), date);
        List<String> call2QuoteList = BaseUtils.readFile(call2FilePath);
        String put2FilePath = USER_PATH + "optionData/optionQuote/" + stock + "/" + date + "/" + put2Code;
        Strategy28.getOptionQuoteList(new OptionCode(put2Symbol), date);
        List<String> put2QuoteList = BaseUtils.readFile(put2FilePath);
        if (CollectionUtils.isEmpty(call2QuoteList) || CollectionUtils.isEmpty(put2QuoteList)) {
            return "noData";
        }
        Map<Long, Double> call2QuotePriceMap = Strategy32.calQuoteListForSeconds(call2QuoteList);
        Map<Long, Double> put2QuotePriceMap = Strategy32.calQuoteListForSeconds(put2QuoteList);
        double tempCall2Price = 0, tempPut2Price = 0;
        for (int i = 0; i < dayAllSeconds.size() - 1; i++) {
            Long seconds = Long.valueOf(dayAllSeconds.get(i)) / 1000000000;
            Double call2Price = call2QuotePriceMap.get(seconds);
            Double put2Price = put2QuotePriceMap.get(seconds);
            if (call2Price != null) {
                tempCall2Price = call2Price;
            }
            if (put2Price != null) {
                tempPut2Price = put2Price;
            }
            call2TradePriceMap.put(seconds, tempCall2Price);
            put2TradePriceMap.put(seconds, tempPut2Price);
        }

        String result = "";
        double call2Open = call2TradePriceMap.get(openSeconds), put2Open = put2TradePriceMap.get(openSeconds);
        Integer stockUpDown = secStockUpDownMap.get(date).get(stock);
        double lowestRatio = Double.MAX_VALUE, highestRatio = Double.MIN_VALUE;
        if (callOpen == call2Open || putOpen == put2Open) {
            return "empty";
        }
        if ((stockUpDown > 0 && callOpen < call2Open) || (stockUpDown < 0 && put2Open < put2Open)) {
            return "empty";
        }
        double callCost = call2Open - callOpen;
        double putCost = put2Open - putOpen;
        for (int i = sec + 60; i < dayAllSeconds.size() - 60; i++) {
            Long seconds = Long.valueOf(dayAllSeconds.get(i)) / 1000000000;
            Double callClose = callTradePriceMap.get(seconds);
            Double putClose = putTradePriceMap.get(seconds);
            Double call2Close = call2TradePriceMap.get(seconds);
            Double put2Close = put2TradePriceMap.get(seconds);
            if (callClose == 0 || putClose == 0) {
                continue;
            }
            double callDiff = BigDecimal.valueOf(call2Close - callClose).setScale(5, RoundingMode.HALF_UP).doubleValue();
            double putDiff = BigDecimal.valueOf(put2Close - putClose).setScale(5, RoundingMode.HALF_UP).doubleValue();
            double callDiffRatio = BigDecimal.valueOf((callCost - callDiff) / callCost * 100).setScale(2, RoundingMode.HALF_UP).doubleValue();
            double putDiffRatio = BigDecimal.valueOf((putCost - putDiff) / putCost * 100).setScale(2, RoundingMode.HALF_UP).doubleValue();
            double allDiffRatio = 0;
            if (stockUpDown > 0) {
                allDiffRatio = callDiffRatio;
            } else {
                allDiffRatio = putDiffRatio;
            }

            String sellTime = LocalDateTime.ofEpochSecond(seconds, 0, ZoneOffset.of("+8")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String buyTime = LocalDateTime.ofEpochSecond(buySeconds, 0, ZoneOffset.of("+8")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            //            if (allDiffRatio < -40) {
            //                result = buyTime + "\t" + sellTime + "\t" + callOpen + "\t" + callClose + "\t" + putOpen + "\t" + putClose + "\t" + call2Open + "\t" + call2Close + "\t" + put2Open + "\t" + put2Close + "\t" + stockUpDown + "\t" + allDiffRatio + "\t" + callDiffRatio + "\t" + putDiffRatio;
            //                return result;
            //            }
            if (allDiffRatio > 50 || allDiffRatio < -100) {
                if (stockUpDown > 0) {
                    double strikeDiff = call2StrikePrice - call1StrikePrice;
                    double callBuyDiff = callOpen - call2Open;
                    double priceDiffRatio = BigDecimal.valueOf(callBuyDiff / strikeDiff * 100).setScale(4, RoundingMode.HALF_UP).doubleValue();
                    result = buyTime + "\t" + sellTime + "\t" + callOpen + "\t" + callClose + "\t" + call2Open + "\t" + call2Close + "\t" + stockUpDown + "\t" + allDiffRatio + "\t" + priceDiffRatio;
                } else {
                    double strikeDiff = put1StrikePrice - put2StrikePrice;
                    double putBuyDiff = putOpen - put2Open;
                    double priceDiffRatio = BigDecimal.valueOf(putBuyDiff / strikeDiff * 100).setScale(4, RoundingMode.HALF_UP).doubleValue();
                    result = buyTime + "\t" + sellTime + "\t" + putOpen + "\t" + putClose + "\t" + put2Open + "\t" + put2Close + "\t" + stockUpDown + "\t" + allDiffRatio + "\t" + priceDiffRatio;
                }
                //                return result;
            }
            if (lowestRatio > allDiffRatio) {
                lowestRatio = allDiffRatio;
            }
            if (highestRatio < allDiffRatio) {
                highestRatio = allDiffRatio;
            }
        }
        Long sellSeconds = Long.valueOf(dayAllSeconds.get(dayAllSeconds.size() - 60)) / 1000000000;
        Double callClose = callTradePriceMap.get(sellSeconds);
        Double putClose = putTradePriceMap.get(sellSeconds);
        Double call2Close = call2TradePriceMap.get(sellSeconds);
        Double put2Close = put2TradePriceMap.get(sellSeconds);
        double callDiff = BigDecimal.valueOf(call2Close - callClose).setScale(5, RoundingMode.HALF_UP).doubleValue();
        double putDiff = BigDecimal.valueOf(put2Close - putClose).setScale(5, RoundingMode.HALF_UP).doubleValue();
        double callDiffRatio = BigDecimal.valueOf((callCost - callDiff) / callCost * 100).setScale(2, RoundingMode.HALF_UP).doubleValue();
        double putDiffRatio = BigDecimal.valueOf((putCost - putDiff) / putCost * 100).setScale(2, RoundingMode.HALF_UP).doubleValue();
        String buyTime = LocalDateTime.ofEpochSecond(buySeconds, 0, ZoneOffset.of("+8")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String sellTime = LocalDateTime.ofEpochSecond(sellSeconds, 0, ZoneOffset.of("+8")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        double halfHourDiffRatio = calDiffRatio(dayAllSeconds, 1800, stockUpDown, callTradePriceMap, putTradePriceMap, call2TradePriceMap, put2TradePriceMap, callCost, putCost);
        double oneHourDiffRatio = calDiffRatio(dayAllSeconds, 3600, stockUpDown, callTradePriceMap, putTradePriceMap, call2TradePriceMap, put2TradePriceMap, callCost, putCost);
        double twoHourDiffRatio = calDiffRatio(dayAllSeconds, 7200, stockUpDown, callTradePriceMap, putTradePriceMap, call2TradePriceMap, put2TradePriceMap, callClose, putCost);
        double threeHourDiffRatio = calDiffRatio(dayAllSeconds, 10800, stockUpDown, callTradePriceMap, putTradePriceMap, call2TradePriceMap, put2TradePriceMap, callCost, putCost);
        double fourHourDiffRatio = calDiffRatio(dayAllSeconds, 14400, stockUpDown, callTradePriceMap, putTradePriceMap, call2TradePriceMap, put2TradePriceMap, callCost, putCost);
        double fiveHourDiffRatio = calDiffRatio(dayAllSeconds, 18000, stockUpDown, callTradePriceMap, putTradePriceMap, call2TradePriceMap, put2TradePriceMap, callCost, putCost);
        double sixHourDiffRatio = calDiffRatio(dayAllSeconds, 21600, stockUpDown, callTradePriceMap, putTradePriceMap, call2TradePriceMap, put2TradePriceMap, callCost, putCost);
        String diffRatioInfo = "";
        diffRatioInfo = halfHourDiffRatio + "\t" + oneHourDiffRatio + "\t" + twoHourDiffRatio + "\t" + threeHourDiffRatio + "\t" + fourHourDiffRatio + "\t" + fiveHourDiffRatio + "\t" + sixHourDiffRatio;
        if (stockUpDown > 0) {
            double strikeDiff = call2StrikePrice - call1StrikePrice;
            double callBuyDiff = callOpen - call2Open;
            double priceDiffRatio = BigDecimal.valueOf(callBuyDiff / strikeDiff * 100).setScale(4, RoundingMode.HALF_UP).doubleValue();
            result = buyTime + "\t" + sellTime + "\t" + callOpen + "\t" + callClose + "\t" + call2Open + "\t" + call2Close + "\t" + stockUpDown + "\t" + callDiffRatio + "\t" + priceDiffRatio + "\t" + diffRatioInfo;
        } else {
            double strikeDiff = put1StrikePrice - put2StrikePrice;
            double putBuyDiff = putOpen - put2Open;
            double priceDiffRatio = BigDecimal.valueOf(putBuyDiff / strikeDiff * 100).setScale(4, RoundingMode.HALF_UP).doubleValue();
            result = buyTime + "\t" + sellTime + "\t" + putOpen + "\t" + putClose + "\t" + put2Open + "\t" + put2Close + "\t" + stockUpDown + "\t" + putDiffRatio + "\t" + priceDiffRatio + "\t" + diffRatioInfo;
        }

        return result;
    }

    public static double calDiffRatio(List<String> dayAllSeconds, int sec, int callOrPut, Map<Long, Double> callTradePriceMap, Map<Long, Double> putTradePriceMap, Map<Long, Double> call2TradePriceMap, Map<Long, Double> put2TradePriceMap, double callCost, double putCost) {
        Long sellSeconds = Long.valueOf(dayAllSeconds.get(sec)) / 1000000000;
        double diffRatio;
        if (callOrPut == 1) { // call
            Double callClose = callTradePriceMap.get(sellSeconds);
            Double call2Close = call2TradePriceMap.get(sellSeconds);
            double callDiff = BigDecimal.valueOf(call2Close - callClose).setScale(5, RoundingMode.HALF_UP).doubleValue();
            diffRatio = BigDecimal.valueOf((callCost - callDiff) / callCost * 100).setScale(2, RoundingMode.HALF_UP).doubleValue();
        } else {
            Double putClose = putTradePriceMap.get(sellSeconds);
            Double put2Close = put2TradePriceMap.get(sellSeconds);
            double putDiff = BigDecimal.valueOf(put2Close - putClose).setScale(5, RoundingMode.HALF_UP).doubleValue();
            diffRatio = BigDecimal.valueOf((putCost - putDiff) / putCost * 100).setScale(2, RoundingMode.HALF_UP).doubleValue();
        }
        return diffRatio;
    }

    public static OptionDaily requestOptionDaily(String code, String date) throws Exception {
        OptionDaily optionDaily = Strategy32.getOptionDaily(code, date);
        if (optionDaily != null) {
            return optionDaily;
        }

        CloseableHttpClient httpClient = HttpClients.createDefault();
        optionDaily = Strategy32.requestOptionDailyList(httpClient, date, code);

        Strategy32.writeOptionDaily(optionDaily, code, date);
        Strategy32.refreshOptionDailyMap(optionDaily, code, date);
        return optionDaily;
    }

    public static List<Double> getIvList(String optionCode, String date) throws Exception {
        List<String> last5Days = last5DaysMap.get(date);
        String code = optionCode.substring(2);
        if (!ivMap.containsKey(code)) {
            Map<String/* optionCode */, String/* date */> optionCodeDateMap = Maps.newHashMap();
            optionCodeDateMap.put(optionCode, date);
            GetDailyImpliedVolatility.getHistoricalIV(optionCodeDateMap);
            ivMap.put(code, Maps.newTreeMap(Comparator.comparing(BaseUtils::formatDateToInt)));
        } else {
            Map<String, Double> ivValueMap = ivMap.get(code);
            if (!ivValueMap.containsKey(last5Days.get(0))) {
                Map<String/* optionCode */, String/* date */> optionCodeDateMap = Maps.newHashMap();
                optionCodeDateMap.put(optionCode, date);
                GetDailyImpliedVolatility.getHistoricalIV(optionCodeDateMap);
            }
        }

        int _2_index = code.indexOf("2");
        String stock = code.substring(0, _2_index);
        List<String> lines = BaseUtils.readFile(USER_PATH + "optionData/IV/" + stock + "/" + code);
        for (String line : lines) {
            String[] split = line.split("\t");
            ivMap.get(code).put(split[0], Double.valueOf(split[1]));
        }

        Map<String, Double> ivValueMap = ivMap.get(code);
        List<Double> ivList = last5Days.stream().filter(d -> ivValueMap.containsKey(d) && ivValueMap.get(d) != -2).map(d -> ivValueMap.get(d)).collect(Collectors.toList());
        return ivList;
    }

    public static boolean canTradeForIv(List<Double> ivList) {
        if (CollectionUtils.isEmpty(ivList) || ivList.size() < 3) {
            return false;
        }

        //        if (ivList.get(0) < 0.6) {
        //            return true;
        //        } else {
        //            return false;
        //        }
        return true;
    }

    public static void main(String[] args) throws Exception {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.http").setLevel(Level.INFO);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.INFO);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.commons").setLevel(Level.ERROR);

        init();
        Strategy28.init();
        Strategy32.init();
        GetDailyImpliedVolatility.init();
        GetAggregateImpliedVolatility.init();
        GetHistoricalSecAggregateTrade.init();

        //        Map<String, Map<String, List<Double>>> secToStockPriceMap = getSecToStockPriceMap();
        Map<String, Map<String, StockKLine>> dateToStockKlineMap = getDateToStockKlineMap();
        //        Map<String, Map<String, StockKLine>> dateToStockKlineMap = getDateToStockKlineMap(secToStockPriceMap);
        //        Map<String, List<Double>> ivMap = loadIvMap();

        for (String date : dateToStockKlineMap.keySet()) {
            if (invalidDay.contains(date)) {
                //                continue;
            }

            Map<String, Double> lastCloseMap = dateToLastClose.get(date);
            if (!weekSet.contains(date)) {
                continue;
            }

            if (!date.equals("2024-01-19")) {
                //                                continue;
            }

            Map<String, StockKLine> stockKLineMap = dateToStockKlineMap.get(date);
            //            List<NearlyOptionData> nearlyOptionDataList = dateToOpenStrikePriceRatioMap.get(BaseUtils.unformatDate(date));
            //            Map<String, NearlyOptionData> stockToNearlyOption = nearlyOptionDataList.stream().collect(Collectors.toMap(NearlyOptionData::getStock, v -> v));
            //            Set<String> stockSet = earningStocks.stream().collect(Collectors.toSet());
            Set<String> stockSet = stockKLineMap.keySet();
            Map<Double/* open strike diff */, String/* stock */> stockToOpenStrikeDiffMap = Maps.newTreeMap((o1, o2) -> o1.compareTo(o2));
            Map<String, Integer> stockUpDownMap = secStockUpDownMap.get(date);
            for (String stock : stockSet) {
                if (!stock.equals("KSS")) {
                    //                    continue;
                }
                if (stockKLineMap.containsKey(stock)) {
                    StockKLine stockKLine = stockKLineMap.get(stock);
                    double open = stockKLine.getOpen();
                    NearlyOptionData nearlyOptionData = calOpenStrikePrice(date, stock, open);
                    if (nearlyOptionData == null) {
                        continue;
                    }
                    Double outCall1StrikePrice = nearlyOptionData.getOutCall1StrikePrice();
                    Double outPut1StrikePrice = nearlyOptionData.getOutPut1StrikePrice();

                    Integer upOrDown = stockUpDownMap.get(stock);
                    double diff;
                    if (upOrDown > 0) {
                        diff = BigDecimal.valueOf(open / outCall1StrikePrice * 100).setScale(4, RoundingMode.HALF_UP).doubleValue();
                    } else {
                        diff = BigDecimal.valueOf(outPut1StrikePrice / open * 100).setScale(4, RoundingMode.HALF_UP).doubleValue();
                    }
                    stockToOpenStrikeDiffMap.put(diff, stock);
                }
            }
            int count = 0;
            for (String stock : stockToOpenStrikeDiffMap.values()) {
                if (!stock.equals("KSS")) {
                    //                    continue;
                }
                if (stockKLineMap.containsKey(stock)) {
                    StockKLine stockKLine = stockKLineMap.get(stock);
                    //                    System.out.println(stock + "\t" + stockKLine);

                    double open = stockKLine.getOpen();
                    double lastClose = lastCloseMap.get(stock);
                    double ratio = Math.abs(open - lastClose) / lastClose * 100;
                    String ratioStr = String.format("%.2f", ratio);
                    String formatDate = stockKLine.getFormatDate();

                    //                    NearlyOptionData nearlyOptionData = stockToNearlyOption.get(stock);
                    NearlyOptionData nearlyOptionData = calOpenStrikePrice(date, stock, open);
                    if (nearlyOptionData == null) {
                        continue;
                    }
                    //                    if (nearlyOptionData2 == null) {
                    //                        System.out.println("nearlyOptionData2 is null. stock=" + stock + " open=" + open);
                    //                        continue;
                    //                    }
                    String outPriceCallOptionCode_1 = nearlyOptionData.getOutPriceCallOptionCode_1();
                    String outPricePutOptionCode_1 = nearlyOptionData.getOutPricePutOptionCode_1();
                    String outPriceCallOptionCode_2 = nearlyOptionData.getOutPriceCallOptionCode_2();
                    String outPricePutOptionCode_2 = nearlyOptionData.getOutPricePutOptionCode_2();
                    Double outCall1StrikePrice = nearlyOptionData.getOutCall1StrikePrice();
                    Double outCall2StrikePrice = nearlyOptionData.getOutCall2StrikePrice();
                    Double outPut1StrikePrice = nearlyOptionData.getOutPut1StrikePrice();
                    Double outPut2StrikePrice = nearlyOptionData.getOutPut2StrikePrice();
                    //                    if (!StringUtils.equals(outPriceCallOptionCode_1, nearlyOptionData2.getOutPriceCallOptionCode_1()) || !StringUtils.equals(outPricePutOptionCode_1, nearlyOptionData2.getOutPricePutOptionCode_1())) {
                    //                        System.out.println();
                    //                    }

                    OptionDaily callDaily = requestOptionDaily(outPriceCallOptionCode_1, formatDate);
                    OptionDaily putDaily = requestOptionDaily(outPricePutOptionCode_1, formatDate);
                    if (callDaily == null || putDaily == null) {
                        System.out.println(stock + " " + date + " option daily is null");
                        continue;
                    }
                    // 过滤前日call和put交易量小于100
                    String lastDate = Strategy32.dateMap.get(formatDate);
                    if (StringUtils.isBlank(lastDate)) {
                        continue;
                    }
                    //                    OptionDaily last_call_1 = Strategy32.getOptionDaily(callDaily.getSymbol(), lastDate);
                    //                    OptionDaily last_put_1 = Strategy32.getOptionDaily(putDaily.getSymbol(), lastDate);
                    OptionDaily last_call_1 = requestOptionDaily(callDaily.getSymbol(), lastDate);
                    OptionDaily last_put_1 = requestOptionDaily(putDaily.getSymbol(), lastDate);
                    if (last_call_1 == null || last_put_1 == null || (last_call_1.getVolume() < 100 || last_put_1.getVolume() < 100)) {
                        continue;
                    }
                    long totalLastVolume = last_call_1.getVolume() + last_put_1.getVolume();
                    double totalLastClose = BigDecimal.valueOf(last_call_1.getClose() + last_put_1.getClose()).setScale(2, RoundingMode.HALF_UP).doubleValue();
                    if (totalLastClose <= 0.5) {
                        continue;
                    }

                    double callOpen = callDaily.getOpen();
                    double putOpen = putDaily.getOpen();
                    if (callOpen == 0 || putOpen == 0) {
                        continue;
                    }

                    List<Double> callIvList = getIvList(outPriceCallOptionCode_1, date);
                    List<Double> putIvList = getIvList(outPricePutOptionCode_1, date);
                    boolean callCanTrade = canTradeForIv(callIvList);
                    boolean putCanTrade = canTradeForIv(putIvList);
                    if (!callCanTrade || !putCanTrade) {
                        continue;
                    }

                    // 计算理论call和put的买入价
                    //                    Double calCallOpen = calOpen(secToStockPriceMap, outPriceCallOptionCode_1, date);
                    //                    Double calPutOpen = calOpen(secToStockPriceMap, outPricePutOptionCode_1, date);
                    //                    Double calCallOpen = calOpen(secToStockPriceMap, outPriceCallOptionCode_1, date, seconds);
                    //                    Double calPutOpen = calOpen(secToStockPriceMap, outPricePutOptionCode_1, date, seconds);

                    String simulateTrade = "";
                    String ivInfo = "";
                    simulateTrade = calTrade(callDaily, putDaily, nearlyOptionData);
                    if (StringUtils.equalsAnyIgnoreCase(simulateTrade, "noData", "empty")) {
                        continue;
                    }
                    String callIvInfo = StringUtils.join(Lists.reverse(callIvList.subList(0, 3)), "\t");
                    String putIvInfo = StringUtils.join(Lists.reverse(putIvList.subList(0, 3)), "\t");
                    ivInfo = callIvInfo + "\t" + putIvInfo;
                    String optionList = outPriceCallOptionCode_1 + "\t" + outCall1StrikePrice + "\t" + outPricePutOptionCode_1 + "\t" + outPut1StrikePrice + "\t" + outPriceCallOptionCode_2 + "\t" + outCall2StrikePrice + "\t" + outPricePutOptionCode_2 + "\t" + outPut2StrikePrice;
                    System.out.println(stock + "\t" + open + "\t" + totalLastVolume + "\t" + date + "\t" + optionList + "\t" + ivInfo + "\t" + simulateTrade);
                    count++;
                    if (count == 3) {
                        //                        break;
                    }
                }
            }
        }
    }
}
