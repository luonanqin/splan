package luonq.strategy;

import bean.BOLL;
import bean.NearlyOptionData;
import bean.OptionCode;
import bean.OptionContracts;
import bean.OptionContractsResp;
import bean.OptionDaily;
import bean.StockKLine;
import bean.StraddleOption;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import luonq.ivolatility.GetAggregateImpliedVolatility;
import luonq.polygon.GetOptionTrade;
import org.apache.commons.collections.list.SynchronizedList;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.LoggerFactory;
import util.BaseUtils;

import java.io.IOException;
import java.io.InputStream;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static util.Constants.*;

/**
 * 预期:
 * 1.以05-23号的收盘价作为输入，获取24号的期权代码
 * 2.以每天的开盘价找到离开盘价最近的行权价call，以及行权价之间的价格步长。每个股票一个文件
 * 3.计算每值股票每天的开盘价和最近期权行权价的差值，从小到大排序
 * 4.从差值最小的开始，计算对应期权代码前后两个行权价的代码
 * <p>
 * 策略1：
 * 0.开盘价为某档期权价，方便双开中性
 * 1.双开价外一档期权（无保护），且前日交易数量大于100张
 * 2.股票当天开盘和前日收盘差值比例，即abs(open-lastClose)/lastClose要大于2，即开盘和前日收盘差距越大，波动概率越高
 * 3.双开的call和put开盘差值比例，即abs(callopen/putopen-1)要小于10，即双开期权尽量中性
 * 测试结果1：2024年1月-5月23日，交易10次，成本1w，收益约2.3w，按实际报价计算并按iv规则过滤收益约2w
 * 测试结果2：2023年1月-5月24日，交易17次，成本1w，收益约7.2w，按实际报价计算并按iv规则过滤收益约14w
 * 测试结果3：2022年1月-5月23日，交易24次，成本1w，收益约3.8k，按实际报价计算并按iv规则过滤收益约5k
 */
public class Strategy32 {

    public static BlockingQueue<CloseableHttpClient> queue;
    public static ThreadPoolExecutor cachedThread;
    public static String apiKey = "&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY";
    public static Map<String/* stock */, Map<String/* date */, Map<String/* optionCode */, OptionDaily>>> stockOptionDailyMap = Maps.newHashMap();
    public static Map<String/* stock */, Map<Integer/* 档位 */, Double/* 振幅均值 */>> stockToVolatilityMap = Maps.newHashMap();
    public static Map<String/* date */, String/* lastDate */> dateMap = Maps.newHashMap(); // 当日和前日的映射

    public static void init() throws Exception {
        int threadCount = 10;
        int corePoolSize = threadCount;
        int maximumPoolSize = corePoolSize;
        long keepAliveTime = 60L;
        LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
        cachedThread = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS, workQueue);
        queue = new LinkedBlockingQueue<>(threadCount);
        for (int i = 0; i < threadCount; i++) {
            queue.offer(HttpClients.createDefault());
        }

