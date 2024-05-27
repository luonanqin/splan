package luonq.strategy;

import bean.NearlyOptionData;
import bean.OptionCode;
import bean.OptionContracts;
import bean.OptionContractsResp;
import bean.OptionDaily;
import bean.StockKLine;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.collections.list.SynchronizedList;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.LoggerFactory;
import util.BaseUtils;
import util.Constants;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
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

/**
 * 1.以05-23号的收盘价作为输入，获取24号的期权代码
 * 2.以每天的开盘价找到离开盘价最近的行权价call，以及行权价之间的价格步长。每个股票一个文件
 * 3.计算每值股票每天的开盘价和最近期权行权价的差值，从小到大排序
 * 4.从差值最小的开始，计算对应期权代码前后两个行权价的代码
 */
public class Strategy32 {

    public static BlockingQueue<CloseableHttpClient> queue;
    public static ThreadPoolExecutor cachedThread;
    public static String apiKey = "&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY";
    public static int[] weekArray = new int[] { 20240105, 20240112, 20240119, 20240126, 20240202, 20240209, 20240216, 20240223, 20240301, 20240308, 20240315, 20240322, 20240328, 20240405, 20240412, 20240419, 20240426, 20240503, 20240510, 20240517, 20240524 };
    public static String[] weekStrArray = new String[] { "2024-01-05", "2024-01-12", "2024-01-19", "2024-01-26", "2024-02-02", "2024-02-09", "2024-02-16", "2024-02-23", "2024-03-01", "2024-03-08", "2024-03-15", "2024-03-22", "2024-03-28", "2024-04-05", "2024-04-12", "2024-04-19", "2024-04-26", "2024-05-03", "2024-05-10", "2024-05-17", "2024-05-24" };

    public static void init() {
        int threadCount = 100;
        int corePoolSize = threadCount;
        int maximumPoolSize = corePoolSize;
        long keepAliveTime = 60L;
        LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
        cachedThread = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS, workQueue);
        queue = new LinkedBlockingQueue<>(threadCount);
        for (int i = 0; i < threadCount; i++) {
            queue.offer(HttpClients.createDefault());
        }
    }

    public static List<String> getOptionCode(CloseableHttpClient httpClient, String code, double price, String date) throws Exception {
        int decade = (int) price / 10;
        int upPrice = (decade + 1) * 10;
        int downPrice = (decade == 0 ? 1 : decade) * 10;

        LocalDate today = LocalDate.now();
        LocalDate day = LocalDate.parse(date, Constants.DB_DATE_FORMATTER);
        String upDate = day.plusMonths(2).withDayOfMonth(1).format(Constants.DB_DATE_FORMATTER);

        boolean expired = true;
        if (today.isBefore(day.plusDays(30))) {
            expired = false;
            upDate = day.plusMonths(1).withDayOfMonth(1).format(Constants.DB_DATE_FORMATTER);
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
        Map<String, String> klineFileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "2024/dailyKLine");
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
        Map<String, String> klineFileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "2024/dailyKLine");
        int year = 2024;
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

            BaseUtils.writeFile(Constants.USER_PATH + "optionData/nearlyOpenOption/" + stock, result);
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

    public static Map<String/* date */, List<NearlyOptionData>> calOpenStrikePriceRatioMap() throws Exception {
        Map<String, List<NearlyOptionData>> dateToOpenStrikePriceRatioMap = Maps.newHashMap();
        Map<String, String> nearlyOptionFileMap = BaseUtils.getFileMap(Constants.USER_PATH + "optionData/nearlyOpenOption");

        for (String stock : nearlyOptionFileMap.keySet()) {
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
                StringBuffer openPriceSb = new StringBuffer(openPrice.replace(".", ""));
                while (openPriceSb.length() < strikePriceLength) {
                    openPriceSb.append("0");
                }
                int openPriceDigital = Integer.valueOf(openPriceSb.toString());
                double priceDiffRatio = Math.abs(1 - (double) openPriceDigital / (double) strikePrice);

                // 计算行权价前后各两档的虚值期权代码
                String optionPrefix = optionCode.substring(0, index + i);
                int call_1 = strikePrice + optionPriceStep;
                int call_2 = strikePrice + optionPriceStep * 2;
                int put_1 = strikePrice - optionPriceStep;
                int put_2 = strikePrice - optionPriceStep * 2;
                String optionCode_call1 = optionPrefix + call_1;
                String optionCode_call2 = optionPrefix + call_2;
                String optionCode_put1 = BaseUtils.getOptionPutCode(optionPrefix + put_1);
                String optionCode_put2 = BaseUtils.getOptionPutCode(optionPrefix + put_2);

                NearlyOptionData nearlyOptionData = new NearlyOptionData();
                nearlyOptionData.setDate(date);
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

    private static OptionDaily requestOptionDailyList(CloseableHttpClient httpClient, String date, String code) throws IOException {
        String url = String.format("https://api.polygon.io/v1/open-close/%s/%s?adjusted=true&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY",
          code, date);

        HttpGet get = new HttpGet(url);
        try {
            CloseableHttpResponse execute = httpClient.execute(get);
            InputStream stream = execute.getEntity().getContent();
            OptionDaily resp = JSON.parseObject(stream, OptionDaily.class);
            String status = resp.getStatus();
            if (!StringUtils.equalsIgnoreCase(status, "OK")) {
                System.out.println("get failed. " + url);
                return null;
            }

            return resp;
        } finally {
            queue.offer(httpClient);
            get.releaseConnection();
        }
    }

    public static void main(String[] args) throws Exception {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.http").setLevel(Level.INFO);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.INFO);

        init();

        //        getHasWeekOptionStock();
        //        getEqualsStrikePriceKline();
        Map<String, List<NearlyOptionData>> dateOpenStrikePriceRatioMap = calOpenStrikePriceRatioMap();
    }
}
