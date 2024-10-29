package luonq.polygon;

import bean.StockKLine;
import bean.Trade;
import bean.TradeResp;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;
import util.BaseUtils;
import util.Constants;

import java.io.FileWriter;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by Luonanqin on 2023/4/28.
 */
@Slf4j
public class GetHistoricalTrade2 {

    public static boolean retry = false;
    public static int limit = 100;
    public static String apiKey = "apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY";

    public static void getData() throws Exception {
        String api = "https://api.polygon.io/v3/trades/";
        String timeLte = "timestamp.lte=";
        String timeGte = "timestamp.gte=";

        // 2023
        LocalDateTime summerTime = BaseUtils.getSummerTime(null);
        LocalDateTime winterTime = BaseUtils.getWinterTime(null);

        //        Map<String, String> hasMergeMap = BaseUtils.getFileMap(Constants.TRADE_OPEN_PATH + year);
        int poolSize = 20;
        LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
        ThreadPoolExecutor cachedThread = new ThreadPoolExecutor(poolSize, poolSize, 60, TimeUnit.SECONDS, workQueue);
        LinkedBlockingQueue<HttpClient> httpClients = new LinkedBlockingQueue<>();
        for (int i = 0; i < poolSize; i++) {
            httpClients.offer(new HttpClient());
        }

        // 计算读取最新k线的目录及读取年份
        LocalDate today = LocalDate.now();
        int year = today.getYear(), lastYear = year - 1;
        LocalDate firstWorkDay = BaseUtils.getFirstWorkDay();
        String kLinePath, openTradePath;
        if (year == 2023) {
            kLinePath = Constants.HIS_BASE_PATH + "2023daily";
            openTradePath = Constants.TRADE_OPEN_PATH + year + "/";
        } else {
            if (today.isAfter(firstWorkDay)) {
                kLinePath = Constants.HIS_BASE_PATH + year + "/dailyKLine";
                openTradePath = Constants.TRADE_OPEN_PATH + year + "/";
            } else {
                if (lastYear == 2023) {
                    kLinePath = Constants.HIS_BASE_PATH + "2023daily/";
                } else {
                    kLinePath = Constants.HIS_BASE_PATH + lastYear + "/dailyKLine";
                }
                openTradePath = Constants.TRADE_OPEN_PATH + lastYear + "/";
            }
        }

        Map<String, String> openMap = BaseUtils.getFileMap(openTradePath);
        Map<String, String> stockMap = BaseUtils.getFileMap(kLinePath);
        for (String stock : stockMap.keySet()) {
            if (!stock.equals("DJT")) {
                //                continue;
            }
            String stockFile = stockMap.get(stock);
            String openFile = openMap.get(stock);

            List<StockKLine> kLines = BaseUtils.loadDataToKline(stockFile, year);
            if (CollectionUtils.isEmpty(kLines)) {
                continue;
            }

            List<String> openLines;
            if (StringUtils.isBlank(openFile)) {
                openLines = Lists.newArrayListWithExpectedSize(0);
            } else {
                openLines = BaseUtils.readFile(openFile);
            }

            List<String> dateList = Lists.newArrayList();
            if (CollectionUtils.isNotEmpty(openLines)) {
                String openLine = openLines.get(0);
                String openDate = openLine.split(",")[0];

                if (openDate.length() > 10) {
                    log.info(stock + " " + openLine);
                    continue;
                }

                LocalDate dateParse = LocalDate.parse(openDate, Constants.FORMATTER);

                for (int i = 0; i < kLines.size(); i++) {
                    String latestDate = kLines.get(i).getDate();
                    LocalDate latestDateParse = LocalDate.parse(latestDate, Constants.FORMATTER);
                    if (dateParse.isEqual(latestDateParse) && retry) {
                        dateList.add(latestDate);
                        openLines.remove(0);
                        continue;
                    }
                    if (dateParse.isBefore(latestDateParse)) {
                        dateList.add(latestDate);
                    } else {
                        break;
                    }
                }
            } else {
                kLines.forEach(k -> dateList.add(k.getDate()));
            }

            if (CollectionUtils.isEmpty(dateList)) {
                //                log.info("has get " + stock);
                continue;
            }

            List<String> result = Collections.synchronizedList(Lists.newArrayListWithExpectedSize(20));
            HttpClient httpClient = httpClients.take();
            cachedThread.execute(() -> {
                long begin = System.currentTimeMillis();
                for (String date : dateList) {
                    LocalDateTime day = LocalDate.parse(date, Constants.FORMATTER).atTime(0, 0);

                    int hour, minute = 30, seconds = 0;
                    if (day.isAfter(summerTime) && day.isBefore(winterTime)) {
                        hour = 21;
                    } else {
                        hour = 22;
                    }
                    LocalDateTime gte = day.withHour(hour).withMinute(minute).withSecond(seconds);
                    LocalDateTime lte = day.withHour(hour).withMinute(minute + 1).withSecond(seconds);

                    long gteTimestamp = gte.toInstant(ZoneOffset.of("+8")).toEpochMilli();
                    long lteTimestamp = lte.toInstant(ZoneOffset.of("+8")).toEpochMilli();

                    String url = api + stock + "?" + timeGte + gteTimestamp + "000000&" + timeLte + lteTimestamp + "000000&limit=" + limit + "&" + apiKey;

                    try {
                        String trade = getTrade(url, httpClient);
                        if (!trade.contains(",")) {
                            log.info(stock + " " + trade);
                            continue;
                        }
                        result.add(date + "," + trade);
                    } finally {
                        httpClients.offer(httpClient);
                    }
                }

                try {
                    Collections.sort(result, (o1, o2) -> {
                        o1 = o1.substring(0, o1.indexOf(","));
                        o2 = o2.substring(0, o2.indexOf(","));
                        return BaseUtils.dateToInt(o2) - BaseUtils.dateToInt(o1);
                    });
                    FileWriter fw = new FileWriter(openTradePath + stock);
                    openLines.addAll(0, result);
                    for (String str : openLines) {
                        fw.write(str + "\n");
                    }
                    fw.close();
                    //                    log.info("stock: " + stock + ", cost: " + ((System.currentTimeMillis() - begin) / 1000) + "s");
                } catch (Exception e) {
                    log.error("error: " + result);
                }
            });
        }
        cachedThread.shutdown();
    }

