package luonq.strategy;

import bean.EarningDate;
import bean.NearlyOptionData;
import bean.OptionDaily;
import bean.StockKLine;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import luonq.ivolatility.GetAggregateImpliedVolatility;
import luonq.ivolatility.GetDailyImpliedVolatility;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.LoggerFactory;
import util.BaseUtils;
import util.Constants;

import java.io.File;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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

public class Strategy33 {

    public static BlockingQueue<HttpClient> queue;
    public static ThreadPoolExecutor cachedThread;
    public static String apiKey = "&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY";
    /* 2024 */
    public static int[] weekArray = new int[] { 20240105, 20240112, 20240119, 20240126, 20240202, 20240209, 20240216, 20240223, 20240301, 20240308, 20240315, 20240322, 20240328, 20240405, 20240412, 20240419, 20240426, 20240503, 20240510, 20240517, 20240524, 20240531, 20240607, 20240614, 20240621, 20240628, 20240705, 20240712, 20240719 };
    public static String[] weekStrArray = new String[] { "2024-01-05", "2024-01-12", "2024-01-19", "2024-01-26", "2024-02-02", "2024-02-09", "2024-02-16", "2024-02-23", "2024-03-01", "2024-03-08", "2024-03-15", "2024-03-22", "2024-03-28", "2024-04-05", "2024-04-12", "2024-04-19", "2024-04-26", "2024-05-03", "2024-05-10", "2024-05-17", "2024-05-24", "2024-05-31", "2024-06-07", "2024-06-14", "2024-06-21", "2024-06-28", "2024-07-05", "2024-07-12", "2024-07-19" };
    public static Set<String> weekSet = Sets.newHashSet("2024-01-05", "2024-01-12", "2024-01-19", "2024-01-26", "2024-02-02", "2024-02-09", "2024-02-16", "2024-02-23", "2024-03-01", "2024-03-08", "2024-03-15", "2024-03-22", "2024-03-28", "2024-04-05", "2024-04-12", "2024-04-19", "2024-04-26", "2024-05-03", "2024-05-10", "2024-05-17", "2024-05-24", "2024-05-31", "2024-06-07", "2024-06-14", "2024-06-21", "2024-06-28", "2024-07-05", "2024-07-12", "2024-07-19");
    public static int year = 2024;
    /* 2023*/
    //            public static int[] weekArray = new int[] { 20230106, 20230113, 20230120, 20230127, 20230203, 20230210, 20230217, 20230224, 20230303, 20230310, 20230317, 20230324, 20230331, 20230406, 20230414, 20230421, 20230428, 20230505, 20230512, 20230519, 20230526, 20230602, 20230609, 20230616, 20230623, 20230630, 20230707, 20230714, 20230721, 20230728, 20230804, 20230811, 20230818, 20230825, 20230901, 20230908, 20230915, 20230922, 20230929, 20231006, 20231013, 20231020, 20231027, 20231103, 20231110, 20231117, 20231124, 20231201, 20231208, 20231215, 20231222, 20231229 };
    //            public static String[] weekStrArray = new String[] { "2023-01-06", "2023-01-13", "2023-01-20", "2023-01-27", "2023-02-03", "2023-02-10", "2023-02-17", "2023-02-24", "2023-03-03", "2023-03-10", "2023-03-17", "2023-03-24", "2023-03-31", "2023-04-06", "2023-04-14", "2023-04-21", "2023-04-28", "2023-05-05", "2023-05-12", "2023-05-19", "2023-05-26", "2023-06-02", "2023-06-09", "2023-06-16", "2023-06-23", "2023-06-30", "2023-07-07", "2023-07-14", "2023-07-21", "2023-07-28", "2023-08-04", "2023-08-11", "2023-08-18", "2023-08-25", "2023-09-01", "2023-09-08", "2023-09-15", "2023-09-22", "2023-09-29", "2023-10-06", "2023-10-13", "2023-10-20", "2023-10-27", "2023-11-03", "2023-11-10", "2023-11-17", "2023-11-24", "2023-12-01", "2023-12-08", "2023-12-15", "2023-12-22", "2023-12-29" };
    //            public static Set<String> weekSet = Sets.newHashSet("2023-01-06", "2023-01-13", "2023-01-20", "2023-01-27", "2023-02-03", "2023-02-10", "2023-02-17", "2023-02-24", "2023-03-03", "2023-03-10", "2023-03-17", "2023-03-24", "2023-03-31", "2023-04-06", "2023-04-14", "2023-04-21", "2023-04-28", "2023-05-05", "2023-05-12", "2023-05-19", "2023-05-26", "2023-06-02", "2023-06-09", "2023-06-16", "2023-06-23", "2023-06-30", "2023-07-07", "2023-07-14", "2023-07-21", "2023-07-28", "2023-08-04", "2023-08-11", "2023-08-18", "2023-08-25", "2023-09-01", "2023-09-08", "2023-09-15", "2023-09-22", "2023-09-29", "2023-10-06", "2023-10-13", "2023-10-20", "2023-10-27", "2023-11-03", "2023-11-10", "2023-11-17", "2023-11-24", "2023-12-01", "2023-12-08", "2023-12-15", "2023-12-22", "2023-12-29");
    //            public static int year = 2023;
    /* 2022 */
    //            public static int[] weekArray = new int[] { 20220107, 20220114, 20220121, 20220128, 20220204, 20220211, 20220218, 20220225, 20220304, 20220311, 20220318, 20220325, 20220401, 20220408, 20220414, 20220422, 20220429, 20220506, 20220513, 20220520, 20220527, 20220603, 20220610, 20220617, 20220624, 20220701, 20220708, 20220715, 20220722, 20220729, 20220805, 20220812, 20220819, 20220826, 20220902, 20220909, 20220916, 20220923, 20220930, 20221007, 20221014, 20221021, 20221028, 20221104, 20221111, 20221118, 20221125, 20221202, 20221209, 20221216, 20221223, 20221230 };
    //            public static String[] weekStrArray = new String[] { "2022-01-07", "2022-01-14", "2022-01-21", "2022-01-28", "2022-02-04", "2022-02-11", "2022-02-18", "2022-02-25", "2022-03-04", "2022-03-11", "2022-03-18", "2022-03-25", "2022-04-01", "2022-04-08", "2022-04-14", "2022-04-22", "2022-04-29", "2022-05-06", "2022-05-13", "2022-05-20", "2022-05-27", "2022-06-03", "2022-06-10", "2022-06-17", "2022-06-24", "2022-07-01", "2022-07-08", "2022-07-15", "2022-07-22", "2022-07-29", "2022-08-05", "2022-08-12", "2022-08-19", "2022-08-26", "2022-09-02", "2022-09-09", "2022-09-16", "2022-09-23", "2022-09-30", "2022-10-07", "2022-10-14", "2022-10-21", "2022-10-28", "2022-11-04", "2022-11-11", "2022-11-18", "2022-11-25", "2022-12-02", "2022-12-09", "2022-12-16", "2022-12-23", "2022-12-30" };
    //            public static Set<String> weekSet = Sets.newHashSet("2022-01-07", "2022-01-14", "2022-01-21", "2022-01-28", "2022-02-04", "2022-02-11", "2022-02-18", "2022-02-25", "2022-03-04", "2022-03-11", "2022-03-18", "2022-03-25", "2022-04-01", "2022-04-08", "2022-04-14", "2022-04-22", "2022-04-29", "2022-05-06", "2022-05-13", "2022-05-20", "2022-05-27", "2022-06-03", "2022-06-10", "2022-06-17", "2022-06-24", "2022-07-01", "2022-07-08", "2022-07-15", "2022-07-22", "2022-07-29", "2022-08-05", "2022-08-12", "2022-08-19", "2022-08-26", "2022-09-02", "2022-09-09", "2022-09-16", "2022-09-23", "2022-09-30", "2022-10-07", "2022-10-14", "2022-10-21", "2022-10-28", "2022-11-04", "2022-11-11", "2022-11-18", "2022-11-25", "2022-12-02", "2022-12-09", "2022-12-16", "2022-12-23", "2022-12-30");
    //            public static int year = 2022;
    public static Map<String/* today */, List<String>/* last5Days*/> last5DaysMap = Maps.newHashMap();
    public static Map<String, Map<String, Double>> ivMap = Maps.newHashMap();
    public static Map<String/* date */, Double/* rate */> riskFreeRateMap = Maps.newHashMap();
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

