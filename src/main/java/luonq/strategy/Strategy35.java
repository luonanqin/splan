package luonq.strategy;

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

import static util.Constants.*;

/**
 * 宽跨式+价差保护策略
 */
public class Strategy35 {

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

    public static NearlyOptionData calOpenStrikePrice(String date, String stock, double open) throws Exception {
        List<String> callAndPuts = Strategy32.getCallAndPuts(date, stock);
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

    public static String calStraddleSimulateTrade(OptionDaily call, OptionDaily put, Double calCallOpen, Double calPutOpen, String call2Symbol, String put2Symbol) throws Exception {
        String date = call.getFrom();

        String callSymbol = call.getSymbol();
        String putSymbol = put.getSymbol();
        String callCode = callSymbol.substring(2);
        String putCode = putSymbol.substring(2);

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

        List<String> list = Lists.newArrayList();
        String result = "";
        int continueGain = 1;
        double continueRatio = 0d;
        double call2Open = 0d, put2Open = 0d;
        for (int i = sec; i < dayAllSeconds.size() - 60; i++) {
            Long seconds = Long.valueOf(dayAllSeconds.get(i)) / 1000000000;
            Double callClose = callTradePriceMap.get(seconds);
            Double putClose = putTradePriceMap.get(seconds);
            Double call2Close = call2TradePriceMap.get(seconds);
            Double put2Close = put2TradePriceMap.get(seconds);
            if (callClose == 0 || putClose == 0) {
                continue;
            }
            double callDiff = BigDecimal.valueOf(callClose - callOpen).setScale(5, RoundingMode.HALF_UP).doubleValue();
            double putDiff = BigDecimal.valueOf(putClose - putOpen).setScale(5, RoundingMode.HALF_UP).doubleValue();
            double allDiff = BigDecimal.valueOf(callDiff + putDiff).setScale(5, RoundingMode.HALF_UP).doubleValue();
            double callDiffRatio = BigDecimal.valueOf(callDiff / callOpen * 100).setScale(2, RoundingMode.HALF_UP).doubleValue();
            double putDiffRatio = BigDecimal.valueOf(putDiff / putOpen * 100).setScale(2, RoundingMode.HALF_UP).doubleValue();
            double diffRatio = BigDecimal.valueOf(allDiff / (callOpen + putOpen) * 100).setScale(2, RoundingMode.HALF_UP).doubleValue();
            if (callDiffRatio > 20 && call2Open == 0d) {
                call2Open = call2Close;
            }
            if (putDiffRatio > 20 && put2Open == 0d) {
                put2Open = put2Close;
            }
            double call2Diff = call2Open == 0d ? 0 : BigDecimal.valueOf(call2Open - call2Close).setScale(5, RoundingMode.HALF_UP).doubleValue();
            double put2Diff = put2Open == 0d ? 0 : BigDecimal.valueOf(put2Open - put2Close).setScale(5, RoundingMode.HALF_UP).doubleValue();
            double all2Diff = BigDecimal.valueOf(call2Diff + put2Diff).setScale(5, RoundingMode.HALF_UP).doubleValue();
            double totalDiff = allDiff;
            if (all2Diff != 0) {
                totalDiff += all2Diff;
            }
            double allDiffRatio = BigDecimal.valueOf(totalDiff / (callOpen + putOpen) * 100).setScale(2, RoundingMode.HALF_UP).doubleValue();

            String sellTime = LocalDateTime.ofEpochSecond(seconds, 0, ZoneOffset.of("+8")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String buyTime = LocalDateTime.ofEpochSecond(buySeconds, 0, ZoneOffset.of("+8")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            //            list.add(result);
            //            System.out.println(result);
            if (allDiffRatio < -40) {
                result = buyTime + "\t" + sellTime + "\t" + callOpen + "\t" + callClose + "\t" + putOpen + "\t" + putClose + "\t" + call2Open + "\t" + call2Close + "\t" + put2Open + "\t" + put2Close + "\t" + all2Diff + "\t" + totalDiff + "\t" + allDiffRatio;
                return result;
            }
            if (i <= 10800 && i >= 120) {
                if (allDiffRatio >= 20) {
                    result = buyTime + "\t" + sellTime + "\t" + callOpen + "\t" + callClose + "\t" + putOpen + "\t" + putClose + "\t" + call2Open + "\t" + call2Close + "\t" + put2Open + "\t" + put2Close + "\t" + all2Diff + "\t" + totalDiff + "\t" + allDiffRatio;
                    return result;
                } else if (allDiffRatio < -20) {
                    result = buyTime + "\t" + sellTime + "\t" + callOpen + "\t" + callClose + "\t" + putOpen + "\t" + putClose + "\t" + call2Open + "\t" + call2Close + "\t" + put2Open + "\t" + put2Close + "\t" + all2Diff + "\t" + totalDiff + "\t" + allDiffRatio;
                    return result;
                }
            } else if (i > 10800) {
                if (allDiffRatio < -20 || allDiffRatio > 20) {
                    result = buyTime + "\t" + sellTime + "\t" + callOpen + "\t" + callClose + "\t" + putOpen + "\t" + putClose + "\t" + call2Open + "\t" + call2Close + "\t" + put2Open + "\t" + put2Close + "\t" + all2Diff + "\t" + totalDiff + "\t" + allDiffRatio;
                    return result;
                }
            }
        }
        //                System.out.println(result);
        //        BaseUtils.writeFile(USER_PATH + "optionData/trade/" + year + "/" + date + "_" + stock, list);
        Long sellSeconds = Long.valueOf(dayAllSeconds.get(dayAllSeconds.size() - 60)) / 1000000000;
        Double callClose = callTradePriceMap.get(sellSeconds);
        Double putClose = putTradePriceMap.get(sellSeconds);
        Double call2Close = call2TradePriceMap.get(sellSeconds);
        Double put2Close = put2TradePriceMap.get(sellSeconds);
        double callDiff = BigDecimal.valueOf(callClose - callOpen).setScale(2, RoundingMode.HALF_UP).doubleValue();
        double putDiff = BigDecimal.valueOf(putClose - putOpen).setScale(2, RoundingMode.HALF_UP).doubleValue();
        double allDiff = BigDecimal.valueOf(callDiff + putDiff).setScale(2, RoundingMode.HALF_UP).doubleValue();
        double diffRatio = BigDecimal.valueOf(allDiff / (callOpen + putOpen) * 100).setScale(2, RoundingMode.HALF_UP).doubleValue();
        String buyTime = LocalDateTime.ofEpochSecond(buySeconds, 0, ZoneOffset.of("+8")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String sellTime = LocalDateTime.ofEpochSecond(sellSeconds, 0, ZoneOffset.of("+8")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        double call2Diff = call2Open == 0d ? 0 : BigDecimal.valueOf(call2Open - call2Close).setScale(5, RoundingMode.HALF_UP).doubleValue();
        double put2Diff = put2Open == 0d ? 0 : BigDecimal.valueOf(put2Open - put2Close).setScale(5, RoundingMode.HALF_UP).doubleValue();
        double all2Diff = BigDecimal.valueOf(call2Diff + put2Diff).setScale(5, RoundingMode.HALF_UP).doubleValue();
        double totalDiff = allDiff;
        if (all2Diff != 0) {
            totalDiff += all2Diff;
        }
        double allDiffRatio = BigDecimal.valueOf(totalDiff / (callOpen + putOpen) * 100).setScale(2, RoundingMode.HALF_UP).doubleValue();
        result = buyTime + "\t" + sellTime + "\t" + callOpen + "\t" + callClose + "\t" + putOpen + "\t" + putClose + "\t" + call2Open + "\t" + call2Close + "\t" + put2Open + "\t" + put2Close + "\t" + all2Diff + "\t" + totalDiff + "\t" + allDiffRatio;

        return result;
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

            if (!date.equals("2024-01-10")) {
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
                if (!stock.equals("KSS")) {
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
                    String outPricePutOptionCode_1 = nearlyOptionData.getOutPricePutOptionCode_1();
                    String outPriceCallOptionCode_2 = nearlyOptionData.getOutPriceCallOptionCode_2();
                    String outPricePutOptionCode_2 = nearlyOptionData.getOutPricePutOptionCode_2();
                    if (StringUtils.isNotBlank(expirationDate)) {
                        StringBuffer callSb = new StringBuffer(outPriceCallOptionCode_1);
                        callSb.replace(callSb.length() - 15, callSb.length() - 9, expirationDate);
                        outPriceCallOptionCode_1 = callSb.toString();
                        StringBuffer putSb = new StringBuffer(outPricePutOptionCode_1);
                        putSb.replace(putSb.length() - 15, putSb.length() - 9, expirationDate);
                        outPricePutOptionCode_1 = putSb.toString();

                        StringBuffer call2Sb = new StringBuffer(outPriceCallOptionCode_2);
                        call2Sb.replace(call2Sb.length() - 15, call2Sb.length() - 9, expirationDate);
                        outPriceCallOptionCode_2 = call2Sb.toString();
                        StringBuffer put2Sb = new StringBuffer(outPricePutOptionCode_2);
                        put2Sb.replace(put2Sb.length() - 15, put2Sb.length() - 9, expirationDate);
                        outPricePutOptionCode_2 = put2Sb.toString();
                    }
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
                        //                    if (callOpen == 0 || putOpen == 0 || callOpen < 0.1 || putOpen < 0.1) {
                        //                        System.out.println(stock + " " + date + " option open is 0");
                        continue;
                    }
                    double callDiff = BigDecimal.valueOf(callDaily.getClose() - callOpen).setScale(2, RoundingMode.HALF_UP).doubleValue();
                    double putDiff = BigDecimal.valueOf(putDaily.getClose() - putOpen).setScale(2, RoundingMode.HALF_UP).doubleValue();
                    double allDiff = BigDecimal.valueOf(callDiff + putDiff).setScale(2, RoundingMode.HALF_UP).doubleValue();
                    double diffRatio = BigDecimal.valueOf(allDiff / (callOpen + putOpen) * 100).setScale(2, RoundingMode.HALF_UP).doubleValue();

                    List<Double> callIvList = getIvList(outPriceCallOptionCode_1, date);
                    List<Double> putIvList = getIvList(outPricePutOptionCode_1, date);
                    boolean callCanTrade = Strategy32.canTradeForIv(callIvList);
                    boolean putCanTrade = Strategy32.canTradeForIv(putIvList);
                    if (!callCanTrade || !putCanTrade) {
                        continue;
                    }

                    // 计算理论call和put的买入价
                    Double calCallOpen = calOpen(secToStockPriceMap, outPriceCallOptionCode_1, date);
                    Double calPutOpen = calOpen(secToStockPriceMap, outPricePutOptionCode_1, date);
                    //                    Double calCallOpen = calOpen(secToStockPriceMap, outPriceCallOptionCode_1, date, seconds);
                    //                    Double calPutOpen = calOpen(secToStockPriceMap, outPricePutOptionCode_1, date, seconds);

                    String simulateTrade = "";
                    String ivInfo = "";
                    simulateTrade = calStraddleSimulateTrade(callDaily, putDaily, calCallOpen, calPutOpen, outPriceCallOptionCode_2, outPricePutOptionCode_2);
                    if (StringUtils.equalsAnyIgnoreCase(simulateTrade, "noData", "empty")) {
                        continue;
                    }
                    String callIvInfo = StringUtils.join(Lists.reverse(callIvList.subList(0, 3)), "\t");
                    String putIvInfo = StringUtils.join(Lists.reverse(putIvList.subList(0, 3)), "\t");
                    ivInfo = callIvInfo + "\t" + putIvInfo;
                    System.out.println(stock + "\t" + open + "\t" + ratioStr + "\t" + totalLastVolume + "\t" + date + "\t" + ivInfo + "\t" + simulateTrade);
                }
            }
        }
    }
}
