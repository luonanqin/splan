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
import com.google.common.collect.Sets;
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
 * 测试结果1：2024年1月-5月23日，交易10次，成本1w，收益约2.3w
 * 测试结果2：2023年1月-5月24日，交易17次，成本1w，收益约7.2w
 * 测试结果3：2022年1月-5月23日，交易24次，成本1w，收益约3.8k
 */
public class Strategy32 {

    public static BlockingQueue<CloseableHttpClient> queue;
    public static ThreadPoolExecutor cachedThread;
    public static String apiKey = "&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY";
    /* 2024 */
    //    public static int[] weekArray = new int[] { 20240105, 20240112, 20240119, 20240126, 20240202, 20240209, 20240216, 20240223, 20240301, 20240308, 20240315, 20240322, 20240328, 20240405, 20240412, 20240419, 20240426, 20240503, 20240510, 20240517, 20240524 };
    //    public static String[] weekStrArray = new String[] { "2024-01-05", "2024-01-12", "2024-01-19", "2024-01-26", "2024-02-02", "2024-02-09", "2024-02-16", "2024-02-23", "2024-03-01", "2024-03-08", "2024-03-15", "2024-03-22", "2024-03-28", "2024-04-05", "2024-04-12", "2024-04-19", "2024-04-26", "2024-05-03", "2024-05-10", "2024-05-17", "2024-05-24" };
    //    public static Set<String> weekSet = Sets.newHashSet("2024-01-05", "2024-01-12", "2024-01-19", "2024-01-26", "2024-02-02", "2024-02-09", "2024-02-16", "2024-02-23", "2024-03-01", "2024-03-08", "2024-03-15", "2024-03-22", "2024-03-28", "2024-04-05", "2024-04-12", "2024-04-19", "2024-04-26", "2024-05-03", "2024-05-10", "2024-05-17", "2024-05-24");
    /* 2023*/
    //    public static int[] weekArray = new int[] { 20230106, 20230113, 20230120, 20230127, 20230203, 20230210, 20230217, 20230224, 20230303, 20230310, 20230317, 20230324, 20230331, 20230406, 20230414, 20230421, 20230428, 20230505, 20230512, 20230519, 20230526, 20230602, 20230609, 20230616, 20230623, 20230630, 20230707, 20230714, 20230721, 20230728, 20230804, 20230811, 20230818, 20230825, 20230901, 20230908, 20230915, 20230922, 20230929, 20231006, 20231013, 20231020, 20231027, 20231103, 20231110, 20231117, 20231124, 20231201, 20231208, 20231215, 20231222, 20231229 };
    //    public static String[] weekStrArray = new String[] { "2023-01-06", "2023-01-13", "2023-01-20", "2023-01-27", "2023-02-03", "2023-02-10", "2023-02-17", "2023-02-24", "2023-03-03", "2023-03-10", "2023-03-17", "2023-03-24", "2023-03-31", "2023-04-06", "2023-04-14", "2023-04-21", "2023-04-28", "2023-05-05", "2023-05-12", "2023-05-19", "2023-05-26", "2023-06-02", "2023-06-09", "2023-06-16", "2023-06-23", "2023-06-30", "2023-07-07", "2023-07-14", "2023-07-21", "2023-07-28", "2023-08-04", "2023-08-11", "2023-08-18", "2023-08-25", "2023-09-01", "2023-09-08", "2023-09-15", "2023-09-22", "2023-09-29", "2023-10-06", "2023-10-13", "2023-10-20", "2023-10-27", "2023-11-03", "2023-11-10", "2023-11-17", "2023-11-24", "2023-12-01", "2023-12-08", "2023-12-15", "2023-12-22", "2023-12-29" };
    //    public static Set<String> weekSet = Sets.newHashSet("2023-01-06", "2023-01-13", "2023-01-20", "2023-01-27", "2023-02-03", "2023-02-10", "2023-02-17", "2023-02-24", "2023-03-03", "2023-03-10", "2023-03-17", "2023-03-24", "2023-03-31", "2023-04-06", "2023-04-14", "2023-04-21", "2023-04-28", "2023-05-05", "2023-05-12", "2023-05-19", "2023-05-26", "2023-06-02", "2023-06-09", "2023-06-16", "2023-06-23", "2023-06-30", "2023-07-07", "2023-07-14", "2023-07-21", "2023-07-28", "2023-08-04", "2023-08-11", "2023-08-18", "2023-08-25", "2023-09-01", "2023-09-08", "2023-09-15", "2023-09-22", "2023-09-29", "2023-10-06", "2023-10-13", "2023-10-20", "2023-10-27", "2023-11-03", "2023-11-10", "2023-11-17", "2023-11-24", "2023-12-01", "2023-12-08", "2023-12-15", "2023-12-22", "2023-12-29");
    /* 2022 */
    public static int[] weekArray = new int[] { 20220107, 20220114, 20220121, 20220128, 20220204, 20220211, 20220218, 20220225, 20220304, 20220311, 20220318, 20220325, 20220401, 20220408, 20220414, 20220422, 20220429, 20220506, 20220513, 20220520, 20220527, 20220603, 20220610, 20220617, 20220624, 20220701, 20220708, 20220715, 20220722, 20220729, 20220805, 20220812, 20220819, 20220826, 20220902, 20220909, 20220916, 20220923, 20220930, 20221007, 20221014, 20221021, 20221028, 20221104, 20221111, 20221118, 20221125, 20221202, 20221209, 20221216, 20221223, 20221230 };
    public static String[] weekStrArray = new String[] { "2022-01-07", "2022-01-14", "2022-01-21", "2022-01-28", "2022-02-04", "2022-02-11", "2022-02-18", "2022-02-25", "2022-03-04", "2022-03-11", "2022-03-18", "2022-03-25", "2022-04-01", "2022-04-08", "2022-04-14", "2022-04-22", "2022-04-29", "2022-05-06", "2022-05-13", "2022-05-20", "2022-05-27", "2022-06-03", "2022-06-10", "2022-06-17", "2022-06-24", "2022-07-01", "2022-07-08", "2022-07-15", "2022-07-22", "2022-07-29", "2022-08-05", "2022-08-12", "2022-08-19", "2022-08-26", "2022-09-02", "2022-09-09", "2022-09-16", "2022-09-23", "2022-09-30", "2022-10-07", "2022-10-14", "2022-10-21", "2022-10-28", "2022-11-04", "2022-11-11", "2022-11-18", "2022-11-25", "2022-12-02", "2022-12-09", "2022-12-16", "2022-12-23", "2022-12-30" };
    public static Set<String> weekSet = Sets.newHashSet("2022-01-07", "2022-01-14", "2022-01-21", "2022-01-28", "2022-02-04", "2022-02-11", "2022-02-18", "2022-02-25", "2022-03-04", "2022-03-11", "2022-03-18", "2022-03-25", "2022-04-01", "2022-04-08", "2022-04-14", "2022-04-22", "2022-04-29", "2022-05-06", "2022-05-13", "2022-05-20", "2022-05-27", "2022-06-03", "2022-06-10", "2022-06-17", "2022-06-24", "2022-07-01", "2022-07-08", "2022-07-15", "2022-07-22", "2022-07-29", "2022-08-05", "2022-08-12", "2022-08-19", "2022-08-26", "2022-09-02", "2022-09-09", "2022-09-16", "2022-09-23", "2022-09-30", "2022-10-07", "2022-10-14", "2022-10-21", "2022-10-28", "2022-11-04", "2022-11-11", "2022-11-18", "2022-11-25", "2022-12-02", "2022-12-09", "2022-12-16", "2022-12-23", "2022-12-30");
    public static Map<String/* stock */, Map<String/* date */, Map<String/* optionCode */, OptionDaily>>> stockOptionDailyMap = Maps.newHashMap();
    public static Map<String/* stock */, Map<Integer/* 档位 */, Double/* 振幅均值 */>> stockToVolatilityMap = Maps.newHashMap();
    public static Map<String/* date */, String/* lastDate */> dateMap = Maps.newHashMap(); // 当日和前日的映射

