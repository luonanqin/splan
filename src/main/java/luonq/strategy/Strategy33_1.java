package luonq.strategy;

import bean.AggregateOptionIV;
import bean.EarningDate;
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
import java.time.temporal.ChronoUnit;
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

import static luonq.strategy.Strategy32.*;
import static util.Constants.*;

/**
 * 宽跨式策略
 */
public class Strategy33_1 {

    public static BlockingQueue<HttpClient> queue;
    public static ThreadPoolExecutor cachedThread;
    public static String apiKey = "&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY";
    public static Map<String/* today */, List<String>/* last5Days*/> last5DaysMap = Maps.newHashMap();
    public static Map<String, Map<String, Double>> ivMap = Maps.newHashMap();
    public static Map<String/* date */, Double/* rate */> riskFreeRateMap = Maps.newHashMap();
    public static Map<String/* date */, Map<String/* stock */, Double/* last close*/>> dateToLastClose = Maps.newHashMap();
    public static LocalDateTime summerTime = BaseUtils.getSummerTime(year);
    public static LocalDateTime winterTime = BaseUtils.getWinterTime(year);

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

    public static Map<String/* date */, List<String>/* stock */> getEarningForEveryDay() throws Exception {
        Set<String> pennyOptionStock = BaseUtils.getPennyOptionStock();
        List<StockKLine> stockKLines = BaseUtils.loadDataToKline(HIS_BASE_PATH + "merge/AAPL", year, year - 1);
        Map<String, List<String>> result = Maps.newHashMap();
        for (int i = 3; i < stockKLines.size() - 1; i++) {
            String date = stockKLines.get(i).getFormatDate();
            if (!date.equals("2024-05-09")) {
                //                continue;
            }
            String date_after1 = stockKLines.get(i - 1).getFormatDate();
            String date_after2 = stockKLines.get(i - 2).getFormatDate();
            String date_after3 = stockKLines.get(i - 3).getFormatDate();
            Map<String, List<EarningDate>> earning = BaseUtils.getAllEarningDate2(date);
            Map<String, List<EarningDate>> earning_1 = BaseUtils.getAllEarningDate2(date_after1);
            Map<String, List<EarningDate>> earning_2 = BaseUtils.getAllEarningDate2(date_after2);
            Map<String, List<EarningDate>> earning_3 = BaseUtils.getAllEarningDate2(date_after3);
            earning.forEach((k, v) -> {
                k = BaseUtils.formatDate(k);
                if (!k.equals(date)) {
                    if (!result.containsKey(date)) {
                        result.put(date, Lists.newArrayList());
                    }
                    result.get(date).addAll(v.stream().filter(s -> pennyOptionStock.contains(s.getStock())).map(EarningDate::getStock).collect(Collectors.toList()));
                }
            });
            earning_1.forEach((k, v) -> {
                k = BaseUtils.formatDate(k);
                if (!k.equals(date)) {
                    if (!result.containsKey(date)) {
                        result.put(date, Lists.newArrayList());
                    }
                    result.get(date).addAll(v.stream().filter(s -> pennyOptionStock.contains(s.getStock())).map(EarningDate::getStock).collect(Collectors.toList()));
                }
            });
            earning_2.forEach((k, v) -> {
                k = BaseUtils.formatDate(k);
                if (!k.equals(date)) {
                    if (!result.containsKey(date)) {
                        result.put(date, Lists.newArrayList());
                    }
                    result.get(date).addAll(v.stream().filter(s -> pennyOptionStock.contains(s.getStock())).map(EarningDate::getStock).collect(Collectors.toList()));
                }
            });
            earning_3.forEach((k, v) -> {
                k = BaseUtils.formatDate(k);
                if (!k.equals(date)) {
                    if (!result.containsKey(date)) {
                        result.put(date, Lists.newArrayList());
                    }
                    result.get(date).addAll(v.stream().filter(s -> pennyOptionStock.contains(s.getStock())).map(EarningDate::getStock).collect(Collectors.toList()));
                }
            });
        }

        return result;
    }

    public static Map<String/* date */, List<String>/* stock */> getEarningForToday() throws Exception {
        Set<String> pennyOptionStock = BaseUtils.getPennyOptionStock();
        List<StockKLine> stockKLines = BaseUtils.loadDataToKline(HIS_BASE_PATH + "merge/AAPL", year, year - 1);
        Map<String, List<String>> result = Maps.newHashMap();
        for (int i = 1; i < stockKLines.size() - 1; i++) {
            String date = stockKLines.get(i).getFormatDate();
            if (!date.equals("2024-05-09")) {
                //                continue;
            }
            Map<String, List<EarningDate>> earning = BaseUtils.getAllEarningDate2(date);
            earning.forEach((k, v) -> {
                k = BaseUtils.formatDate(k);
                if (!result.containsKey(k)) {
                    result.put(k, Lists.newArrayList());
                }
                result.get(k).addAll(v.stream().filter(s -> pennyOptionStock.contains(s.getStock())).map(EarningDate::getStock).collect(Collectors.toList()));
            });
        }

        return result;
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
                if (ratio < 3 || open < 7) {
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
            }
        }