    // date +request failed.
    // date +totalVolumn +avgPrice
    // date + request error
    public static String getTrade(String url, HttpClient httpClient) {
        GetMethod get = new GetMethod(url);

        String result = "";
        int totalVolumn = 0;
        double totalAmount = 0;
        boolean success = true;
        try {
            while (true) {
                int code = 0;
                for (int i = 0; i < 3; i++) {
                    code = httpClient.executeMethod(get);
                    if (code == 200) {
                        break;
                    }
                }
                if (code != 200) {
                    //                    System.err.println("request failed");
                    result = "request failed";
                    success = false;
                    break;
                }
                InputStream stream = get.getResponseBodyAsStream();
                TradeResp tickerResp = JSON.parseObject(stream, TradeResp.class);
                int count = tickerResp.getCount();
                String next_url = tickerResp.getNext_url();
                List<Trade> results = tickerResp.getResults();
                for (Trade trade : results) {
                    totalVolumn += trade.getSize();
                    totalAmount += trade.getPrice() * trade.getSize();
                }

                if (count < limit) {
                    break;
                }

                next_url += apiKey;
                get.setURI(new URI(next_url, false));
            }
            if (success) {
                double avgPrice = totalAmount / totalVolumn;
                result = String.format("%d,%.2f", totalVolumn, avgPrice);
                //                        log.info("finish stock: " + stock + " date: " + date);
            }
        } catch (Exception e) {
            result = "request error";
        } finally {
            get.releaseConnection();
        }
        return result;
    }

    public static void main(String[] args) throws Exception {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.commons").setLevel(Level.ERROR);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.ERROR);

        getData();
        log.info("============ end ============");
    }
}
