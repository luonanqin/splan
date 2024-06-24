package luonq.ivolatility;

import bean.StockKLine;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;
import util.BaseUtils;
import util.Constants;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class GetDailyImpliedVolatility {
    public static BlockingQueue<HttpClient> queue;
    public static ThreadPoolExecutor cachedThread;

    public static void init() throws Exception {
        int threadCount = 1;
        int corePoolSize = threadCount;
        int maximumPoolSize = corePoolSize;
        long keepAliveTime = 60L;
        LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
        cachedThread = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS, workQueue);
        queue = new LinkedBlockingQueue<>(threadCount);
        for (int i = 0; i < threadCount; i++) {
            queue.offer(new HttpClient());
        }
    }

    // https://restapi.ivolatility.com/equities/eod/option-series-on-date?apiKey=S3j7pBefWG0J0glb&symbol=BILI&expFrom=2022-05-20&expTo=2022-05-20&strikeFrom=22&strikeTo=22&date=2022-05-20
    public static Map<String/* optioncode */, String/* optionId */> getOptionId(List<String> optionCodeList) throws Exception {
        Map<String, String> optionIdMap = getOptionIdMap();
        Map<String, String> optionCodeToIdMap = Maps.newHashMap();
        List<String> result = Lists.newArrayList();
        for (String optionCode : optionCodeList) {
            if (optionIdMap.containsKey(optionCode)) {
                continue;
            }

            HttpClient httpClient = queue.take();
            int _2_index = optionCode.indexOf("2");
            String stock = optionCode.substring(2, _2_index);

            String date = optionCode.substring(_2_index, optionCode.length() - 9);
            String formatDate = LocalDate.parse("20" + date, DateTimeFormatter.ofPattern("yyyyMMdd")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String contractType = optionCode.substring(optionCode.length() - 9, optionCode.length() - 8);

            String priceStr = optionCode.substring(optionCode.length() - 8);
            int priceInt = Integer.valueOf(priceStr);
            String strikePrice = BigDecimal.valueOf(priceInt).divide(BigDecimal.valueOf(1000)).setScale(1, RoundingMode.DOWN).toString();
            if (strikePrice.contains(".0")) {
                strikePrice = strikePrice.substring(0, strikePrice.length() - 2);
            }
            //            System.out.println(strikePrice);
            //            System.out.println(formatDate);

            String url = String.format("https://restapi.ivolatility.com/equities/eod/option-series-on-date?apiKey=S3j7pBefWG0J0glb&symbol=%s&expFrom=%s&expTo=%s&strikeFrom=%s&strikeTo=%s&callPut=%s&date=%s",
              stock, formatDate, formatDate, strikePrice, strikePrice, contractType, formatDate);
            GetMethod get = new GetMethod(url);

            //            cachedThread.execute(() -> {
            try {
                int status = httpClient.executeMethod(get);
                String content = get.getResponseBodyAsString();
                //                System.out.println(status + " " + content);
                List<Map> listMap = JSON.parseArray(content, Map.class);
                if (CollectionUtils.isEmpty(listMap)) {
                    System.out.println(optionCode + " is empth");
                    result.add(optionCode + "\t0");
                    continue;
                }
                if (status == 429) {
                    System.out.println(429);
                    continue;
                    //                    System.exit(0);
                }

                Map map = listMap.get(0);
                String optionId = MapUtils.getString(map, "optionId");
                result.add(optionCode + "\t" + optionId);
                System.out.println(optionCode + "\t" + optionId);

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                get.releaseConnection();
                queue.offer(httpClient);
            }
            Thread.sleep(1000);
            //            });
        }

        List<String> lines = BaseUtils.readFile(Constants.USER_PATH + "optionData/optionId");
        lines.addAll(result);
        BaseUtils.writeFile(Constants.USER_PATH + "optionData/optionId", lines);

        return optionCodeToIdMap;
    }

    // https://restapi.ivolatility.com/equities/eod/single-stock-option-raw-iv?apiKey=S3j7pBefWG0J0glb&optionId=120387742&from=2022-05-16&to=2022-05-20
    public static void getHistoricalIV(Map<String/* optionCode */, String/* date */> optionCodeDateMap) throws Exception {
        List<StockKLine> stockKLines = BaseUtils.loadDataToKline(Constants.HIS_BASE_PATH + "merge/AAPL", 2024, 2020);
        List<String> dateList = stockKLines.stream().map(StockKLine::getFormatDate).collect(Collectors.toList());
        Map<String/* today */, String/* nextDay */> nextDayMap = Maps.newHashMap();
        Map<String/* today */, List<String>/* last5Days*/> last5DaysMap = Maps.newHashMap();
        for (int i = 0; i < dateList.size() - 5; i++) {
            last5DaysMap.put(dateList.get(i), Lists.newArrayList(dateList.get(i + 1), dateList.get(i + 2), dateList.get(i + 3), dateList.get(i + 4), dateList.get(i + 5)));
        }
        for (int i = 0; i < dateList.size() - 1; i++) {
            nextDayMap.put(dateList.get(i + 1), dateList.get(i));
        }
        Map<String, String> optionIdMap = getOptionIdMap();

        HttpClient httpClient = new HttpClient();
        for (String optionCode : optionCodeDateMap.keySet()) {
            String optionId = optionIdMap.get(optionCode);
            if (StringUtils.isBlank(optionId)) {
                getOptionId(Lists.newArrayList(optionCode));
                optionIdMap = getOptionIdMap();

                optionId = optionIdMap.get(optionCode);
                if (StringUtils.isBlank(optionId) || StringUtils.equalsAny(optionId, "0")) {
                    continue;
                }
            } else if (StringUtils.equalsAny(optionId, "0")) {
                continue;
            }

            // 加载已抓数据，如果没有就新建
            String code = optionCode.substring(2);
            Map<String, Double> ivMap = Maps.newTreeMap(Comparator.comparing(BaseUtils::formatDateToInt));
            int _2_index = code.indexOf("2");
            String stock = code.substring(0, _2_index);
            File ivDir = new File(Constants.USER_PATH + "optionData/IV/" + stock);
            if (!ivDir.exists()) {
                ivDir.mkdirs();
            }
            List<String> lines = BaseUtils.readFile(Constants.USER_PATH + "optionData/IV/" + stock + "/" + code);
            for (String l : lines) {
                String[] s = l.split("\t");
                ivMap.put(s[0], Double.valueOf(s[1]));
            }

            // 抓取数据
            String expireDate = optionCode.substring(optionCode.length() - 15, optionCode.length() - 9);
            String expireDay = LocalDate.parse(expireDate, DateTimeFormatter.ofPattern("yyMMdd")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String date = optionCodeDateMap.get(optionCode);
            String last5Day = last5DaysMap.get(date).get(4);
            String url = String.format("https://restapi.ivolatility.com/equities/eod/single-stock-option-raw-iv?apiKey=S3j7pBefWG0J0glb&optionId=%s&from=%s&to=%s",
              optionId, last5Day, expireDay);

            GetMethod get = new GetMethod(url);
            String content = null;
            try {
                httpClient.executeMethod(get);
                content = get.getResponseBodyAsString();
                JSONObject map = JSON.parseObject(content, JSONObject.class);
                Object data = map.get("data");
                List<Map> maps = JSON.parseArray(data.toString(), Map.class);
                if (CollectionUtils.isEmpty(maps)) {
                    continue;
                }
                Map<String, Double> ivmap = Maps.newHashMap();
                for (Map m : maps) {
                    String curDate = MapUtils.getString(m, "date");
                    Double iv = MapUtils.getDouble(m, "iv");
                    ivmap.put(curDate, iv);
                }

                while (true) {
                    if (ivmap.containsKey(last5Day)) {
                        ivMap.put(last5Day, ivmap.get(last5Day));
                    } else {
                        ivMap.put(last5Day, -2d);
                    }
                    if (last5Day.equals(expireDay)) {
                        break;
                    }
                    last5Day = nextDayMap.get(last5Day);
                }
            } catch (Exception e) {
                System.out.println(url + "\t" + content);
                e.printStackTrace();
            } finally {
                get.releaseConnection();
            }
            Thread.sleep(1000);

            // 写入文件

            List<String> results = Lists.newArrayList();
            for (String k : ivMap.keySet()) {
                results.add(k + "\t" + ivMap.get(k));
            }
            BaseUtils.writeFile(Constants.USER_PATH + "optionData/IV/" + stock + "/" + code, results);
        }
    }

    public static void getHistoricalIV() throws Exception {
        List<StockKLine> stockKLines = BaseUtils.loadDataToKline(Constants.HIS_BASE_PATH + "merge/AAPL", 2024, 2020);
        List<String> dateList = stockKLines.stream().map(StockKLine::getFormatDate).collect(Collectors.toList());
        Map<String/* today */, String/* nextDay */> nextDayMap = Maps.newHashMap();
        for (int i = 0; i < dateList.size() - 1; i++) {
            nextDayMap.put(dateList.get(i + 1), dateList.get(i));
        }
        Map<String, String> optionIdMap = getOptionIdMap();
        Set<String> weekOptionStock = BaseUtils.getWeekOptionStock();

        HttpClient httpClient = new HttpClient();
        for (String stock : weekOptionStock) {
            Map<String, String> fileMap = BaseUtils.getFileMap(Constants.USER_PATH + "optionData/IV/" + stock);
            for (String file : fileMap.keySet()) {
                String filePath = fileMap.get(file);
                List<String> lines = BaseUtils.readFile(filePath);
                if (CollectionUtils.isEmpty(lines)) {
                    continue;
                }
                String expireDate = file.substring(stock.length(), file.length() - 9);
                String expireDay = LocalDate.parse(expireDate, DateTimeFormatter.ofPattern("yyMMdd")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                String latestData = lines.get(lines.size() - 1);
                String latestDay = latestData.split("\t")[0];
                String beginDay = nextDayMap.get(latestDay);
                String optionId = optionIdMap.get("O:" + file);
                if (StringUtils.isBlank(optionId)) {
                    System.out.println("optionId: " + file);
                    continue;
                }
                if (latestDay.equals(expireDay)) {
                    continue;
                }

                String url = String.format("https://restapi.ivolatility.com/equities/eod/single-stock-option-raw-iv?apiKey=S3j7pBefWG0J0glb&optionId=%s&from=%s&to=%s",
                  optionId, beginDay, expireDay);

                Map<String, Double> ivmap = Maps.newHashMap();
                GetMethod get = new GetMethod(url);
                String content = null;
                try {
                    httpClient.executeMethod(get);
                    content = get.getResponseBodyAsString();
                    JSONObject map = JSON.parseObject(content, JSONObject.class);
                    Object data = map.get("data");
                    List<Map> maps = JSON.parseArray(data.toString(), Map.class);
                    if (CollectionUtils.isEmpty(maps)) {
                        continue;
                    }
                    for (Map m : maps) {
                        String curDate = MapUtils.getString(m, "date");
                        Double iv = MapUtils.getDouble(m, "iv");
                        ivmap.put(curDate, iv);
                    }
                } catch (Exception e) {
                    System.out.println(url + "\t" + content);
                    e.printStackTrace();
                } finally {
                    get.releaseConnection();
                }
                Thread.sleep(1000);

                while (true) {
                    if (ivmap.containsKey(beginDay)) {
                        lines.add(beginDay + "\t" + ivmap.get(beginDay));
                    } else {
                        lines.add(beginDay + "\t" + -2);
                    }
                    if (beginDay.equals(expireDay)) {
                        break;
                    }
                    beginDay = nextDayMap.get(beginDay);
                }

                BaseUtils.writeFile(filePath, lines);
            }
            System.out.println("finish " + stock);
        }
    }

    public static Map<String, String> getOptionCodeDateMap() {
        Map<String, String> map = Maps.newHashMap();
        map.put("O:AAPL240503C00170000", "2024-05-03");
        return map;
    }

    public static Map<String/* optoinCode */, String/* optionId */> getOptionIdMap() throws Exception {
        List<String> lines = BaseUtils.readFile(Constants.USER_PATH + "optionData/optionId");
        Map<String, String> map = Maps.newHashMap();
        for (String line : lines) {
            String[] split = line.split("\t");
            map.put(split[0], split[1]);
        }

        return map;
    }

    public static List<String> buildTestData() {
        List<String> list = Lists.newArrayList();
        list.add("O:AAPL240503C00170000");
        return list;
    }

    public static void main(String[] args) throws Exception {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.http").setLevel(Level.INFO);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.INFO);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.commons").setLevel(Level.ERROR);

        init();

        //        List<String> list = buildTestData();
        //        getOptionId(list);
        //        getHistoricalIV(getOptionCodeDateMap());

    }
}