    public static void init() throws Exception {
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

        List<StockKLine> stockKLines = BaseUtils.loadDataToKline(Constants.HIS_BASE_PATH + "merge/AAPL", 2023, 2021);
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
        //        Map<String, String> klineFileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "2024/dailyKLine");
        //        int year = 2024;
        Map<String, String> klineFileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "merge");
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

            BaseUtils.writeFile(Constants.USER_PATH + "optionData/nearlyOpenOption2022/" + stock, result);
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
        Map<String, List<NearlyOptionData>> dateToOpenStrikePriceRatioMap = Maps.newTreeMap(Comparator.comparing(BaseUtils::dateToInt));
        Map<String, String> nearlyOptionFileMap = BaseUtils.getFileMap(Constants.USER_PATH + "optionData/nearlyOpenOption2022");

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
            get.releaseConnection();
        }
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
                    synchronized (stockOptionDailyMap) {
                        optionDaily = getOptionDaily(daily.getSymbol(), date);
                    }
                    if (optionDaily == null) {
                        optionDaily = requestOptionDailyList(httpClient, date, daily.getSymbol());
                        synchronized (stockOptionDailyMap) {
                            writeOptionDaily(optionDaily, daily.getSymbol(), date);
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

    public static Map<String, Map<String, OptionDaily>> loadDailyMap(String stock) throws Exception {
        Map<String/* date */, Map<String/* optionCode */, OptionDaily>> dateToOptionDailyMap = Maps.newHashMap();

        List<String> lines = BaseUtils.readFile(Constants.USER_PATH + "optionData/optionDaily/" + stock);
        if (CollectionUtils.isEmpty(lines)) {
            return Maps.newHashMap();
        }
        // 2024-01-02	O:AR240105P00022500	0.33	0.35	0.3	0.34	135
        for (String line : lines) {
            String[] split = line.split("\t");
            String date = split[0];
            String code = split[1];
            double open = Double.parseDouble(split[2]);
            double high = Double.parseDouble(split[3]);
            double low = Double.parseDouble(split[4]);
            double close = Double.parseDouble(split[5]);
            long volume = Long.parseLong(split[6]);
            OptionDaily optionDaily = new OptionDaily();
            optionDaily.setFrom(date);
            optionDaily.setSymbol(code);
            optionDaily.setOpen(open);
            optionDaily.setClose(close);
            optionDaily.setHigh(high);
            optionDaily.setLow(low);
            optionDaily.setVolume(volume);

            if (!dateToOptionDailyMap.containsKey(date)) {
                dateToOptionDailyMap.put(date, Maps.newHashMap());
            }
            dateToOptionDailyMap.get(date).put(code, optionDaily);
        }

        return dateToOptionDailyMap;
    }

    public static OptionDaily getOptionDaily(String optionCode, String date) throws Exception {
        int _2_index = optionCode.indexOf("2");
        String stock = optionCode.substring(2, _2_index);
        if (!stockOptionDailyMap.containsKey(stock)) {
            stockOptionDailyMap.put(stock, loadDailyMap(stock));
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

    public static void writeOptionDaily(OptionDaily optionDaily, String optionCode, String date) throws Exception {
        if (optionDaily == null) {
            optionDaily = OptionDaily.EMPTY(date, optionCode);
        }

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

        List<String> lines = BaseUtils.readFile(Constants.USER_PATH + "optionData/optionDaily/" + stock);
        lines.add(optionDaily.toString());
        BaseUtils.writeFile(Constants.USER_PATH + "optionData/optionDaily/" + stock, lines);
    }

    private static void calCallWithProtect() throws Exception {
        List<String> lines = BaseUtils.readFile(Constants.USER_PATH + "optionData/裸买和带保护");
        Map<String, List<Double>> dateToCall = Maps.newTreeMap(Comparator.comparing(BaseUtils::formatDateToInt));
        Map<String, List<Double>> dateToCallP = Maps.newTreeMap(Comparator.comparing(BaseUtils::formatDateToInt));
        for (String line : lines) {
            String[] split = line.split("\t");
            String date = split[0];
            double call = Double.parseDouble(split[1]);
            double callP = Double.parseDouble(split[2]);

            if (!dateToCall.containsKey(date)) {
                dateToCall.put(date, Lists.newArrayList());
            }
            dateToCall.get(date).add(call);

            if (!dateToCallP.containsKey(date)) {
                dateToCallP.put(date, Lists.newArrayList());
            }
            dateToCallP.get(date).add(callP);
        }

        double init = 10000;
        for (String date : dateToCall.keySet()) {
            List<Double> doubles = dateToCall.get(date);
            Double avgRatio = doubles.stream().collect(Collectors.averagingDouble(d -> d));
            init = init * (1 + avgRatio / 100);
            System.out.println(date + "\t" + init);
        }

        System.out.println();

        init = 10000;
        for (String date : dateToCallP.keySet()) {
            List<Double> doubles = dateToCallP.get(date);
            Double avgRatio = doubles.stream().collect(Collectors.averagingDouble(d -> d));
            init = init * (1 + avgRatio / 100);
            System.out.println(date + "\t" + init);
        }
    }

    private static StockKLine getLastKLine(String date, String stock) throws Exception {
        List<StockKLine> stockKLines = BaseUtils.loadDataToKline(Constants.HIS_BASE_PATH + "merge/" + stock, 2024, 2021);
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
        List<BOLL> bolls = BaseUtils.readBollFile(Constants.HIS_BASE_PATH + "mergeBoll/" + stock, 2024, 2021);
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

                System.out.println(stock + "\t" + openPrice + "\t" + lastClose + "\t" + bollCrossOpenClose + "\t" + avgAmplitude + "\t" + straddleOption);
            }
        }
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
                    break;
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

            List<StockKLine> stockKLines = BaseUtils.loadDataToKline(Constants.HIS_BASE_PATH + "2023daily/" + stock, year, year - 1);
            if (CollectionUtils.isEmpty(stockKLines)) {
                continue;
            }
            List<BOLL> bolls = BaseUtils.readBollFile(Constants.HIS_BASE_PATH + "mergeBoll/" + stock, year, year - 1);
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
        List<String> lines = BaseUtils.readFile(Constants.USER_PATH + "optionData/crossBollAmplitude");
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
//        list.add("2024-02-15	O:MARA240216C00031000");
//        list.add("2024-02-20	O:CVNA240223C00052000");
//        list.add("2024-02-22	O:DKNG240223C00042500");
//        list.add("2024-03-04	O:SE240308C00057000");
//        list.add("2024-03-06	O:AFRM240308C00037500");
//        list.add("2024-03-06	O:MSTR240308C01150000");
//        list.add("2024-04-25	O:AI240426C00022000");
//        list.add("2024-04-25	O:TSM240426C00131000");
//        list.add("2024-04-29	O:PARA240503C00013000");
//        list.add("2024-05-16	O:JD240517C00035000");
//        list.add("2024-01-24	O:WDC240126P00058000");
//        list.add("2024-02-13	O:SMCI240216P00745000");
//        list.add("2024-02-15	O:MARA240216P00029000");
//        list.add("2024-02-20	O:CVNA240223P00050000");
//        list.add("2024-02-22	O:DKNG240223P00041500");
//        list.add("2024-03-04	O:SE240308P00055000");
//        list.add("2024-03-06	O:AFRM240308P00036500");
//        list.add("2024-03-06	O:MSTR240308P01130000");
//        list.add("2024-04-25	O:AI240426P00021000");
//        list.add("2024-04-25	O:TSM240426P00129000");
        list.add("2024-04-29	O:PARA240503P00012000");
        list.add("2024-05-16	O:JD240517P00034000");
        for (String l : list) {
            String[] split = l.split("\t");
            String date = split[0];
            String code = split[1];
            OptionCode optionCode = new OptionCode();
            optionCode.setCode(code);
            Strategy28.getOptionQuoteList(optionCode, date);
            System.out.println("finish " + l);
        }
    }

    public static void main(String[] args) throws Exception {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.http").setLevel(Level.INFO);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.INFO);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.commons").setLevel(Level.ERROR);

        init();

        //        getHasWeekOptionStock(); // 获取有周期权的股票
        //        getEqualsStrikePriceKline(); // 获取某个时间范围内，股票开盘价对应的最近行权价代码
        //        calStraddleData(); // 获取双开期权的optionDaily数据
        //        getStraddleLastDaily(); // 获取双开期权当天前一日的optionDaily数据
        //        loadHistoricalHighVolatility();
        //        calHistoricalHighVolatility();
        //        calCallWithProtect(); // 计算裸买和带保护的收益
        getOptionQuoteList(); // 获取已过滤出的代码的报价，用于计算止损

        //        List<String> lines = BaseUtils.readFile(Constants.USER_PATH + "optionData/stockOpenDate");
        //        for (String line : lines) {
        //            String[] split = line.split("\t");
        //            String date = split[0];
        //            String stock = split[1];
        //            double close = getLastClose(date, stock);
        //            System.out.println(date + "\t" + stock + "\t" + close);
        //        }

        //        List<String> lines = BaseUtils.readFile(Constants.USER_PATH + "optionData/dailydata");
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
        //            BaseUtils.writeFile(Constants.USER_PATH + "optionData/optionDaily/" + stock, linesMap.get(stock));
        //        }

    }
}