        List<StockKLine> stockKLines = BaseUtils.loadDataToKline(HIS_BASE_PATH + "merge/AAPL", year, year - 2);
        List<String> dateList = stockKLines.stream().map(StockKLine::getFormatDate).collect(Collectors.toList());
        for (int i = 0; i < dateList.size() - 2; i++) {
            dateMap.put(dateList.get(i), dateList.get(i + 1));
        }
    }

    public static List<String> getOptionCode(CloseableHttpClient httpClient, String code, double price, String date) throws Exception {
        int decade = (int) price / 10;
        int upPrice = (decade + 1) * 10;
        int downPrice = (decade == 0 ? 1 : decade) * 10;

        LocalDate today = LocalDate.now();
        LocalDate day = LocalDate.parse(date, DB_DATE_FORMATTER);
        String upDate = day.plusMonths(2).withDayOfMonth(1).format(DB_DATE_FORMATTER);

        boolean expired = true;
        if (today.isBefore(day.plusDays(30))) {
            expired = false;
            upDate = day.plusMonths(1).withDayOfMonth(1).format(DB_DATE_FORMATTER);
        }
        String url = String.format("https://api.polygon.io/v3/reference/options/contracts?contract_type=call&"
          + "underlying_ticker=%s&expired=%s&order=desc&limit=100&sort=expiration_date&expiration_date.lte=%s&expiration_date.gt=%s&strike_price.lte=%d&stike_price.gte=%d"
          + "&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY", code, true, upDate, date, upPrice, downPrice);

        //        System.out.println(url);

        return requestOptionCodeList(httpClient, url);
    }

    public static List<String> getNearlyOptionCode(CloseableHttpClient httpClient, String code, double price, String date) throws Exception {
        int decade = (int) price / 10;
        int upPrice = (decade + 1) * 10;
        int downPrice = (decade == 0 ? 1 : decade) * 10;

        String url = String.format("https://api.polygon.io/v3/reference/options/contracts?contract_type=call&"
          + "underlying_ticker=%s&expired=true&order=desc&limit=100&sort=expiration_date&expiration_date=%s&strike_price.lte=%d&stike_price.gte=%d"
          + "&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY", code, date, upPrice, downPrice);

        return requestOptionCodeList(httpClient, url);
    }

    private static List<String> requestOptionCodeList(CloseableHttpClient httpClient, String url) throws IOException {
        HttpGet get = new HttpGet(url);
        try {
            CloseableHttpResponse execute = httpClient.execute(get);
            InputStream stream = execute.getEntity().getContent();
            OptionContractsResp resp = JSON.parseObject(stream, OptionContractsResp.class);
            String status = resp.getStatus();
            if (!StringUtils.equalsIgnoreCase(status, "OK")) {
                System.out.println("get failed. " + url);
                return Lists.newArrayListWithExpectedSize(0);
            }

            List<OptionContracts> results = resp.getResults();
            List<String> tickerList = results.stream().map(OptionContracts::getTicker).collect(Collectors.toList());
            //            System.out.println(tickerList);
            if (CollectionUtils.isEmpty(tickerList)) {
                return Lists.newArrayListWithExpectedSize(0);
            }

            String latestTicker = tickerList.get(tickerList.size() - 1);
            int i;
            for (i = tickerList.size() - 2; i >= 0; i--) {
                String ticker = tickerList.get(i);
                if (!StringUtils.equalsIgnoreCase(latestTicker.substring(0, latestTicker.length() - 8), ticker.substring(0, ticker.length() - 8))) {
                    break;
                }
            }
            tickerList = tickerList.subList(i + 1, tickerList.size());

            return tickerList;
        } finally {
            get.releaseConnection();
        }
    }

    public static OptionCode getOptionCodeBean(List<String> optionCodeList, double price) {
        // 找出当前价前后的行权价及等于当前价的行权价
        int decade = (int) price;
        int count = String.valueOf(decade).length();

        int standardCount = count + 3;
        String priceStr = String.valueOf(price).replace(".", "");
        int lastCount = standardCount - priceStr.length();
        int digitalPrice = Integer.valueOf(priceStr) * (int) Math.pow(10, lastCount);

        String upOptionCode = null, downOptionCode = null, equalOptionCode = null;
        int priceDiff = Integer.MAX_VALUE;
        String optionCode = null;
        int nextStrikePrice = 0, actualStrikePrice = 0;
        for (int j = 0; j < optionCodeList.size(); j++) {
            String code = optionCodeList.get(j);
            int index = code.length() - 8;
            String temp = code.substring(index);
            int i;
            for (i = 0; i < temp.length(); i++) {
                if (temp.charAt(i) != '0') {
                    break;
                }
            }
            int strikePrice = Integer.parseInt(temp.substring(i));

            int tempDiff = Math.abs(strikePrice - digitalPrice);
            //            if (strikePrice >= digitalPrice) {
            if (priceDiff > tempDiff) {
                priceDiff = tempDiff;
                actualStrikePrice = strikePrice;
                if (j + 1 == optionCodeList.size()) {
                    return null;
                }
                nextStrikePrice = Integer.parseInt(optionCodeList.get(j + 1).substring(index).substring(i));
                optionCode = code;
            } else {
                break;
            }
        }

        if (StringUtils.isEmpty(optionCode)) {
            return null;
        }

        OptionCode optionCodeBean = new OptionCode();
        optionCodeBean.setCode(optionCode);
        optionCodeBean.setContractType("call");
        optionCodeBean.setStrikePrice(price);
        optionCodeBean.setActualDigitalStrikePrice(actualStrikePrice);
        optionCodeBean.setNextDigitalStrikePrice(nextStrikePrice);
        optionCodeBean.setDigitalStrikePrice(digitalPrice);
        return optionCodeBean;
    }

    private static void getHasWeekOptionStock() throws Exception {
        Map<String, String> klineFileMap = BaseUtils.getFileMap(HIS_BASE_PATH + "2024/dailyKLine");
        int year = 2024;
        Set<String> optionStock = BaseUtils.getOptionStock();
        for (String stock : optionStock) {
            if (!stock.equals("AAPL")) {
                //                continue;
            }
            String filePath = klineFileMap.get(stock);

            List<StockKLine> stockKLines = BaseUtils.loadDataToKline(filePath, year, year - 1);
            if (CollectionUtils.isEmpty(stockKLines)) {
                continue;
            }
            StockKLine stockKLine = stockKLines.get(0);
            String date = stockKLine.getFormatDate();
            if (!date.equals("2024-05-23")) {
                continue;
            }

            double close = stockKLine.getClose();

            CloseableHttpClient httpClient = queue.take();
            cachedThread.execute(() -> {
                try {
                    List<String> optionCodeList = getOptionCode(httpClient, stock, close, date);
                    for (String optionCode : optionCodeList) {
                        if (optionCode.contains("240524")) {
                            System.out.println(stock + "\t" + optionCode);
                            break;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    queue.offer(httpClient);
                }
            });
        }
    }

    public static void getEqualsStrikePriceKline() throws Exception {
        //        Map<String, String> klineFileMap = BaseUtils.getFileMap(HIS_BASE_PATH + "2024/dailyKLine");
        //        int year = 2024;
        Map<String, String> klineFileMap = BaseUtils.getFileMap(HIS_BASE_PATH + "merge");
        int year = 2022;
        Set<String> optionStock = BaseUtils.getWeekOptionStock();
        for (String stock : optionStock) {
            if (!stock.equals("AAPL")) {
                //                continue;
            }
            String filePath = klineFileMap.get(stock);

            List<StockKLine> stockKLines = BaseUtils.loadDataToKline(filePath, year, year - 1);
            if (CollectionUtils.isEmpty(stockKLines)) {
                continue;
            }
            List<String> result = SynchronizedList.decorate(Lists.newArrayList());
            CountDownLatch cdl = new CountDownLatch(stockKLines.size());
            for (StockKLine stockKLine : stockKLines) {
                String date = stockKLine.getDate();
                double open = stockKLine.getOpen();
                int dateInt = BaseUtils.dateToInt(date);
                int weekIndex = binarySearch(weekArray, dateInt);
                String strikeDate = weekStrArray[weekIndex];

                CloseableHttpClient httpClient = queue.take();
                cachedThread.execute(() -> {
                    try {
                        List<String> optionCodeList = getNearlyOptionCode(httpClient, stock, open, strikeDate);
                        OptionCode optionCodeBean = getOptionCodeBean(optionCodeList, open);
                        if (optionCodeBean == null) {
                            return;
                        }
                        int strikePriceStep = Math.abs(optionCodeBean.getNextDigitalStrikePrice() - optionCodeBean.getActualDigitalStrikePrice());
                        result.add(date + "\t" + open + "\t" + optionCodeBean.getCode() + "\t" + strikePriceStep);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        queue.offer(httpClient);
                        cdl.countDown();
                    }
                });
            }
            cdl.await();
            if (CollectionUtils.isNotEmpty(result)) {
                result.sort((o1, o2) -> {
                    String date1 = o1.split("\t")[0];
                    String date2 = o2.split("\t")[0];

                    return BaseUtils.dateToInt(date1).compareTo(BaseUtils.dateToInt(date2));
                });
            }

            BaseUtils.writeFile(USER_PATH + "optionData/nearlyOpenOption2022/" + stock, result);
            System.out.println(stock);
        }
    }

    // 二分查找最接近的日期
    public static int binarySearch(int[] array, int target) {
        int left = 0;
        int right = array.length - 1;

        while (left <= right) {
            int mid = left + (right - left) / 2;

            if (array[mid] == target) {
                return mid; // 目标值在数组中的索引
            } else if (array[mid] < target) {
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }

        return left; // 未找到目标值，取离他最近的大值
    }

    public static NearlyOptionData calOpenStrikePrice(String date, String stock, double open) throws Exception {
        String expirationDate = "";
        LocalDate day = LocalDate.parse(date, DB_DATE_FORMATTER);
        for (int i = 0; i < weekStrArray.length; i++) {
            String week = weekStrArray[i];
            LocalDate weekDay = LocalDate.parse(week, DB_DATE_FORMATTER);
            if (weekDay.isAfter(day)) {
                expirationDate = week;
                break;
            }
        }
        if (StringUtils.isBlank(expirationDate)) {
            return null;
        }

        String chainDir = USER_PATH + "optionData/optionChain/" + stock + "/";
        String filePath = chainDir + date;
        List<String> callAndPuts = BaseUtils.readFile(filePath);

        if (CollectionUtils.isEmpty(callAndPuts)) {
            CloseableHttpClient httpClient = queue.take();
            String url = String.format("https://api.polygon.io/v3/reference/options/contracts?contract_type=call&"
              + "underlying_ticker=%s&expired=%s&expiration_date=%s&order=asc&limit=100&sort=strike_price"
              + "&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY", stock, true, expirationDate);
            HttpGet getMethod = new HttpGet(url);
            try {
                while (true) {
                    CloseableHttpResponse execute = httpClient.execute(getMethod);
                    InputStream content = execute.getEntity().getContent();
                    OptionContractsResp resp = JSON.parseObject(content, OptionContractsResp.class);
                    for (OptionContracts chain : resp.getResults()) {
                        String callCode = chain.getTicker();
                        String putCode = BaseUtils.getOptionPutCode(callCode);
                        callAndPuts.add(callCode + "|" + putCode);
                    }
                    String nextUrl = resp.getNext_url();
                    if (StringUtils.isBlank(nextUrl)) {
                        break;
                    } else {
                        getMethod.releaseConnection();
                        getMethod = new HttpGet(nextUrl + "&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY");
                    }
                }

                BaseUtils.createDirectory(chainDir);
                BaseUtils.writeFile(filePath, callAndPuts);
            } catch (Exception e) {
                System.out.println("grabOptionChain error. url=" + url);
            } finally {
                getMethod.releaseConnection();
                queue.offer(httpClient);
            }
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
        int priceDiff = Integer.MAX_VALUE;
        String callOption = "";
        List<String> callList = Lists.newArrayList();
        for (int i = 0; i < callAndPuts.size(); i++) {
            String callAndPut = callAndPuts.get(i);
            String code = callAndPut.split("\\|")[0];
            int strikePrice = Integer.parseInt(code.substring(code.length() - 8));
            callList.add(code);

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
        for (int i = 1; i < callList.size() - 1; i++) {
            if (StringUtils.equalsIgnoreCase(callList.get(i), callOption)) {
                lower = callList.get(i - 1);
                higher = callList.get(i + 1);
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
        if (digitalPrice != strikePrice) {
            if (digitalPrice < strikePrice) {
                upStrike = callOption;
                downStrike = lower;
                double downDiffRatio = (double) (strikePrice - digitalPrice) / (double) (strikePrice - lowerPrice);
                if (downDiffRatio < 0.25) {
                    upStrike = higher;
                }
            } else {
                upStrike = higher;
                downStrike = callOption;
                double downDiffRatio = (double) (digitalPrice - strikePrice) / (double) (higherPrice - strikePrice);
                if (downDiffRatio < 0.25) {
                    downStrike = lower;
                }
            }
        } else if (digitalPrice == strikePrice) {
            upStrike = higher;
            downStrike = lower;
        }

        String call = upStrike;
        String put = BaseUtils.getOptionPutCode(downStrike);

        NearlyOptionData nearlyOptionData = new NearlyOptionData();
        nearlyOptionData.setOpenPrice(open);
        nearlyOptionData.setDate(date);
        nearlyOptionData.setStock(stock);
        nearlyOptionData.setOutPriceCallOptionCode_1(call);
        nearlyOptionData.setOutPricePutOptionCode_1(put);
        return nearlyOptionData;
    }

    public static Map<String/* date */, List<NearlyOptionData>> calOpenStrikePriceRatioMap() throws Exception {
        Map<String, List<NearlyOptionData>> dateToOpenStrikePriceRatioMap = Maps.newTreeMap(Comparator.comparing(BaseUtils::dateToInt));
        Map<String, String> nearlyOptionFileMap = BaseUtils.getFileMap(USER_PATH + "optionData/nearlyOpenOption/" + year);

        for (String stock : nearlyOptionFileMap.keySet()) {
            if (!stock.equals("AI")) {
                //                                continue;
            }
            String filePath = nearlyOptionFileMap.get(stock);

            List<String> lines = BaseUtils.readFile(filePath);
            if (CollectionUtils.isEmpty(lines)) {
                continue;
            }

            for (String line : lines) {
                String[] split = line.split("\t");
                String date = split[0];
                String openPrice = split[1];
                String optionCode = split[2];
                Integer optionPriceStep = Integer.valueOf(split[3]);
                if (!date.equals("02/17/2022")) {
                    //                    continue;
                }

                // 计算开盘价和行权价的差值
                int index = optionCode.length() - 8;
                String temp = optionCode.substring(index);
                int i;
                for (i = 0; i < temp.length(); i++) {
                    if (temp.charAt(i) != '0') {
                        break;
                    }
                }
                int strikePrice = Integer.parseInt(temp.substring(i));
                int strikePriceLength = temp.substring(i).length();
                //                StringBuffer openPriceSb = new StringBuffer(openPrice.replace(".", ""));
                //                while (openPriceSb.length() < strikePriceLength) {
                //                    openPriceSb.append("0");
                //                }
                //                int openPriceDigital = Integer.valueOf(openPriceSb.toString());
                int decade = Double.valueOf(openPrice).intValue();
                int count = String.valueOf(decade).length();
                int standardCount = count + 3;
                String priceStr = openPrice.replace(".", "");
                int lastCount = standardCount - priceStr.length();
                int openPriceDigital = Integer.valueOf(priceStr) * (int) Math.pow(10, lastCount);
                double priceDiffRatio = Math.abs(1 - (double) openPriceDigital / (double) strikePrice);
                String optionPrefix = optionCode.substring(0, index + i);

                //                if (openPriceDigital != openPriceDigital2) {
                //                    System.out.println(openPrice + " " + optionCode);
                //                }

                // 计算行权价前后各两档的虚值期权代码
                // 改进后算法
                int upStrikePrice = 0;
                int downStrikePrice = 0;
                if (openPriceDigital != strikePrice) {
                    if (openPriceDigital < strikePrice) {
                        upStrikePrice = strikePrice;
                        downStrikePrice = strikePrice - optionPriceStep;
                    } else {
                        upStrikePrice = strikePrice + optionPriceStep;
                        downStrikePrice = strikePrice;
                    }
                    if (openPriceDigital - optionPriceStep * 0.25 < downStrikePrice) {
                        downStrikePrice = downStrikePrice - optionPriceStep;
                    }
                    if (openPriceDigital + optionPriceStep * 0.25 > upStrikePrice) {
                        upStrikePrice = upStrikePrice + optionPriceStep;
                    }
                } else if (openPriceDigital == strikePrice) {
                    upStrikePrice = strikePrice + optionPriceStep;
                    downStrikePrice = strikePrice - optionPriceStep;
                }
                String call_1 = String.valueOf(upStrikePrice);
                String call_2 = String.valueOf(upStrikePrice + optionPriceStep);
                String put_1 = String.valueOf(downStrikePrice);
                String put_2 = String.valueOf(downStrikePrice - optionPriceStep);
                // 改进前算法
                //                String call_1 = String.valueOf(strikePrice + optionPriceStep);
                //                String call_2 = String.valueOf(strikePrice + optionPriceStep * 2);
                //                String put_1 = String.valueOf(strikePrice - optionPriceStep);
                //                String put_2 = String.valueOf(strikePrice - optionPriceStep * 2);

                String optionCode_call1 = optionPrefix + call_1;
                String optionCode_call2 = optionPrefix + call_2;
                String optionCode_put1 = BaseUtils.getOptionPutCode(optionPrefix + put_1);
                String optionCode_put2 = BaseUtils.getOptionPutCode(optionPrefix + put_2);
                if (call_1.length() < strikePriceLength) {
                    optionCode_call1 = optionPrefix + "0" + call_1;
                } else if (call_1.length() > strikePriceLength) {
                    optionCode_call1 = optionPrefix.substring(0, optionPrefix.length() - 1) + call_1;
                }
                if (call_2.length() < strikePriceLength) {
                    optionCode_call2 = optionPrefix + "0" + call_2;
                } else if (call_2.length() > strikePriceLength) {
                    optionCode_call2 = optionPrefix.substring(0, optionPrefix.length() - 1) + call_2;
                }
                if (put_1.length() < strikePriceLength) {
                    optionCode_put1 = BaseUtils.getOptionPutCode(optionPrefix + "0" + put_1);
                } else if (put_1.length() > strikePriceLength) {
                    optionCode_put1 = BaseUtils.getOptionPutCode(optionPrefix.substring(0, optionPrefix.length() - 1) + put_1);
                }
                if (put_2.length() < strikePriceLength) {
                    optionCode_put2 = BaseUtils.getOptionPutCode(optionPrefix + "0" + put_2);
                } else if (put_2.length() > strikePriceLength) {
                    optionCode_put2 = BaseUtils.getOptionPutCode(optionPrefix.substring(0, optionPrefix.length() - 1) + put_2);
                }

                NearlyOptionData nearlyOptionData = new NearlyOptionData();
                nearlyOptionData.setDate(date);
                nearlyOptionData.setStock(stock);
                nearlyOptionData.setOpenPrice(Double.parseDouble(openPrice));
                nearlyOptionData.setOptionCode(optionCode);
                nearlyOptionData.setStrikePriceStep(optionPriceStep);
                nearlyOptionData.setOpenStrikePriceDiffRatio(priceDiffRatio);
                nearlyOptionData.setOutPriceCallOptionCode_1(optionCode_call1);
                nearlyOptionData.setOutPriceCallOptionCode_2(optionCode_call2);
                nearlyOptionData.setOutPricePutOptionCode_1(optionCode_put1);
                nearlyOptionData.setOutPricePutOptionCode_2(optionCode_put2);

                if (!dateToOpenStrikePriceRatioMap.containsKey(date)) {
                    dateToOpenStrikePriceRatioMap.put(date, Lists.newArrayList());
                }
                dateToOpenStrikePriceRatioMap.get(date).add(nearlyOptionData);
            }
        }

        for (String date : dateToOpenStrikePriceRatioMap.keySet()) {
            List<NearlyOptionData> list = dateToOpenStrikePriceRatioMap.get(date);
            list.sort(Comparator.comparing(NearlyOptionData::getOpenStrikePriceDiffRatio));
        }

        return dateToOpenStrikePriceRatioMap;
    }

    public static OptionDaily requestOptionDailyList(CloseableHttpClient httpClient, String date, String code) throws IOException {
        String url = String.format("https://api.polygon.io/v1/open-close/%s/%s?adjusted=true&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY",
          code, date);

        HttpGet get = new HttpGet(url);
        try {
            CloseableHttpResponse execute = httpClient.execute(get);
            InputStream stream = execute.getEntity().getContent();
            OptionDaily resp = JSON.parseObject(stream, OptionDaily.class);
            String status = resp.getStatus();
            if (!StringUtils.equalsIgnoreCase(status, "OK")) {
                //                System.out.println("get failed. " + url);
                return null;
            }

            return resp;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            get.releaseConnection();
        }

        return null;
    }

    public static StraddleOption getDaily(StraddleOption straddleOption) throws Exception {
        OptionDaily call_1 = straddleOption.getCall_1();
        OptionDaily call_2 = straddleOption.getCall_2();
        OptionDaily put_1 = straddleOption.getPut_1();
        OptionDaily put_2 = straddleOption.getPut_2();
        List<OptionDaily> dailyList = Lists.newArrayList(call_1, call_2, put_1, put_2);

        CountDownLatch cdl = new CountDownLatch(4);
        for (int i = 0; i < 4; i++) {
            OptionDaily daily = dailyList.get(i);
            CloseableHttpClient httpClient = queue.take();
            int index = i;
            cachedThread.execute(() -> {
                try {
                    String date = daily.getFrom();
                    OptionDaily optionDaily;
                    String symbol = daily.getSymbol();
                    synchronized (stockOptionDailyMap) {
                        optionDaily = getOptionDaily(symbol, date);
                    }
                    if (optionDaily == null) {
                        optionDaily = requestOptionDailyList(httpClient, date, symbol);
                        synchronized (stockOptionDailyMap) {
                            writeOptionDaily(optionDaily, symbol, date);
                            refreshOptionDailyMap(optionDaily, symbol, date);
                        }
                    }
                    if (index == 0) {
                        straddleOption.setCall_1(optionDaily);
                    } else if (index == 1) {
                        straddleOption.setCall_2(optionDaily);
                    } else if (index == 2) {
                        straddleOption.setPut_1(optionDaily);
                    } else if (index == 3) {
                        straddleOption.setPut_2(optionDaily);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    queue.offer(httpClient);
                    cdl.countDown();
                }
            });
        }
        cdl.await();

        return straddleOption;
    }

    public static OptionDaily getOptionDaily(String optionCode, String date) throws Exception {
        int _2_index = optionCode.indexOf("2");
        String stock = optionCode.substring(2, _2_index);
        if (!stockOptionDailyMap.containsKey(stock)) {
            stockOptionDailyMap.put(stock, BaseUtils.loadOptionDailyMap(stock));
        }

        Map<String, Map<String, OptionDaily>> dailyMap = stockOptionDailyMap.get(stock);

        if (MapUtils.isEmpty(dailyMap)) {
            return null;
        }

        Map<String, OptionDaily> dateDailyMap = dailyMap.get(date);
        if (MapUtils.isEmpty(dateDailyMap)) {
            return null;
        }

        OptionDaily optionDaily = dateDailyMap.get(optionCode);
        return optionDaily;
    }

    public static void clearOptionDailyCache() {
        stockOptionDailyMap.clear();
    }

    public static void writeOptionDaily(OptionDaily optionDaily, String optionCode, String date) throws Exception {
        if (optionDaily == null) {
            optionDaily = OptionDaily.EMPTY(date, optionCode);
        }

        int _2_index = optionCode.indexOf("2");
        String stock = optionCode.substring(2, _2_index);

        List<String> lines = BaseUtils.readFile(USER_PATH + "optionData/optionDaily/" + stock);
        lines.add(optionDaily.toString());
        BaseUtils.writeFile(USER_PATH + "optionData/optionDaily/" + stock, lines);
    }

    public static void refreshOptionDailyMap(OptionDaily optionDaily, String optionCode, String date) {
        int _2_index = optionCode.indexOf("2");
        String stock = optionCode.substring(2, _2_index);
        if (!stockOptionDailyMap.containsKey(stock)) {
            stockOptionDailyMap.put(stock, Maps.newHashMap());
        }

        Map<String, Map<String, OptionDaily>> dailyMap = stockOptionDailyMap.get(stock);

        if (!dailyMap.containsKey(date)) {
            dailyMap.put(date, Maps.newHashMap());
        }

        Map<String, OptionDaily> dateDailyMap = dailyMap.get(date);
        dateDailyMap.put(optionCode, optionDaily);
    }

    private static StockKLine getLastKLine(String date, String stock) throws Exception {
        List<StockKLine> stockKLines = BaseUtils.loadDataToKline(HIS_BASE_PATH + "merge/" + stock, 2024, 2021);
        for (int i = 0; i < stockKLines.size() - 1; i++) {
            StockKLine stockKLine = stockKLines.get(i);
            String d = BaseUtils.formatDate(stockKLine.getDate());
            if (d.equals(date)) {
                StockKLine lastKline = stockKLines.get(i + 1);
                return lastKline;
            }
        }
        return null;
    }

    private static BOLL getLastBoll(String date, String stock) throws Exception {
        List<BOLL> bolls = BaseUtils.readBollFile(HIS_BASE_PATH + "mergeBoll/" + stock, 2024, 2021);
        for (int i = 0; i < bolls.size() - 1; i++) {
            BOLL boll = bolls.get(i);
            String d = BaseUtils.formatDate(boll.getDate());
            if (d.equals(date)) {
                BOLL lastBoll = bolls.get(i + 1);
                return lastBoll;
            }
        }
        return null;
    }

    private static void calStraddleData() throws Exception {
        Map<String, List<Double>> ivMap = loadIvMap();
        Map<String, List<NearlyOptionData>> dateOpenStrikePriceRatioMap = calOpenStrikePriceRatioMap();
        for (String date : dateOpenStrikePriceRatioMap.keySet()) {
            String formatDate = BaseUtils.formatDate(date);
            if (weekSet.contains(formatDate)) {
                continue;
            }

            List<NearlyOptionData> nearlyOptionDataList = dateOpenStrikePriceRatioMap.get(date);
            if (CollectionUtils.isEmpty(nearlyOptionDataList)) {
                continue;
            }

            for (NearlyOptionData nearlyOptionData : nearlyOptionDataList) {
                if (nearlyOptionData.getOpenStrikePriceDiffRatio().compareTo(0.0) != 0) {
                    break;
                }
                double openPrice = nearlyOptionData.getOpenPrice();
                String stock = nearlyOptionData.getStock();

                StraddleOption straddleOption = new StraddleOption();
                String outPriceCallOptionCode_1 = nearlyOptionData.getOutPriceCallOptionCode_1();
                String outPriceCallOptionCode_2 = nearlyOptionData.getOutPriceCallOptionCode_2();
                String outPricePutOptionCode_1 = nearlyOptionData.getOutPricePutOptionCode_1();
                String outPricePutOptionCode_2 = nearlyOptionData.getOutPricePutOptionCode_2();
                OptionDaily call_1 = new OptionDaily(formatDate, outPriceCallOptionCode_1);
                OptionDaily call_2 = new OptionDaily(formatDate, outPriceCallOptionCode_2);
                OptionDaily put_1 = new OptionDaily(formatDate, outPricePutOptionCode_1);
                OptionDaily put_2 = new OptionDaily(formatDate, outPricePutOptionCode_2);
                straddleOption.setCall_1(call_1);
                straddleOption.setCall_2(call_2);
                straddleOption.setPut_1(put_1);
                straddleOption.setPut_2(put_2);

                straddleOption = getDaily(straddleOption);
                call_1 = straddleOption.getCall_1();
                call_2 = straddleOption.getCall_2();
                put_1 = straddleOption.getPut_1();
                put_2 = straddleOption.getPut_2();

                StockKLine lastKline = getLastKLine(formatDate, stock);
                double lastClose = 0;
                double avgAmplitude = 0;
                boolean bollCrossOpenClose = false;
                if (lastKline != null) {
                    lastClose = lastKline.getClose();
                    double lastOpen = lastKline.getOpen();

                    //                    BOLL lastBoll = getLastBoll(formatDate, stock);
                    //                    bollCrossOpenClose = checkBollCrossOpenClose(lastBoll, lastOpen, lastClose);
                    //                    if (bollCrossOpenClose) {
                    //                        Map<Integer, Double> gearAvg = stockToVolatilityMap.get(stock);
                    //                        if (gearAvg == null) {
                    //                            avgAmplitude = 0;
                    //                        } else {
                    //                            int lastCloseOpenRatioInt = (int) (Math.abs((lastOpen - lastClose) / lastOpen) * 100);
                    //                            if (!gearAvg.containsKey(lastCloseOpenRatioInt)) {
                    //                                avgAmplitude = 0d;
                    //                            } else {
                    //                                avgAmplitude = gearAvg.get(lastCloseOpenRatioInt);
                    //                            }
                    //                        }
                    //                    }
                }

                // 过滤前日call和put交易量小于100
                String lastDate = dateMap.get(formatDate);
                if (StringUtils.isBlank(lastDate)) {
                    continue;
                }
                OptionDaily last_call_1 = getOptionDaily(call_1.getSymbol(), lastDate);
                OptionDaily last_put_1 = getOptionDaily(put_1.getSymbol(), lastDate);
                if (last_call_1 == null || last_put_1 == null || last_call_1.getVolume() < 100 || last_put_1.getVolume() < 100) {
                    continue;
                }

                // 过滤开盘call和put差值比例超过10%
                double open_call_1 = call_1.getOpen();
                double open_put_1 = put_1.getOpen();
                double openCallPutRatio = Math.abs(open_call_1 / open_put_1 - 1) * 100;
                if (openCallPutRatio > 10) {
                    continue;
                }

                // 过滤股票开盘和前日收盘差值比例小于2%
                double openLastCloseRatio = Math.abs(openPrice - lastClose) / lastClose * 100;
                if (openLastCloseRatio < 2) {
                    continue;
                }

                List<Double> call_1_IvList = ivMap.get(outPriceCallOptionCode_1);
                List<Double> put_1_IvList = ivMap.get(outPricePutOptionCode_1);
                boolean call_1_canTrade = canTradeForIv(call_1_IvList);
                boolean put_1_canTrade = canTradeForIv(put_1_IvList);
                boolean canTradeForIv = call_1_canTrade && put_1_canTrade;

                //                calStraddleSimulateTrade(call_1, put_1);
                System.out.println(stock + "\t" + openPrice + "\t" + lastClose + "\t" + canTradeForIv + "\t" + straddleOption);
            }
        }
    }

    public static List<String> getOptionTradeDetail(String option, String beginTime, String endTime, boolean limit) {
        List<String> list = Lists.newArrayList();
        CloseableHttpClient httpClient = null;
        try {
            httpClient = queue.take();
            if (limit) {
                list = GetOptionTrade.getData(httpClient, option, beginTime, endTime);
            } else {
                list = GetOptionTrade.getAllData(httpClient, option, beginTime, endTime);
            }
        } catch (Exception e) {
            System.out.println("getTradeDetail error. option=" + option);
        } finally {
            if (httpClient != null) {
                queue.offer(httpClient);
            }
        }
        return list;
    }

    public static void getOptionTradeData(String stock, String call, String put, List<String> dayAllSeconds, String date) throws Exception {
        String dir = USER_PATH + "optionData/trade/" + stock + "/" + date + "/";
        String callPath = dir + call.substring(2);
        String putPath = dir + put.substring(2);

        if (BaseUtils.fileExist(callPath) && BaseUtils.fileExist(putPath)) {
            return;
        }

        String beginTime = dayAllSeconds.get(0);
        String endTime = dayAllSeconds.get(dayAllSeconds.size() - 1);

        List<String> callDetail = getOptionTradeDetail(call, beginTime, endTime, true);
        List<String> putDetail = getOptionTradeDetail(put, beginTime, endTime, true);
        if (callDetail == null || putDetail == null) {
            return;
        }

        String callLast = callDetail.get(callDetail.size() - 1);
        String putLast = putDetail.get(putDetail.size() - 1);

        Long callLastTime = Long.valueOf(callLast.split("\t")[0]);
        Long putLastTime = Long.valueOf(putLast.split("\t")[0]);

        if (callLastTime > putLastTime) {
            putDetail = getOptionTradeDetail(put, beginTime, String.valueOf(callLastTime), false);
        } else {
            callDetail = getOptionTradeDetail(call, beginTime, String.valueOf(putLastTime), false);
        }

        BaseUtils.createDirectory(dir);
        BaseUtils.writeFile(callPath, callDetail);
        BaseUtils.writeFile(putPath, putDetail);
    }

    public static void getOption1MinTradeData(String stock, String call, String put, List<String> dayAllSeconds, String date) throws Exception {
        String dir = USER_PATH + "optionData/trade/" + stock + "/" + date + "/";
        String callPath = dir + call.substring(2);
        String putPath = dir + put.substring(2);

        if (BaseUtils.fileExist(callPath) && BaseUtils.fileExist(putPath)) {
            return;
        }

        String beginTime = dayAllSeconds.get(0);
        String endTime = dayAllSeconds.get(59);

        List<String> callDetail = getOptionTradeDetail(call, beginTime, endTime, true);
        List<String> putDetail = getOptionTradeDetail(put, beginTime, endTime, true);
        if (callDetail == null || putDetail == null) {
            System.out.println("getOptionTradeDetail null. call=" + call + " put=" + put);
            return;
        }

        BaseUtils.createDirectory(dir);
        BaseUtils.writeFile(callPath, callDetail);
        BaseUtils.writeFile(putPath, putDetail);
    }

    public static int calCanTradeSeconds(String stock, String date, String call, String put, List<String> dayAllSeconds) throws Exception {
        String dir = USER_PATH + "optionData/trade/" + stock + "/" + date + "/";
        String callPath = dir + call.substring(2);
        String putPath = dir + put.substring(2);

        List<String> callList = BaseUtils.readFile(callPath);
        List<String> putList = BaseUtils.readFile(putPath);
        String callData = callList.get(4);
        String putData = putList.get(4);
        Long callTime = Long.valueOf(callData.split("\t")[0]);
        Long putTime = Long.valueOf(putData.split("\t")[0]);
        Long tradeTime = callTime > putTime ? callTime : putTime;
        for (int i = 0; i < dayAllSeconds.size(); i++) {
            if (Long.valueOf(dayAllSeconds.get(i)) > tradeTime) {
                return i;
            }
        }
        return 0;
    }

    public static boolean calCanTrade1Min(String stock, String date, String call, String put, List<String> dayAllSeconds) throws Exception {
        String dir = USER_PATH + "optionData/trade/" + stock + "/" + date + "/";
        String callPath = dir + call.substring(2);
        String putPath = dir + put.substring(2);

        List<String> callList = BaseUtils.readFile(callPath);
        List<String> putList = BaseUtils.readFile(putPath);
        return !CollectionUtils.isEmpty(callList) && !CollectionUtils.isEmpty(putList);
    }

    // 根据报价数据计算双开模拟交易数据，用于测试止损和止盈点
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
            for (int i = sec; i < dayAllSeconds.size() - 60; i++) {
                Long seconds = Long.valueOf(dayAllSeconds.get(i)) / 1000000000;
                Double callClose = callTradePriceMap.get(seconds);
                Double putClose = putTradePriceMap.get(seconds);
                if (callClose == 0 || putClose == 0) {
                    continue;
                }
                double open = BigDecimal.valueOf(putOpen + callOpen).setScale(5, RoundingMode.HALF_UP).doubleValue();
                double callDiff = BigDecimal.valueOf(callClose - callOpen).setScale(5, RoundingMode.HALF_UP).doubleValue();
                double putDiff = BigDecimal.valueOf(putClose - putOpen).setScale(5, RoundingMode.HALF_UP).doubleValue();
                double allDiff = BigDecimal.valueOf(callDiff + putDiff).setScale(5, RoundingMode.HALF_UP).doubleValue();
                double diffRatio = BigDecimal.valueOf(allDiff / (callOpen + putOpen) * 100).setScale(2, RoundingMode.HALF_UP).doubleValue();

                String sellTime = LocalDateTime.ofEpochSecond(seconds, 0, ZoneOffset.of("+8")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                String buyTime = LocalDateTime.ofEpochSecond(buySeconds, 0, ZoneOffset.of("+8")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                //            list.add(result);
                //            System.out.println(result);
                if (diffRatio < -40) {
                    result = buyTime + "\t" + sellTime + "\t" + callOpen + "\t" + callClose + "\t" + putOpen + "\t" + putClose + "\t" + callDiff + "\t" + putDiff + "\t" + allDiff + "\t" + diffRatio;
                    return result;
                }
                if (i <= 10800 && i >= 120) {
                    if (diffRatio >= 20) {
                        //                    if (false
                        //                      || (open <= 0.3 && diffRatio >= 4.14)
                        //                      || (open > 0.3 && open <= 0.4 && diffRatio >= 7.7)
                        //                      || (open > 0.4 && open <= 0.5 && diffRatio >= 9.95)
                        //                      || (open > 0.5 && open <= 0.6 && diffRatio >= 11.51)
                        //                      || (open > 0.6 && open <= 0.7 && diffRatio >= 12.64)
                        //                      || (open > 0.7 && open <= 0.8 && diffRatio >= 13.51)
                        ////                      || ( open <= 0.8 && diffRatio >= 15)
                        //                      || (open > 0.8 && open <= 0.9 && diffRatio >= 14.2)
                        //                      || (open > 0.9 && open <= 1.0 && diffRatio >= 14.75)
                        //                      || (open > 1.0 && open <= 1.1 && diffRatio >= 15.21)
                        //                      || (open > 1.1 && open <= 1.2 && diffRatio >= 15.6)
                        //                      || (open > 1.2 && open <= 1.3 && diffRatio >= 15.92)
                        //                      || (open > 1.3 && open <= 1.4 && diffRatio >= 16.2)
                        //                      || (open > 1.4 && open <= 1.5 && diffRatio >= 16.45)
                        //                      || (open > 1.5 && open <= 1.6 && diffRatio >= 16.67)
                        //                      || (open > 1.6 && open <= 1.7 && diffRatio >= 16.86)
                        //                      || (open > 1.7 && open <= 1.8 && diffRatio >= 17.03)
                        //                      || (open > 1.8 && open <= 1.9 && diffRatio >= 17.18)
                        //                      || (open > 1.9 && open <= 2 && diffRatio >= 17.32)
                        //                      || (open > 2 && diffRatio > 17.4)
                        //                    ) { // 开盘价和小于0.5的止盈是30%，大于0.5的止盈是20%
                        result = buyTime + "\t" + sellTime + "\t" + callOpen + "\t" + callClose + "\t" + putOpen + "\t" + putClose + "\t" + callDiff + "\t" + putDiff + "\t" + allDiff + "\t" + diffRatio;
                        return result;
                    } else if (diffRatio < -20) {
                        //                    } else if (false
                        //                      || (open <= 0.3 && diffRatio <= -7.8)
                        //                      || (open > 0.3 && open <= 0.4 && diffRatio <= -10.88)
                        //                      || (open > 0.4 && open <= 0.5 && diffRatio <= -12.72)
                        //                      || (open > 0.5 && open <= 0.6 && diffRatio <= -13.92)
                        //                      || (open > 0.6 && open <= 0.7 && diffRatio <= -14.8)
                        //                      || (open > 0.7 && open <= 0.8 && diffRatio <= -15.44)
                        //                      || (open <= 0.8 && diffRatio <= -16)
                        //                      || (open > 0.8 && open <= 0.9 && diffRatio <= -16)
                        //                      || (open > 0.9 && open <= 1.0 && diffRatio <= -16.4)
                        //                      || (open > 1.0 && open <= 1.1 && diffRatio <= -16.7)
                        //                      || (open > 1.1 && open <= 1.2 && diffRatio <= -16.96)
                        //                      || (open > 1.2 && open <= 1.3 && diffRatio <= -17.2)
                        //                      || (open > 1.3 && open <= 1.4 && diffRatio <= -17.44)
                        //                      || (open > 1.4 && open <= 1.5 && diffRatio <= -17.6)
                        //                      || (open > 1.5 && open <= 1.6 && diffRatio <= -17.76)
                        //                      || (open > 1.6 && open <= 1.7 && diffRatio <= -17.84)
                        //                      || (open > 1.7 && open <= 1.8 && diffRatio <= -18)
                        //                      || (open > 1.8 && open <= 1.9 && diffRatio <= -18.08)
                        //                      || (open > 1.9 && open <= 2 && diffRatio <= -18.16)
                        //                      || (open > 2 && diffRatio <= -19)
                        //                    ) {
                        result = buyTime + "\t" + sellTime + "\t" + callOpen + "\t" + callClose + "\t" + putOpen + "\t" + putClose + "\t" + callDiff + "\t" + putDiff + "\t" + allDiff + "\t" + diffRatio;
                        return result;
                    }
                } else if (i > 10800) {
                    if (diffRatio < -20 || diffRatio > 10) {
                        //                    if (false
                        //                      || (open <= 0.3 && diffRatio <= -7.8)
                        //                      || (open > 0.3 && open <= 0.4 && diffRatio <= -10.88)
                        //                      || (open > 0.4 && open <= 0.5 && diffRatio <= -12.72)
                        //                      || (open > 0.5 && open <= 0.6 && diffRatio <= -13.92)
                        //                      || (open > 0.6 && open <= 0.7 && diffRatio <= -14.8)
                        //                      || (open > 0.7 && open <= 0.8 && diffRatio <= -15.44)
                        //                      || (open <= 0.8 && diffRatio <= -16)
                        //                      || (open > 0.8 && open <= 0.9 && diffRatio <= -16)
                        //                      || (open > 0.9 && open <= 1.0 && diffRatio <= -16.4)
                        //                      || (open > 1.0 && open <= 1.1 && diffRatio <= -16.7)
                        //                      || (open > 1.1 && open <= 1.2 && diffRatio <= -16.96)
                        //                      || (open > 1.2 && open <= 1.3 && diffRatio <= -17.2)
                        //                      || (open > 1.3 && open <= 1.4 && diffRatio <= -17.44)
                        //                      || (open > 1.4 && open <= 1.5 && diffRatio <= -17.6)
                        //                      || (open > 1.5 && open <= 1.6 && diffRatio <= -17.76)
                        //                      || (open > 1.6 && open <= 1.7 && diffRatio <= -17.84)
                        //                      || (open > 1.7 && open <= 1.8 && diffRatio <= -18)
                        //                      || (open > 1.8 && open <= 1.9 && diffRatio <= -18.08)
                        //                      || (open > 1.9 && open <= 2 && diffRatio <= -18.16)
                        //                      || (open > 2 && diffRatio <= -19)
                        //                      || diffRatio > 10
                        //                    ) {
                        result = buyTime + "\t" + sellTime + "\t" + callOpen + "\t" + callClose + "\t" + putOpen + "\t" + putClose + "\t" + callDiff + "\t" + putDiff + "\t" + allDiff + "\t" + diffRatio;
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

    public static Map<Long, Double> calQuoteListForSeconds(List<String> quoteList) {
        Map<Long, Double> secondsPriceMap = Maps.newHashMap();
        long latestTime = 0;
        List<Double> askPriceList = Lists.newArrayList();
        List<Double> bidPriceList = Lists.newArrayList();
        for (String callQuote : quoteList) {
            String[] split = callQuote.split("\t"); // 1706126148133253376	2.02	18	1.97	13
            Long timestamp = Long.valueOf(split[0]) / 1000000000;
            int askSize = Integer.parseInt(split[2]);
            int bidSize = Integer.parseInt(split[4]);
            double askPrice = Double.parseDouble(split[1]);
            double bidPrice = Double.parseDouble(split[3]);
            if ((askSize < 5 && bidSize < 5) || askPrice == 0 || bidPrice == 0) {
                continue;
            }

            //            double diff = bidPrice < 3 ? (askPrice - bidPrice) / 0.01 : (askPrice - bidPrice) / 0.05;
            //            if (diff > 15) {
            //                LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault());
            //                System.out.println("time=" + dateTime + ", diff=" + diff + ", bid=" + bidPrice + ", ask=" + askPrice);
            //            }
            if (timestamp != latestTime) {
                Double avgAskPrice = askPriceList.stream().collect(Collectors.averagingDouble(a -> a));
                Double avgBidPrice = bidPriceList.stream().collect(Collectors.averagingDouble(b -> b));
                if (avgAskPrice != 0) {
                    secondsPriceMap.put(latestTime, BigDecimal.valueOf((avgAskPrice + avgBidPrice) / 2).setScale(2, RoundingMode.HALF_UP).doubleValue());
                    askPriceList.clear();
                    bidPriceList.clear();
                }
                latestTime = timestamp;
                askPriceList.add(askPrice);
                bidPriceList.add(bidPrice);
            } else {
                askPriceList.add(askPrice);
                bidPriceList.add(bidPrice);
            }
        }
        return secondsPriceMap;
    }

    public static Map<Long, Double> calQuoteBidForSeconds(List<String> quoteList) {
        Map<Long, Double> secondsPriceMap = Maps.newHashMap();
        long latestTime = 0;
        List<Double> priceList = Lists.newArrayList();
        for (String callQuote : quoteList) {
            String[] split = callQuote.split("\t"); // 1706126148133253376	2.02	18	1.97	13
            Long timestamp = Long.valueOf(split[0]) / 1000000000;
            int askSize = Integer.parseInt(split[2]);
            int bidSize = Integer.parseInt(split[4]);
            double askPrice = Double.parseDouble(split[1]);
            double bidPrice = Double.parseDouble(split[3]);
            double price = askPrice > bidPrice ? bidPrice : askPrice;
            if ((askSize < 5 && bidSize < 5) || price == 0) {
                continue;
            }

            if (timestamp != latestTime) {
                Double avgPrice = priceList.stream().collect(Collectors.averagingDouble(a -> a));
                secondsPriceMap.put(latestTime, avgPrice);
                latestTime = timestamp;
                priceList.clear();
                priceList.add(price);
            } else {
                priceList.add(price);
            }
        }
        return secondsPriceMap;
    }

    public static void getStraddleLastDaily() throws Exception {
        Map<String, List<NearlyOptionData>> dateOpenStrikePriceRatioMap = calOpenStrikePriceRatioMap();
        for (String date : dateOpenStrikePriceRatioMap.keySet()) {
            String formatDate = BaseUtils.formatDate(date);
            if (weekSet.contains(formatDate)) {
                //                continue;
            }

            List<NearlyOptionData> nearlyOptionDataList = dateOpenStrikePriceRatioMap.get(date);
            if (CollectionUtils.isEmpty(nearlyOptionDataList)) {
                continue;
            }

            String requestDate = dateMap.get(formatDate);
            if (requestDate == null) {
                continue;
            }

            List<String> optionCodeList = Lists.newArrayList();
            for (NearlyOptionData nearlyOptionData : nearlyOptionDataList) {
                if (nearlyOptionData.getOpenStrikePriceDiffRatio().compareTo(0.0) != 0) {
                    //                    break;
                }
                String callOptionCode = nearlyOptionData.getOutPriceCallOptionCode_1();
                String putOptionCode = nearlyOptionData.getOutPricePutOptionCode_1();
                optionCodeList.add(callOptionCode);
                optionCodeList.add(putOptionCode);
            }

            for (String optionCode : optionCodeList) {
                CloseableHttpClient httpClient = queue.take();
                cachedThread.execute(() -> {
                    try {
                        OptionDaily optionDaily = getOptionDaily(optionCode, requestDate);
                        if (optionDaily == null) {
                            optionDaily = requestOptionDailyList(httpClient, requestDate, optionCode);
                            synchronized (stockOptionDailyMap) {
                                writeOptionDaily(optionDaily, optionCode, requestDate);
                                refreshOptionDailyMap(optionDaily, optionCode, requestDate);
                            }
                            System.out.println("get " + optionCode + " " + requestDate);
                        } else {
                            System.out.println("has " + optionCode + " " + requestDate);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        queue.offer(httpClient);
                    }
                });
            }
        }
    }

    public static void calHistoricalHighVolatility() throws Exception {
        Set<String> weekOptionStock = BaseUtils.getWeekOptionStock();
        int year = 2023;
        for (String stock : weekOptionStock) {
            if (!stock.equals("COIN")) {
                //                continue;
            }

            List<StockKLine> stockKLines = BaseUtils.loadDataToKline(HIS_BASE_PATH + "2023daily/" + stock, year, year - 1);
            if (CollectionUtils.isEmpty(stockKLines)) {
                continue;
            }
            List<BOLL> bolls = BaseUtils.readBollFile(HIS_BASE_PATH + "mergeBoll/" + stock, year, year - 1);
            if (CollectionUtils.isEmpty(bolls)) {
                continue;
            }

            for (int i = 0; i < stockKLines.size() - 2; i++) {
                StockKLine stockKLine = stockKLines.get(i);
                StockKLine lastKLine = stockKLines.get(i + 1);
                BOLL lastBoll = bolls.get(i + 1);

                String date = stockKLine.getDate();
                double open = stockKLine.getOpen();
                double high = stockKLine.getHigh();
                double low = stockKLine.getLow();
                double lastOpen = lastKLine.getOpen();
                double lastClose = lastKLine.getClose();
                boolean bollCrossOpenClose = checkBollCrossOpenClose(lastBoll, lastOpen, lastClose);
                if (bollCrossOpenClose) {
                    double lastCloseOpenRatio = Math.abs((lastOpen - lastClose) / lastOpen) * 100;
                    double amplitude = Math.abs(high - low) / lastClose * 100;

                    System.out.println(stock + "\t" + date + "\t" + open + "\t" + lastCloseOpenRatio + "\t" + amplitude);
                }
            }
        }
    }

    private static boolean checkBollCrossOpenClose(BOLL lastBoll, double lastOpen, double lastClose) {
        double lastUp = lastBoll.getUp();
        double lastDn = lastBoll.getDn();
        boolean lastUpIn_1 = lastOpen < lastUp && lastUp < lastClose;
        boolean lastDnIn_1 = lastOpen < lastUp && lastUp < lastClose;
        boolean lastUpIn_2 = lastClose < lastDn && lastDn < lastOpen;
        boolean lastDnIn_2 = lastClose < lastDn && lastDn < lastOpen;
        boolean bollCrossOpenClose = lastUpIn_1 || lastUpIn_2 || lastDnIn_1 || lastDnIn_2;
        return bollCrossOpenClose;
    }

    public static void loadHistoricalHighVolatility() throws Exception {
        List<String> lines = BaseUtils.readFile(USER_PATH + "optionData/crossBollAmplitude");
        Map<String/* stock */, Map<Integer/* 档位 */, List<Double>/* 振幅列表 */>> stockToVolatilityListMap = Maps.newHashMap();
        for (String line : lines) {
            String[] split = line.split("\t");
            String stock = split[0];
            double hisCloseOpenRatio = Double.valueOf(split[3]);
            Double amplitude = Double.valueOf(split[4]);

            int hisCloseOpenRatioInt = (int) hisCloseOpenRatio;
            if (!stockToVolatilityListMap.containsKey(stock)) {
                stockToVolatilityListMap.put(stock, Maps.newHashMap());
            }

            Map<Integer, List<Double>> listMap = stockToVolatilityListMap.get(stock);
            if (!listMap.containsKey(hisCloseOpenRatioInt)) {
                listMap.put(hisCloseOpenRatioInt, Lists.newArrayList());
            }

            listMap.get(hisCloseOpenRatioInt).add(amplitude);
        }

        for (String stock : stockToVolatilityListMap.keySet()) {
            Map<Integer, List<Double>> listMap = stockToVolatilityListMap.get(stock);
            Map<Integer, Double> gearAvgMap = Maps.newHashMap();
            for (Integer gear : listMap.keySet()) {
                List<Double> list = listMap.get(gear);
                if (list.size() < 3) {
                    gearAvgMap.put(gear, 0d);
                    continue;
                }

                double min = Double.MIN_VALUE;
                int max_index = 0;
                for (int i = 0; i < list.size(); i++) {
                    if (min < list.get(i)) {
                        min = list.get(i);
                        max_index = i;
                    }
                }
                list.remove(max_index);
                Double avg = list.stream().collect(Collectors.averagingDouble(l -> l));

                gearAvgMap.put(gear, avg);
            }
            stockToVolatilityMap.put(stock, gearAvgMap);
        }

        System.out.println();
    }

    public static void getOptionQuoteList() throws Exception {
        Strategy28.init();
        List<String> list = Lists.newArrayList();
        list.add("2022-02-01	O:SNAP220204C00035000");
        for (String l : list) {
            String[] split = l.split("\t");
            String date = split[0];
            String code = split[1];
            OptionCode optionCode = new OptionCode();
            optionCode.setCode(code);
            long begin = System.currentTimeMillis();
            Strategy28.getOptionQuoteList(optionCode, date);
            long end = System.currentTimeMillis();
            System.out.println("finish " + l + ", cost: " + (end - begin) + "ms");
        }
    }

    public static Map<String/* optionCode */, List<Double>/* iv list */> loadIvMap() throws Exception {
        List<String> lines = BaseUtils.readFile(USER_PATH + "optionData/IV/" + year + "/IV");
        Map<String, List<Double>> ivMap = Maps.newHashMap();
        for (String line : lines) {
            String[] split = line.split("\t");
            String optionCode = split[0];
            List<Double> ivList = Lists.newArrayList();
            for (int i = 1; i < split.length; i++) {
                ivList.add(Double.valueOf(split[i]));
            }
            ivMap.put(optionCode, ivList);
        }

        return ivMap;
    }

    public static boolean canTradeForIv(List<Double> ivList) {
        if (CollectionUtils.isEmpty(ivList) || ivList.size() < 3) {
            return false;
        }

        boolean result = true;
        // 如果iv连续两天下跌则不交易
        int times1 = 2;
        List<Double> temp1 = ivList;
        for (int i = 0; i < temp1.size() - 1 && times1 > 0; i++, times1--) {
            if (temp1.get(i) > temp1.get(i + 1)) {
                break;
            }
        }
        if (times1 == 0) {
            return false;
        }

        // 如果近三天的iv都小于0.5则不交易
        int times2 = 3;
        for (int i = 0; i < ivList.size() && times2 > 0; i++, times2--) {
            if (ivList.get(i) > 0.5) { // todo  临时改成0.4方便测试
                break;
            }
        }
        if (times2 == 0) {
            return false;
        }

        // 如果近一天iv小于0.8则不交易
        Double ivValue = ivList.get(0);
        if (ivValue < 0.8) {
            //            return false;
        }

        // 如果近二天比近三天小则不交易
        if (ivList.get(1) < ivList.get(2)) {
            //            return false;
        }

        return result;
    }


    public static void main(String[] args) throws Exception {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.http").setLevel(Level.INFO);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.INFO);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.commons").setLevel(Level.ERROR);

        init();

        //        calOpenStrikePrice("2024-02-05", "NVDA", 68.225);
        //        String callFilePath = USER_PATH + "optionData/optionQuote/HOOD/2024-08-15/HOOD240816C00019500"; // <0.5 =>6
        //        String callFilePath = USER_PATH + "optionData/optionQuote/MU/2024-08-14/MU240816C00102000"; // >1 <1.5
        //        String callFilePath = USER_PATH + "optionData/optionQuote/MU/2024-08-14/MU240816P00100000"; // >1 <1.5 =>15
        String callFilePath = USER_PATH + "optionData/optionQuote/ONON/2024-08-13/ONON240816C00042000"; // >1 <1.5 =>15
        List<String> callQuoteList = BaseUtils.readFile(callFilePath);
        calQuoteListForSeconds(callQuoteList);
        //        getHasWeekOptionStock(); // 获取有周期权的股票
        //        getEqualsStrikePriceKline(); // 获取某个时间范围内，股票开盘价对应的最近行权价代码
        //                calStraddleData(); // 获取双开期权的optionDaily数据
        //        getStraddleLastDaily(); // 获取双开期权当天前一日的optionDaily数据
        //        loadHistoricalHighVolatility();
        //        calHistoricalHighVolatility();
        //                calCallWithProtect(); // 计算裸买和带保护的收益
        //        getOptionQuoteList(); // 获取已过滤出的代码的报价，用于计算止损

        //        List<String> lines = BaseUtils.readFile(USER_PATH + "optionData/stockOpenDate");
        //        for (String line : lines) {
        //            String[] split = line.split("\t");
        //            String date = split[0];
        //            String stock = split[1];
        //            double close = getLastClose(date, stock);
        //            System.out.println(date + "\t" + stock + "\t" + close);
        //        }

        //        List<String> lines = BaseUtils.readFile(USER_PATH + "optionData/dailydata");
        //        Map<String, List<String>> linesMap = Maps.newHashMap();
        //        for (String line : lines) {
        //            String[] split = line.split("\t");
        //            String optionCode = split[1];
        //            int _2_index = optionCode.indexOf("2");
        //            String stock = optionCode.substring(2, _2_index);
        //
        //            if (!linesMap.containsKey(stock)) {
        //                linesMap.put(stock, Lists.newArrayList());
        //            }
        //
        //            linesMap.get(stock).add(line);
        //        }
        //
        //        for (String stock : linesMap.keySet()) {
        //            BaseUtils.writeFile(USER_PATH + "optionData/optionDaily/" + stock, linesMap.get(stock));
        //        }

        //        boolean b1 = canTradeForIv(Lists.newArrayList(0.469416, 0.475573, 0.518379, 0.497572, 0.535959));
        //        boolean b2 = canTradeForIv(Lists.newArrayList(0.444911, 0.51388, 0.49832, 0.482975, 0.580102));
        //        System.out.println(b1&&b2);
    }
}