        return dateToStockKline;
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

    public static Map<String/* stock */, Map<String/* Date */, Double/* price */>> getSecToStockPriceMap() throws Exception {
        Map<String, Map<String, Double>> map = Maps.newHashMap();
        Set<String> pennyOptionStock = BaseUtils.getPennyOptionStock();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        int sec = 120;

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
                if (CollectionUtils.isNotEmpty(lines)) {
                    for (String line : lines) {
                        String[] split = line.split("\t");
                        String datetime = split[0];
                        Double price = Double.valueOf(split[5]);
                        map.get(stock).put(datetime, price);
                    }

                    // 填充没有报价的时间，用最近一次的报价填充当前空余时间
                    LocalDateTime t = LocalDateTime.parse(lines.get(0).split("\t")[0], formatter);
                    Double price = map.get(stock).get(t);
                    for (int i = 0; i < sec; i++) {
                        t = t.plusSeconds(1);
                        String datetime = t.format(formatter);
                        Map<String, Double> priceMap = map.get(stock);
                        if (!priceMap.containsKey(datetime)) {
                            priceMap.put(datetime, price);
                        } else {
                            price = priceMap.get(datetime);
                        }
                    }
                }
            }
        }

        return map;
    }

    public static double calOpen(Map<String, Map<String, Double>> secToStockPriceMap, String optionCode, String date) throws Exception {
        String expireDate = optionCode.substring(optionCode.length() - 15, optionCode.length() - 9);
        String expireDay = LocalDate.parse(expireDate, DateTimeFormatter.ofPattern("yyMMdd")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        String type = optionCode.substring(optionCode.length() - 9, optionCode.length() - 8);
        String priceStr = optionCode.substring(optionCode.length() - 8);
        int priceInt = Integer.valueOf(priceStr);
        String strikePrice = BigDecimal.valueOf(priceInt).divide(BigDecimal.valueOf(1000)).setScale(1, RoundingMode.DOWN).toString();
        if (strikePrice.contains(".0")) {
            strikePrice = strikePrice.substring(0, strikePrice.length() - 2);
        }
        double strikePriceD = Double.valueOf(strikePrice);
        Double dateIV = GetAggregateImpliedVolatility.getAggregateIv(optionCode.substring(2), date);
        if (dateIV <= 0) {
            return 0;
        }

        int _2_index = optionCode.indexOf("2");
        String stock = optionCode.substring(2, _2_index);
        Double stockPrice = 0d;
        Map<String, String> optionToFirstMap = GetAggregateImpliedVolatility.dateToFirstIvTimeMap.get(date);
        if (MapUtils.isNotEmpty(optionToFirstMap)) {
            String firstDatetime = optionToFirstMap.get(optionCode.substring(2));
            if (StringUtils.isNotBlank(firstDatetime)) {
                int hour;
                LocalDateTime day = LocalDate.parse(date, DB_DATE_FORMATTER).atTime(0, 0);
                if (day.isAfter(summerTime) && day.isBefore(winterTime)) {
                    hour = 21;
                } else {
                    hour = 22;
                }
                firstDatetime = firstDatetime.replaceFirst("09:", hour + ":");
                stockPrice = secToStockPriceMap.get(stock).get(firstDatetime);
                if (stockPrice == null) {
                    //                    System.out.println(optionCode + " " + firstDatetime + " has no trade");
                    return 0;
                }
            } else {
                return 0;
            }
        } else {
            List<Double> priceList = secToStockPriceMap.get(stock).values().stream().collect(Collectors.toList());
            stockPrice = priceList.get(priceList.size() - 1);
        }

        Double rate = riskFreeRateMap.get(date);
        if (rate == null) {
            rate = 0.0526;
        }
        if (type.equalsIgnoreCase("C")) {
            return BaseUtils.getCallPredictedValue(stockPrice, strikePriceD, rate, dateIV, date, expireDay);
        } else {
            return BaseUtils.getPutPredictedValue(stockPrice, strikePriceD, rate, dateIV, date, expireDay);
        }
    }

    public static double calOpen(Map<String, Map<String, Double>> secToStockPriceMap, String optionCode, String date, int seconds) throws Exception {
        String expireDate = optionCode.substring(optionCode.length() - 15, optionCode.length() - 9);
        String expireDay = LocalDate.parse(expireDate, DateTimeFormatter.ofPattern("yyMMdd")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        String type = optionCode.substring(optionCode.length() - 9, optionCode.length() - 8);
        String priceStr = optionCode.substring(optionCode.length() - 8);
        int priceInt = Integer.valueOf(priceStr);
        String strikePrice = BigDecimal.valueOf(priceInt).divide(BigDecimal.valueOf(1000)).setScale(1, RoundingMode.DOWN).toString();
        if (strikePrice.contains(".0")) {
            strikePrice = strikePrice.substring(0, strikePrice.length() - 2);
        }
        double strikePriceD = Double.valueOf(strikePrice);

        // 根据计算的可交易时间，选择时间对应的iv及股票报价
        String code = optionCode.substring(2);
        List<Double> ivList = GetAggregateImpliedVolatility.getAggregateIvList(code, date);
        if (CollectionUtils.isEmpty(ivList)) {
            return 0;
        }

        int _2_index = optionCode.indexOf("2");
        String stock = optionCode.substring(2, _2_index);
        Double stockPrice = 0d;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        Map<String, List<String>> optionToFirstMap = GetAggregateImpliedVolatility.dateToFirstIvTimeListMap.get(date);
        List<String> ivTimeList = optionToFirstMap.get(code);
        LocalDateTime canTradeTime = LocalDateTime.parse(date + " 00:00:00", formatter).withHour(9).withMinute(30).withSecond(0).plusSeconds(seconds);

        double iv = 0;
        for (int i = 0; i < ivTimeList.size(); i++) {
            LocalDateTime dateTime = LocalDateTime.parse(ivTimeList.get(i), formatter);
            if (dateTime.isAfter(canTradeTime)) {
                if (i == 0) {
                    iv = ivList.get(i);
                } else {
                    iv = ivList.get(i - 1);
                }
                break;
            }
        }

        int hour;
        LocalDateTime day = LocalDate.parse(date, DB_DATE_FORMATTER).atTime(0, 0);
        if (day.isAfter(summerTime) && day.isBefore(winterTime)) {
            hour = 21;
        } else {
            hour = 22;
        }
        Map<String, Double> stockPriceMap = secToStockPriceMap.get(stock);
        String canTradeTimeFormat = canTradeTime.format(formatter);
        canTradeTimeFormat = canTradeTimeFormat.replace("09", String.valueOf(hour));
        stockPrice = stockPriceMap.get(canTradeTimeFormat);

        if (stockPrice == null) {
            List<Double> priceList = secToStockPriceMap.get(stock).values().stream().collect(Collectors.toList());
            stockPrice = priceList.get(priceList.size() - 1);
        }

        Double rate = riskFreeRateMap.get(date);
        if (rate == null) {
            rate = 0.0526;
        }
        if (type.equalsIgnoreCase("C")) {
            return BaseUtils.getCallPredictedValue(stockPrice, strikePriceD, rate, iv, date, expireDay);
        } else {
            return BaseUtils.getPutPredictedValue(stockPrice, strikePriceD, rate, iv, date, expireDay);
        }
    }

    public static String calStraddleSimulateTrade(OptionDaily call, OptionDaily put, Double calCallOpen, Double calPutOpen) throws Exception {
        String date = call.getFrom();
        String callSymbol = call.getSymbol();
        String putSymbol = put.getSymbol();
        String callCode = callSymbol.substring(2);
        String putCode = putSymbol.substring(2);

        int _2_index = callCode.indexOf("2");
        String stock = callCode.substring(0, _2_index);

        String callFilePath = USER_PATH + "optionData/optionQuote/" + stock + "/" + date + "/" + callCode;
        Strategy28.getOptionQuoteList(new OptionCode(callSymbol), date);
        //        Strategy28.sortQuote(callFilePath);
        List<String> callQuoteList = BaseUtils.readFile(callFilePath);
        String putFilePath = USER_PATH + "optionData/optionQuote/" + stock + "/" + date + "/" + putCode;
        Strategy28.getOptionQuoteList(new OptionCode(putSymbol), date);
        //        Strategy28.sortQuote(putFilePath);
        List<String> putQuoteList = BaseUtils.readFile(putFilePath);
        if (CollectionUtils.isEmpty(callQuoteList) || CollectionUtils.isEmpty(putQuoteList)) {
            return "noData";
        }

        List<String> dayAllSeconds = Strategy28.getDayAllSeconds(date);

        //        getOptionTradeData(stock, call.getSymbol(), put.getSymbol(), dayAllSeconds, date);
        getOption1MinTradeData(stock, callSymbol, putSymbol, dayAllSeconds, date);
        boolean canTrade1Min = calCanTrade1Min(stock, date, callSymbol, putSymbol, dayAllSeconds);
        if (!canTrade1Min) {
            return "empty";
        }

        Map<Long, Double> callQuotePriceMap = calQuoteListForSeconds(callQuoteList);
        Map<Long, Double> putQuotePriceMap = calQuoteListForSeconds(putQuoteList);
        Map<Long, Double> callBidPriceMap = calQuoteBidForSeconds(callQuoteList);
        Map<Long, Double> putBidPriceMap = calQuoteBidForSeconds(putQuoteList);

        Map<Long, Double> callTradePriceMap = Maps.newHashMap();
        Map<Long, Double> putTradePriceMap = Maps.newHashMap();
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
        Double callBid = 0d;
        Double putBid = 0d;
        Long buySeconds = 0l;
        int sec = 60;
        Map<String, String> optionToFirstMap = GetAggregateImpliedVolatility.dateToFirstIvTimeMap.get(date);
        if (MapUtils.isNotEmpty(optionToFirstMap)) {
            String firstDatetime = optionToFirstMap.get(callCode);
            if (StringUtils.isBlank(firstDatetime)) {
                return "empty";
            }
            LocalDateTime firstTime = LocalDateTime.parse(firstDatetime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            LocalDateTime openTime = firstTime.withMinute(30).withSecond(0);
            sec = (int) ChronoUnit.SECONDS.between(openTime, firstTime);
        }
        //        int tradeSec = calCanTradeSeconds(stock, date, call.getSymbol(), put.getSymbol(), dayAllSeconds);
        //        if (sec < tradeSec) {
        //            sec = tradeSec;
        //        }
        if (sec < 0) {
            System.out.println("illegal firsttime. " + call);
            return "empty";
        }
        if (sec > 60) {
            return "empty";
        }
        Long openSeconds = Long.valueOf(dayAllSeconds.get(sec)) / 1000000000;
        if (callTradePriceMap.get(openSeconds) != 0) {
            callOpen = callTradePriceMap.get(openSeconds);
        }
        if (putTradePriceMap.get(openSeconds) != 0) {
            putOpen = putTradePriceMap.get(openSeconds);
        }

        callBid = MapUtils.getDouble(callBidTradePriceMap, openSeconds, 0d);
        putBid = MapUtils.getDouble(putBidTradePriceMap, openSeconds, 0d);

        if (callOpen != 0 && putOpen != 0) {
            buySeconds = openSeconds;
        }

        if (callOpen == 0 || putOpen == 0) {
            return "empty";
        }
        if (calCallOpen != 0 && calCallOpen < callOpen && calCallOpen >= callBid) {
            callOpen = calCallOpen;
        }
        if (calPutOpen != 0 && calPutOpen < putOpen && calPutOpen >= putBid) {
            putOpen = calPutOpen;
        }
        if (callOpen < 0.5 || putOpen < 0.5) {
            //            System.out.println(call + " " + put + " less 0.1");
            //                        return "empty";
        }
        if (callOpen + putOpen < 1) {
            //            return "empty";
        }
        //        callOpen = (callOpen * 100 + 1.3) / 100;
        //        putOpen = (putOpen * 100 + 1.3) / 100;
        //        callOpen = (callOpen * 100 + 2.284) / 100;
        //        putOpen = (putOpen * 100 + 2.284) / 100;
        boolean stopLoss = true;
        //        boolean stopLoss = false;
        List<String> list = Lists.newArrayList();
        String result = "";
        if (stopLoss) {
            int continueGain = 1;
            double continueRatio = 0d;
            for (int i = sec; i < dayAllSeconds.size() - 60; i++) {
                Long seconds = Long.valueOf(dayAllSeconds.get(i)) / 1000000000;
                Double callClose = callTradePriceMap.get(seconds);
                Double putClose = putTradePriceMap.get(seconds);
                callBid = callBidTradePriceMap.get(seconds);
                putBid = putBidTradePriceMap.get(seconds);

                if (callClose == 0 || putClose == 0) {
                    continue;
                }
                double open = BigDecimal.valueOf(putOpen + callOpen).setScale(5, RoundingMode.HALF_UP).doubleValue();
                double callDiff = BigDecimal.valueOf(callClose - callOpen).setScale(5, RoundingMode.HALF_UP).doubleValue();
                double putDiff = BigDecimal.valueOf(putClose - putOpen).setScale(5, RoundingMode.HALF_UP).doubleValue();
                double allDiff = BigDecimal.valueOf(callDiff + putDiff).setScale(5, RoundingMode.HALF_UP).doubleValue();
                double diffRatio = BigDecimal.valueOf(allDiff / (callOpen + putOpen) * 100).setScale(2, RoundingMode.HALF_UP).doubleValue();
                double gainDiffRatio;
                if (callDiff > putDiff) {
                    gainDiffRatio = BigDecimal.valueOf((callDiff + putBid - putOpen) / (callOpen + putOpen) * 100).setScale(2, RoundingMode.HALF_UP).doubleValue();
                } else {
                    gainDiffRatio = BigDecimal.valueOf((putDiff + callBid - callOpen) / (callOpen + putOpen) * 100).setScale(2, RoundingMode.HALF_UP).doubleValue();
                }

                String sellTime = LocalDateTime.ofEpochSecond(seconds, 0, ZoneOffset.of("+8")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                String buyTime = LocalDateTime.ofEpochSecond(buySeconds, 0, ZoneOffset.of("+8")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                //            list.add(result);
                //            System.out.println(result);
                if (diffRatio < -40) {
                    result = buyTime + "\t" + sellTime + "\t" + callOpen + "\t" + callClose + "\t" + putOpen + "\t" + putClose + "\t" + callDiff + "\t" + putDiff + "\t" + allDiff + "\t" + diffRatio;
                    return result;
                }
                if (i <= 10800 && i >= 120) {
                    if (gainDiffRatio >= 20) {
                        result = buyTime + "\t" + sellTime + "\t" + callOpen + "\t" + callClose + "\t" + putOpen + "\t" + putClose + "\t" + callDiff + "\t" + putDiff + "\t" + allDiff + "\t" + gainDiffRatio;
                        return result;
                    } else if (diffRatio < -20) {
                        result = buyTime + "\t" + sellTime + "\t" + callOpen + "\t" + callClose + "\t" + putOpen + "\t" + putClose + "\t" + callDiff + "\t" + putDiff + "\t" + allDiff + "\t" + diffRatio;
                        return result;
                    }
                } else if (i > 10800) {
                    if (diffRatio < -20) {
                        result = buyTime + "\t" + sellTime + "\t" + callOpen + "\t" + callClose + "\t" + putOpen + "\t" + putClose + "\t" + callDiff + "\t" + putDiff + "\t" + allDiff + "\t" + diffRatio;
                        return result;
                    }
                    if (gainDiffRatio > 10) {
                        result = buyTime + "\t" + sellTime + "\t" + callOpen + "\t" + callClose + "\t" + putOpen + "\t" + putClose + "\t" + callDiff + "\t" + putDiff + "\t" + allDiff + "\t" + gainDiffRatio;
                        return result;
                    }
                }
            }
        }
        //                System.out.println(result);
        //        BaseUtils.writeFile(USER_PATH + "optionData/trade/" + year + "/" + date + "_" + stock, list);
        Long sellSeconds = Long.valueOf(dayAllSeconds.get(dayAllSeconds.size() - 60)) / 1000000000;
        Double callClose = callTradePriceMap.get(sellSeconds);
        Double putClose = putTradePriceMap.get(sellSeconds);
        double callDiff = BigDecimal.valueOf(callClose - callOpen).setScale(2, RoundingMode.HALF_UP).doubleValue();
        double putDiff = BigDecimal.valueOf(putClose - putOpen).setScale(2, RoundingMode.HALF_UP).doubleValue();
        double allDiff = BigDecimal.valueOf(callDiff + putDiff).setScale(2, RoundingMode.HALF_UP).doubleValue();
        double diffRatio = BigDecimal.valueOf(allDiff / (callOpen + putOpen) * 100).setScale(2, RoundingMode.HALF_UP).doubleValue();
        String buyTime = LocalDateTime.ofEpochSecond(buySeconds, 0, ZoneOffset.of("+8")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String sellTime = LocalDateTime.ofEpochSecond(sellSeconds, 0, ZoneOffset.of("+8")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        result = buyTime + "\t" + sellTime + "\t" + callOpen + "\t" + callClose + "\t" + putOpen + "\t" + putClose + "\t" + callDiff + "\t" + putDiff + "\t" + allDiff + "\t" + diffRatio;

        return result;
    }

    public static NearlyOptionData calOpenStrikePrice(String date, String stock, double open) throws Exception {
        List<String> callAndPuts = getCallAndPuts(date, stock);
        if (CollectionUtils.isEmpty(callAndPuts)) {
            return null;
        }

        // 开盘价附近的call和put
        if (CollectionUtils.isEmpty(callAndPuts)) {
            //            System.out.println("there is no call and put for open price. stock=" + stock);
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
        String callOption = "";
        List<String> callList = Lists.newArrayList();
        int diff = Integer.MAX_VALUE;
        for (int i = 0; i < callAndPuts.size(); i++) {
            String callAndPut = callAndPuts.get(i);
            String code = callAndPut.split("\\|")[0];
            int strikePrice = Integer.parseInt(code.substring(code.length() - 8));
            callList.add(code);

            int tempDiff = strikePrice - digitalPrice;
            if (tempDiff > 0 && diff > tempDiff) {
                diff = tempDiff;
                callOption = code;
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
        String lower1 = "", higher1 = "";
        String lower2 = "", higher2 = "";
        String lower3 = "", higher3 = "";
        //        String lower4 = "", higher4 = "";
        for (int i = 2; i < callList.size() - 2; i++) {
            if (StringUtils.equalsIgnoreCase(callList.get(i), callOption)) {
                higher1 = callList.get(i - 1);
                higher2 = callList.get(i);
                higher3 = callList.get(i + 1);
                lower1 = callList.get(i);
                lower2 = callList.get(i - 1);
                lower3 = callList.get(i - 2);
            }
        }
        if (StringUtils.isAnyBlank(higher1, lower1, higher2, lower2, higher3, lower3)) {
            //            System.out.println("there is no higher1 and lower1 option to calculate option. stock=" + stock);
            return null;
        }

        String put1 = BaseUtils.getOptionPutCode(lower1);
        String put2 = BaseUtils.getOptionPutCode(lower2);
        String put3 = BaseUtils.getOptionPutCode(lower3);

        NearlyOptionData nearlyOptionData = new NearlyOptionData();
        nearlyOptionData.setOpenPrice(open);
        nearlyOptionData.setDate(date);
        nearlyOptionData.setStock(stock);
        nearlyOptionData.setOutPriceCallOptionCode_1(higher1);
        nearlyOptionData.setOutPriceCallOptionCode_2(higher2);
        nearlyOptionData.setOutPriceCallOptionCode_3(higher3);
        nearlyOptionData.setOutPricePutOptionCode_1(put1);
        nearlyOptionData.setOutPricePutOptionCode_2(put2);
        nearlyOptionData.setOutPricePutOptionCode_3(put3);

        return nearlyOptionData;
    }

    public static String calMidDelta(String call1, String call2, String call3, String put1, String put2, String put3, String date) throws Exception {
        Map<String, List<AggregateOptionIV>> map = GetAggregateImpliedVolatility.dateToGreekListMap.get(date);
        List<AggregateOptionIV> callGreekList1 = null;
        List<AggregateOptionIV> callGreekList2 = null;
        List<AggregateOptionIV> callGreekList3 = null;
        List<AggregateOptionIV> putGreekList1 = null;
        List<AggregateOptionIV> putGreekList2 = null;
        List<AggregateOptionIV> putGreekList3 = null;
        if (MapUtils.isEmpty(map)) {
            callGreekList1 = GetAggregateImpliedVolatility.getAggregateGreekList(call1, date);
            callGreekList2 = GetAggregateImpliedVolatility.getAggregateGreekList(call2, date);
            callGreekList3 = GetAggregateImpliedVolatility.getAggregateGreekList(call3, date);
            putGreekList1 = GetAggregateImpliedVolatility.getAggregateGreekList(put1, date);
            putGreekList2 = GetAggregateImpliedVolatility.getAggregateGreekList(put2, date);
            putGreekList3 = GetAggregateImpliedVolatility.getAggregateGreekList(put3, date);
        } else {
            if (!map.containsKey(call1)) {
                callGreekList1 = GetAggregateImpliedVolatility.getAggregateGreekList(call1, date);
            }
            if (!map.containsKey(call2)) {
                callGreekList2 = GetAggregateImpliedVolatility.getAggregateGreekList(call2, date);
            }
            if (!map.containsKey(call3)) {
                callGreekList3 = GetAggregateImpliedVolatility.getAggregateGreekList(call3, date);
            }
            if (!map.containsKey(put1)) {
                putGreekList1 = GetAggregateImpliedVolatility.getAggregateGreekList(put1, date);
            }
            if (!map.containsKey(put2)) {
                putGreekList2 = GetAggregateImpliedVolatility.getAggregateGreekList(put2, date);
            }
            if (!map.containsKey(put3)) {
                putGreekList3 = GetAggregateImpliedVolatility.getAggregateGreekList(put3, date);
            }
        }

        if (CollectionUtils.isEmpty(callGreekList1) ||
          CollectionUtils.isEmpty(callGreekList2) ||
          CollectionUtils.isEmpty(callGreekList3) ||
          CollectionUtils.isEmpty(putGreekList1) ||
          CollectionUtils.isEmpty(putGreekList2) ||
          CollectionUtils.isEmpty(putGreekList3)
        ) {
            System.out.println(call1 + " greek data is null");
            return null;
        }

        AggregateOptionIV callGreek1 = callGreekList1.get(0);
        AggregateOptionIV callGreek2 = callGreekList2.get(0);
        AggregateOptionIV callGreek3 = callGreekList3.get(0);
        AggregateOptionIV putGreek1 = putGreekList1.get(0);
        AggregateOptionIV putGreek2 = putGreekList2.get(0);
        AggregateOptionIV putGreek3 = putGreekList3.get(0);

        List<AggregateOptionIV> callGreekList = Lists.newArrayList(callGreek1, callGreek2, callGreek3);
        List<AggregateOptionIV> putGreekList = Lists.newArrayList(putGreek1, putGreek2, putGreek3);

        double sum = Double.MAX_VALUE;
        String result = "";
        for (AggregateOptionIV callGreek : callGreekList) {
            for (AggregateOptionIV putGreek : putGreekList) {
                double temp = callGreek.getOptionDelta() + putGreek.getOptionDelta();
                if (sum > temp) {
                    sum = temp;
                    result = callGreek.getOptionSymbol() + "|" + putGreek.getOptionSymbol();
                }
            }
        }
        return result;
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

        //                Map<String, List<String>> earningForEveryDay = getEarningForEveryDay2024();
        Map<String, List<String>> earningForEveryDay = getEarningForEveryDay();
        Map<String, List<String>> earningForToday = getEarningForToday();
        Map<String, Map<String, StockKLine>> dateToStockKlineMap = getDateToStockKlineMap();
        //        Map<String, List<NearlyOptionData>> dateToOpenStrikePriceRatioMap = Strategy32.calOpenStrikePriceRatioMap();
        Map<String, Map<String, Double>> secToStockPriceMap = getSecToStockPriceMap();
        //        Map<String, List<Double>> ivMap = loadIvMap();

        for (String date : dateToStockKlineMap.keySet()) {
            Map<String, Double> lastCloseMap = dateToLastClose.get(date);
            String expirationDate = "";
            if (weekSet.contains(date)) {
                //                continue;
                int i = 0;
                for (; i < weekStrArray.length; i++) {
                    if (StringUtils.equals(date, weekStrArray[i])) {
                        i++;
                        break;
                    }
                }
                if (i >= weekStrArray.length) {
                    continue;
                }
                expirationDate = weekStrArray[i];
                expirationDate = LocalDate.parse(expirationDate, DB_DATE_FORMATTER).format(DateTimeFormatter.ofPattern("yyMMdd"));
            }

            LocalDate localDate = LocalDate.parse(date, DB_DATE_FORMATTER);
            if (localDate.isAfter(LocalDate.parse("2024-10-08", DB_DATE_FORMATTER))) {
                continue;
            }
            if (!date.equals("2024-10-08")) {
                //                                continue;
            }

            List<String> earningStocks = earningForEveryDay.get(date);
            List<String> earningTodayStocks = earningForToday.get(date);
            Map<String, StockKLine> stockKLineMap = dateToStockKlineMap.get(date);
            if (CollectionUtils.isEmpty(earningStocks)) {
                //                continue;
            }
            //            List<NearlyOptionData> nearlyOptionDataList = dateToOpenStrikePriceRatioMap.get(BaseUtils.unformatDate(date));
            //            Map<String, NearlyOptionData> stockToNearlyOption = nearlyOptionDataList.stream().collect(Collectors.toMap(NearlyOptionData::getStock, v -> v));
            //            Set<String> stockSet = earningStocks.stream().collect(Collectors.toSet());
            Set<String> stockSet = stockKLineMap.keySet();
            for (String stock : stockSet) {
                if (!stock.equals("ZIM")) {
                    //                    continue;
                }
                // 财报日不交易收益不符合预期，所以不限制
                if (CollectionUtils.isNotEmpty(earningTodayStocks) && earningTodayStocks.contains(stock)) {
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
                    String outPriceCallOptionCode_2 = nearlyOptionData.getOutPriceCallOptionCode_2();
                    String outPriceCallOptionCode_3 = nearlyOptionData.getOutPriceCallOptionCode_3();
                    String outPricePutOptionCode_1 = nearlyOptionData.getOutPricePutOptionCode_1();
                    String outPricePutOptionCode_2 = nearlyOptionData.getOutPricePutOptionCode_2();
                    String outPricePutOptionCode_3 = nearlyOptionData.getOutPricePutOptionCode_3();
                    if (StringUtils.isNotBlank(expirationDate)) {
                        StringBuffer callSb1 = new StringBuffer(outPriceCallOptionCode_1);
                        callSb1.replace(callSb1.length() - 15, callSb1.length() - 9, expirationDate);
                        outPriceCallOptionCode_1 = callSb1.toString();
                        StringBuffer callSb2 = new StringBuffer(outPriceCallOptionCode_2);
                        callSb2.replace(callSb2.length() - 15, callSb2.length() - 9, expirationDate);
                        outPriceCallOptionCode_2 = callSb2.toString();
                        StringBuffer callSb3 = new StringBuffer(outPriceCallOptionCode_3);
                        callSb3.replace(callSb3.length() - 15, callSb3.length() - 9, expirationDate);
                        outPriceCallOptionCode_3 = callSb3.toString();
                        StringBuffer putSb1 = new StringBuffer(outPricePutOptionCode_1);
                        putSb1.replace(putSb1.length() - 15, putSb1.length() - 9, expirationDate);
                        outPricePutOptionCode_1 = putSb1.toString();
                        StringBuffer putSb2 = new StringBuffer(outPricePutOptionCode_2);
                        putSb2.replace(putSb2.length() - 15, putSb2.length() - 9, expirationDate);
                        outPricePutOptionCode_2 = putSb2.toString();
                        StringBuffer putSb3 = new StringBuffer(outPricePutOptionCode_3);
                        putSb3.replace(putSb3.length() - 15, putSb3.length() - 9, expirationDate);
                        outPricePutOptionCode_3 = putSb3.toString();
                    }
                    //                    if (!StringUtils.equals(outPriceCallOptionCode_1, nearlyOptionData2.getOutPriceCallOptionCode_1()) || !StringUtils.equals(outPricePutOptionCode_1, nearlyOptionData2.getOutPricePutOptionCode_1())) {
                    //                        System.out.println();
                    //                    }

                    OptionDaily callDaily1 = requestOptionDaily(outPriceCallOptionCode_1, formatDate);
                    OptionDaily callDaily2 = requestOptionDaily(outPriceCallOptionCode_2, formatDate);
                    OptionDaily callDaily3 = requestOptionDaily(outPriceCallOptionCode_3, formatDate);
                    OptionDaily putDaily1 = requestOptionDaily(outPricePutOptionCode_1, formatDate);
                    OptionDaily putDaily2 = requestOptionDaily(outPricePutOptionCode_2, formatDate);
                    OptionDaily putDaily3 = requestOptionDaily(outPricePutOptionCode_3, formatDate);
                    if (callDaily1 == null || putDaily1 == null || callDaily2 == null || putDaily2 == null || callDaily3 == null || putDaily3 == null) {
                        System.out.println(stock + " " + date + " option daily is null");
                        continue;
                    }
                    // 过滤前日call和put交易量小于100
                    String lastDate = Strategy32.dateMap.get(formatDate);
                    if (StringUtils.isBlank(lastDate)) {
                        continue;
                    }

                    double callOpen1 = callDaily1.getOpen();
                    double callOpen2 = callDaily2.getOpen();
                    double callOpen3 = callDaily3.getOpen();
                    double putOpen1 = putDaily1.getOpen();
                    double putOpen2 = putDaily2.getOpen();
                    double putOpen3 = putDaily3.getOpen();
                    if (callOpen1 == 0 || putOpen1 == 0 || callOpen2 == 0 || putOpen2 == 0 || callOpen3 == 0 || putOpen3 == 0) {
                        continue;
                    }

                    String deltaResult = calMidDelta(
                      outPriceCallOptionCode_1.substring(2),
                      outPriceCallOptionCode_2.substring(2),
                      outPriceCallOptionCode_3.substring(2),
                      outPricePutOptionCode_1.substring(2),
                      outPricePutOptionCode_2.substring(2),
                      outPricePutOptionCode_3.substring(2),
                      date
                    );

                    if (StringUtils.isBlank(deltaResult)) {
                        continue;
                    }
                    String[] deltaSplit = deltaResult.split("\\|");
                    String call = "O:" + deltaSplit[0];
                    String put = "O:" + deltaSplit[1];

                    OptionDaily last_call_1 = requestOptionDaily(call, lastDate);
                    OptionDaily last_put_1 = requestOptionDaily(put, lastDate);
                    if (last_call_1 == null || last_put_1 == null || (last_call_1.getVolume() < 100 || last_put_1.getVolume() < 100)) {
                        continue;
                    }
                    long totalLastVolume = last_call_1.getVolume() + last_put_1.getVolume();
                    double totalLastClose = BigDecimal.valueOf(last_call_1.getClose() + last_put_1.getClose()).setScale(2, RoundingMode.HALF_UP).doubleValue();
                    if (totalLastClose <= 0.5) {
                        continue;
                    }

                    List<Double> callIvList = getIvList(call, date);
                    List<Double> putIvList = getIvList(put, date);
                    boolean callCanTrade = Strategy32.canTradeForIv(callIvList);
                    boolean putCanTrade = Strategy32.canTradeForIv(putIvList);
                    if (!callCanTrade || !putCanTrade) {
                        continue;
                    }

                    // 计算理论call和put的买入价
                    Double calCallOpen = calOpen(secToStockPriceMap, call, date);
                    Double calPutOpen = calOpen(secToStockPriceMap, put, date);

                    String simulateTrade = "";
                    String ivInfo = "";
                    simulateTrade = calStraddleSimulateTrade(callDaily1, putDaily1, calCallOpen, calPutOpen);
                    if (StringUtils.equalsAnyIgnoreCase(simulateTrade, "noData", "empty")) {
                        continue;
                    }
                    String callIvInfo = StringUtils.join(Lists.reverse(callIvList.subList(0, 3)), "\t");
                    String putIvInfo = StringUtils.join(Lists.reverse(putIvList.subList(0, 3)), "\t");
                    ivInfo = callIvInfo + "\t" + putIvInfo;
                    System.out.println(stock + "\t" + open + "\t" + ratioStr + "\t" + totalLastVolume + "\t" + totalLastClose + "\t" + call + "\t" + put + "\t" + ivInfo + "\t" + simulateTrade);
                }
            }
        }
    }
}