        List<StockKLine> stockKLines = BaseUtils.loadDataToKline(Constants.HIS_BASE_PATH + "merge/AAPL", 2024, 2020);
        List<String> dateList = stockKLines.stream().map(StockKLine::getFormatDate).collect(Collectors.toList());
        for (int i = 0; i < dateList.size() - 6; i++) {
            last5DaysMap.put(dateList.get(i), Lists.newArrayList(dateList.get(i + 1), dateList.get(i + 2), dateList.get(i + 3), dateList.get(i + 4), dateList.get(i + 5)));
        }

        loadIv();
        riskFreeRateMap = BaseUtils.loadRiskFreeRate();
    }

    private static void loadIv() throws Exception {
        Map<String, String> ivDirMap = BaseUtils.getFileMap(Constants.USER_PATH + "optionData/IV/");
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

    public static List<String> getEarningData(HttpClient httpClient, String date) throws Exception {
        GetMethod get = new GetMethod("https://api.nasdaq.com/api/calendar/earnings?date=" + date);
        get.addRequestHeader("authority", "api.nasdaq.com");
        get.addRequestHeader("accept", "application/json, text/plain, */*");
        get.addRequestHeader("user-agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.116 Safari/537.36");
        get.addRequestHeader("origin", "https://www.nasdaq.com");
        get.addRequestHeader("sec-fetch-site", "same-site");
        get.addRequestHeader("sec-fetch-mode", "cors");
        get.addRequestHeader("sec-fetch-dest", "empty");
        get.addRequestHeader("referer", "https://www.nasdaq.com/");
        get.addRequestHeader("accept-language", "en-US,en;q=0.9");
        try {

            httpClient.executeMethod(get);
            InputStream stream = get.getResponseBodyAsStream();
            Map<String, Object> result = JSON.parseObject(stream, Map.class);
            Map<String, Object> data = (Map<String, Object>) MapUtils.getObject(result, "data");
            List<Map<String, String>> rows = (List<Map<String, String>>) MapUtils.getObject(data, "rows");

            List<String> codeList = rows.stream().map(k -> k.get("symbol")).collect(Collectors.toList());
            return codeList;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            get.releaseConnection();
        }

        return Lists.newArrayListWithExpectedSize(0);
    }

    public static Map<String/* date */, List<String>/* stock */> getEarningForEveryDayFromNasdaq() throws Exception {
        Set<String> weekOptionStock = BaseUtils.getWeekOptionStock();
        List<StockKLine> stockKLines = BaseUtils.loadDataToKline(Constants.HIS_BASE_PATH + "merge/AAPL", year, year - 1);
        Map<String, List<String>> result = Maps.newHashMap();
        Map<String, List<String>> dateToStocks = Maps.newHashMap();
        CountDownLatch cdl = new CountDownLatch(stockKLines.size() - 3);
        for (int i = 3; i < stockKLines.size(); i++) {
            String date = stockKLines.get(i).getFormatDate();
            HttpClient httpClient = queue.take();
            cachedThread.execute(() -> {
                List<String> stocks = null;
                try {
                    stocks = getEarningData(httpClient, date);
                    System.out.println(date + "\t" + stocks.size());
                    dateToStocks.put(date, stocks);
                    Thread.sleep(1000);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    queue.offer(httpClient);
                    cdl.countDown();
                }
            });
        }

        cdl.await();

        for (int i = 3; i < stockKLines.size(); i++) {
            String date = stockKLines.get(i).getFormatDate();
            String date_after1 = stockKLines.get(i - 1).getFormatDate();
            String date_after2 = stockKLines.get(i - 2).getFormatDate();
            String date_after3 = stockKLines.get(i - 3).getFormatDate();

            List<String> earning = dateToStocks.get(date);
            List<String> earning_1 = dateToStocks.get(date_after1);
            List<String> earning_2 = dateToStocks.get(date_after2);
            List<String> earning_3 = dateToStocks.get(date_after3);

            Set<String> stockSet = Sets.newHashSet();
            if (earning != null) {
                earning.stream().filter(s -> weekOptionStock.contains(s)).forEach(s -> stockSet.add(s));
            }
            if (earning_1 != null) {
                earning_1.stream().filter(s -> weekOptionStock.contains(s)).forEach(s -> stockSet.add(s));
            }
            if (earning_2 != null) {
                earning_2.stream().filter(s -> weekOptionStock.contains(s)).forEach(s -> stockSet.add(s));
            }
            if (earning_3 != null) {
                earning_3.stream().filter(s -> weekOptionStock.contains(s)).forEach(s -> stockSet.add(s));
            }

            result.put(date, Lists.newArrayList(stockSet));
        }
        return result;
    }

    public static Map<String/* date */, List<String>/* stock */> getEarningForEveryDay() throws Exception {
        Set<String> pennyOptionStock = BaseUtils.getPennyOptionStock();
        List<StockKLine> stockKLines = BaseUtils.loadDataToKline(Constants.HIS_BASE_PATH + "merge/AAPL", year, year - 1);
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
    // 获取开盘相对于前日收盘波动超过2%的kline

    public static Map<String/* date */, Map<String/* stock */, StockKLine>> getDateToStockKlineMap() throws Exception {
        Set<String> pennyOptionStock = BaseUtils.getPennyOptionStock();
        Map<String/* date */, Map<String/* stock */, StockKLine>> dateToStockKline = Maps.newTreeMap(Comparator.comparing(BaseUtils::formatDateToInt));
        for (String stock : pennyOptionStock) {
            List<StockKLine> stockKLines = BaseUtils.loadDataToKline(Constants.HIS_BASE_PATH + "merge/" + stock, year, year - 1);
            for (int i = 0; i < stockKLines.size() - 2; i++) {
                StockKLine stockKLine = stockKLines.get(i);
                StockKLine lastKLine = stockKLines.get(i + 1);
                double open = stockKLine.getOpen();
                double lastClose = lastKLine.getClose();
                double ratio = Math.abs(open - lastClose) / lastClose * 100;
                if (ratio < 3 || open < 5) {
                    continue;
                }
                String date = stockKLine.getFormatDate();
                if (!dateToStockKline.containsKey(date)) {
                    dateToStockKline.put(date, Maps.newHashMap());
                }

                dateToStockKline.get(date).put(stock, stockKLine);
            }
        }

        return dateToStockKline;
    }
    //    public static String getOptionCode() throws Exception {
    //        Map<String, List<NearlyOptionData>> dateToOpenStrikePriceRatioMap = Maps.newTreeMap(Comparator.comparing(BaseUtils::dateToInt));
    //        Map<String, String> nearlyOptionFileMap = BaseUtils.getFileMap(Constants.USER_PATH + "optionData/nearlyOpenOption/" + year);
    //
    //        for (String stock : nearlyOptionFileMap.keySet()) {
    //            String filePath = nearlyOptionFileMap.get(stock);
    //
    //            List<String> lines = BaseUtils.readFile(filePath);
    //            if (CollectionUtils.isEmpty(lines)) {
    //                continue;
    //            }
    //
    //            for (String line : lines) {
    //                String[] split = line.split("\t");
    //                String date = split[0];
    //                String openPrice = split[1];
    //                String optionCode = split[2];
    //                Integer optionPriceStep = Integer.valueOf(split[3]);
    //
    //                // 计算开盘价和行权价的差值
    //                int index = optionCode.length() - 8;
    //                String temp = optionCode.substring(index);
    //                int i;
    //                for (i = 0; i < temp.length(); i++) {
    //                    if (temp.charAt(i) != '0') {
    //                        break;
    //                    }
    //                }
    //                int strikePrice = Integer.parseInt(temp.substring(i));
    //                int strikePriceLength = temp.substring(i).length();
    //                StringBuffer openPriceSb = new StringBuffer(openPrice.replace(".", ""));
    //                while (openPriceSb.length() < strikePriceLength) {
    //                    openPriceSb.append("0");
    //                }
    //                int openPriceDigital = Integer.valueOf(openPriceSb.toString());
    //                double priceDiffRatio = Math.abs(1 - (double) openPriceDigital / (double) strikePrice);
    //            }
    //        }

    //    }

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

    public static Map<String/* optionCode */, List<Double>/* iv list */> loadIvMap() throws Exception {
        List<String> lines = BaseUtils.readFile(Constants.USER_PATH + "optionData/IV/" + year + "/IV3");
        Map<String, List<Double>> ivMap = Maps.newHashMap();
        for (String line : lines) {
            String[] split = line.split("\t");
            String optionCode = split[0];
            List<Double> ivList = Lists.newArrayList();
            for (int i = 1; i < split.length; i++) {
                if (i % 2 == 0) {
                    ivList.add(Double.valueOf(split[i]));
                }
            }
            ivMap.put(optionCode, ivList);
        }

        return ivMap;
    }

    public static void moveQuoteFile() throws Exception {
        Map<String, String> fileMap = BaseUtils.getFileMap(Constants.USER_PATH + "optionData/optionQuote");
        for (String filePath : fileMap.values()) {
            File file = new File(filePath);
            if (file.isFile()) {
                continue;
            }

            File[] files = file.listFiles();
            String stock = file.getName();
            if (!stock.equals("AA")) {
                //                continue;
            }

            for (File quote : files) {
                if (quote.isDirectory()) {
                    continue;
                }
                String name = quote.getName();
                List<String> lines = BaseUtils.readFile(quote);
                if (CollectionUtils.isEmpty(lines)) {
                    System.out.println(name + " has no data");
                    quote.delete();
                    continue;
                }
                String firstLine = lines.get(0);
                String lastLine = lines.get(lines.size() - 1);
                Long firstTime = Long.valueOf(firstLine.split("\t")[0]) / 1000000000;
                Long lastTime = Long.valueOf(lastLine.split("\t")[0]) / 1000000000;
                if (lastTime - firstTime > 23400) {
                    System.out.println(file.getAbsolutePath() + " has many data");
                    quote.delete();
                    continue;
                }
                LocalDateTime dateTime = LocalDateTime.ofEpochSecond(firstTime, 0, ZoneOffset.of("+8"));
                String date = dateTime.format(Constants.DB_DATE_FORMATTER);

                String newDirPath = Constants.USER_PATH + "optionData/optionQuote/" + stock + "/" + date;
                File newDir = new File(newDirPath);
                if (!newDir.exists()) {
                    newDir.mkdir();
                }
                String newFilePath = newDirPath + "/" + name;
                File newFile = new File(newFilePath);
                if (newFile.exists()) {
                    System.out.println(name + " has moved");
                    continue;
                }
                boolean result = quote.renameTo(newFile);
                //                BaseUtils.writeFile(newFilePath, lines);
                if (result) {
                    System.out.println(name + " finish moving");
                } else {
                    System.out.println(name + " moving failed");
                }
            }
        }
    }

    public static void moveIVFile() throws Exception {
        loadIv();

        List<String> lines = BaseUtils.readFile(Constants.USER_PATH + "optionData/IV/2024/IV2");
        for (String line : lines) {
            String[] split = line.split("\t");
            String optionCode = split[0];
            String code = optionCode.substring(2);

            if (!ivMap.containsKey(code)) {
                ivMap.put(code, Maps.newTreeMap(Comparator.comparing(BaseUtils::formatDateToInt)));
            }
            Map<String, Double> ivValueMap = ivMap.get(code);
            for (int i = 1; i < split.length; i = i + 2) {
                ivValueMap.put(split[i], Double.valueOf(split[i + 1]));
            }
        }

        //        List<String> lines2 = BaseUtils.readFile(Constants.USER_PATH + "optionData/IV/2023/IV3");
        //        for (String line : lines2) {
        //            String[] split = line.split("\t");
        //            String optionCode = split[0];
        //            String code = optionCode.substring(2);
        //
        //            if (!ivMap.containsKey(code)) {
        //                ivMap.put(code, Maps.newTreeMap(Comparator.comparing(BaseUtils::formatDateToInt)));
        //            }
        //            Map<String, Double> ivValueMap = ivMap.get(code);
        //            for (int i = 1; i < split.length; i = i + 2) {
        //                ivValueMap.put(split[i], Double.valueOf(split[i + 1]));
        //            }
        //        }

        for (String code : ivMap.keySet()) {
            int _2_index = code.indexOf("2");
            String stock = code.substring(0, _2_index);
            Map<String, Double> kv = ivMap.get(code);
            List<String> results = Lists.newArrayList();
            for (String k : kv.keySet()) {
                results.add(k + "\t" + kv.get(k));
            }
            File ivDir = new File(Constants.USER_PATH + "optionData/IV/" + stock);
            if (!ivDir.exists()) {
                ivDir.mkdirs();
            }
            BaseUtils.writeFile(Constants.USER_PATH + "optionData/IV/" + stock + "/" + code, results);
            System.out.println("finish " + code);
        }
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
        List<String> lines = BaseUtils.readFile(Constants.USER_PATH + "optionData/IV/" + stock + "/" + code);
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

        for (String stock : pennyOptionStock) {
            Map<String, String> fileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "sec120Aggregate/" + stock);
            map.put(stock, Maps.newHashMap());
            for (String date : fileMap.keySet()) {
                String filePah = fileMap.get(date);
                List<String> lines = BaseUtils.readFile(filePah);
                if (CollectionUtils.isNotEmpty(lines)) {
                    for (String line : lines) {
                        String[] split = line.split("\t");
                        String datetime = split[0];
                        Double price = Double.valueOf(split[5]);
                        map.get(stock).put(datetime, price);
                    }

                    LocalDateTime t = LocalDateTime.parse(lines.get(0).split("\t")[0], formatter);
                    Double price = map.get(stock).get(t);
                    for (int i = 0; i < 120; i++) {
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
                LocalDateTime day = LocalDate.parse(date, Constants.DB_DATE_FORMATTER).atTime(0, 0);
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

    // 按照改进过的双开期权代码计算，收益如下：
    // 2022年过滤iv过滤开盘小于0.1的收益16w（截止5.23号4.3w）,只过滤iv不过滤开盘小于0.1的收益13w（截止5.23号4.2w），只过滤前日call和put交易量都小于100的收益46w，都过滤收益29w，什么都不过滤收益24w
    // 2023年过滤iv过滤开盘小于0.1的收益4.6w（截止5.23号7.5k）,只过滤iv不过滤开盘小于0.1的收益9.5w（截止5.23号1.4w），只过滤前日call和put交易量都小于100的收益11w，都过滤收益18w，什么都不过滤收益2.3w
    // 2024年过滤iv过滤开盘小于0.1的收益5.2w（截止5.23号），只过滤iv不过滤开盘小于0.1的收益5.4w（截止5.23号）,只过滤前日call和put交易量都小于100的收益4w，都过滤收益3.5w，什么都不过滤收益3.3w

    // 按照改进前的代码计算，收益如下：
    // 2022年过滤iv过滤开盘小于0.1的收益188w（截止5.23号8w）,只过滤iv不过滤开盘小于0.1的收益151w（截止5.23号7.8w），只过滤前日call和put交易量都小于100的收益6.2w，都过滤收益6.5w，什么都不过滤收益222w
    // 2023年过滤iv过滤开盘小于0.1的收益15w（截止5.23号3w）,只过滤iv不过滤开盘小于0.1的收益30w（截止5.23号5w），只过滤前日call和put交易量都小于100的收益13w，都过滤收益43w，什么都不过滤收益2.7w
    // 2024年过滤iv过滤开盘小于0.1的收益5.1w（截止5.23号），只过滤iv不过滤开盘小于0.1的收益3w（截止5.23号）,只过滤前日call和put交易量都小于100的收益1.3w，都过滤收益1.8w，什么都不过滤收益2.4w

    // 每周最后一天没有计算，需要改进代码取下一周的期权来计算
    public static void main(String[] args) throws Exception {
        //        moveQuoteFile();
        //        moveIVFile();
        //        Strategy32.calCallWithProtect();

        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.http").setLevel(Level.INFO);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.INFO);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.commons").setLevel(Level.ERROR);

        init();
        Strategy28.init();
        Strategy32.init();
        GetDailyImpliedVolatility.init();
        GetAggregateImpliedVolatility.init();

        //                Map<String, List<String>> earningForEveryDay = getEarningForEveryDay2024();
        Map<String, List<String>> earningForEveryDay = getEarningForEveryDay();
        Map<String, Map<String, StockKLine>> dateToStockKlineMap = getDateToStockKlineMap();
//        Map<String, List<NearlyOptionData>> dateToOpenStrikePriceRatioMap = Strategy32.calOpenStrikePriceRatioMap();
        Map<String, Map<String, Double>> secToStockPriceMap = getSecToStockPriceMap();
        //        Map<String, List<Double>> ivMap = loadIvMap();

        for (String date : dateToStockKlineMap.keySet()) {
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
                expirationDate = LocalDate.parse(expirationDate, Constants.DB_DATE_FORMATTER).format(DateTimeFormatter.ofPattern("yyMMdd"));
            }

            if (!date.equals("2024-07-10")) {
//                continue;
            }

            List<String> earningStocks = earningForEveryDay.get(date);
            Map<String, StockKLine> stockKLineMap = dateToStockKlineMap.get(date);
            if (CollectionUtils.isEmpty(earningStocks)) {
                //                continue;
            }
//            List<NearlyOptionData> nearlyOptionDataList = dateToOpenStrikePriceRatioMap.get(BaseUtils.unformatDate(date));
//            Map<String, NearlyOptionData> stockToNearlyOption = nearlyOptionDataList.stream().collect(Collectors.toMap(NearlyOptionData::getStock, v -> v));
            //            Set<String> stockSet = earningStocks.stream().collect(Collectors.toSet());
            Set<String> stockSet = stockKLineMap.keySet();
            for (String stock : stockSet) {
                if (!stock.equals("PLTR")) {
//                                        continue;
                }
                if (stockKLineMap.containsKey(stock)) {
                    StockKLine stockKLine = stockKLineMap.get(stock);
                    //                    System.out.println(stock + "\t" + stockKLine);

                    double open = stockKLine.getOpen();
                    String formatDate = stockKLine.getFormatDate();

//                    NearlyOptionData nearlyOptionData = stockToNearlyOption.get(stock);
                    NearlyOptionData nearlyOptionData = Strategy32.calOpenStrikePrice(date, stock, open);
                    if (nearlyOptionData == null) {
                        continue;
                    }
//                    if (nearlyOptionData2 == null) {
//                        System.out.println("nearlyOptionData2 is null. stock=" + stock + " open=" + open);
//                        continue;
//                    }
                    String outPriceCallOptionCode_1 = nearlyOptionData.getOutPriceCallOptionCode_1();
                    String outPricePutOptionCode_1 = nearlyOptionData.getOutPricePutOptionCode_1();
                    if (StringUtils.isNotBlank(expirationDate)) {
                        StringBuffer callSb = new StringBuffer(outPriceCallOptionCode_1);
                        callSb.replace(callSb.length() - 15, callSb.length() - 9, expirationDate);
                        outPriceCallOptionCode_1 = callSb.toString();
                        StringBuffer putSb = new StringBuffer(outPricePutOptionCode_1);
                        putSb.replace(putSb.length() - 15, putSb.length() - 9, expirationDate);
                        outPricePutOptionCode_1 = putSb.toString();
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

                    String simulateTrade = "";
                    String ivInfo = "";
                    simulateTrade = Strategy32.calStraddleSimulateTrade(callDaily, putDaily, calCallOpen, calPutOpen);
                    if (StringUtils.equalsAnyIgnoreCase(simulateTrade, "noData", "empty")) {
                        continue;
                    }
                    String callIvInfo = StringUtils.join(Lists.reverse(callIvList.subList(0, 3)), "\t");
                    String putIvInfo = StringUtils.join(Lists.reverse(putIvList.subList(0, 3)), "\t");
                    ivInfo = callIvInfo + "\t" + putIvInfo;
                    System.out.println(stock + "\t" + open + "\t" + callDaily + "\t" + putDaily + "\t" + callDiff + "\t" + putDiff + "\t" + allDiff + "\t" + diffRatio + "\t" + ivInfo + "\t" + simulateTrade);
                }
            }
        }
    }
}
