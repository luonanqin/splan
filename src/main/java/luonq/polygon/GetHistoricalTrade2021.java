package luonq.polygon;

import bean.StockKLine;
import bean.Trade;
import bean.TradeResp;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
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
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by Luonanqin on 2023/4/28.
 */
public class GetHistoricalTrade2021 {

    public static boolean retry = false;
    public static int limit = 100;
    public static String apiKey = "apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY";

    public static void getData() throws Exception {
        String api = "https://api.polygon.io/v3/trades/";
        String timeLte = "timestamp.lte=";
        String timeGte = "timestamp.gte=";

        // 2023
        LocalDateTime dayLight_1 = LocalDateTime.of(2021, 3, 14, 0, 0, 0);
        LocalDateTime dayLight_2 = LocalDateTime.of(2023, 11, 7, 0, 0, 0);
        int year = 2021;

        int poolSize = 20;
        LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
        Executor cachedThread = new ThreadPoolExecutor(poolSize, poolSize, 60, TimeUnit.SECONDS, workQueue);
        LinkedBlockingQueue<HttpClient> httpClients = new LinkedBlockingQueue<>();
        for (int i = 0; i < poolSize; i++) {
            httpClients.offer(new HttpClient());
        }

        Map<String, String> openMap = BaseUtils.getFileMap(Constants.TRADE_OPEN_PATH + year);
        Map<String, String> stockMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "merge");
        for (String stock : stockMap.keySet()) {
            if (!stock.equals("AAPL")) {
                continue;
            }
            String stockFile = stockMap.get(stock);
            String openFile = openMap.get(stock);
            if (StringUtils.isBlank(openFile)) {
                continue;
            }
            List<StockKLine> stockKLines = BaseUtils.loadDataToKline(stockFile, 2021, 2020);
            List<String> openLines = BaseUtils.readFile(openFile);
            if (CollectionUtils.isEmpty(stockKLines) || CollectionUtils.isEmpty(openLines)) {
                continue;
            }
            String openLine = openLines.get(0);
            String openDate = openLine.split(",")[0];

            if (openDate.length() > 10) {
                System.out.println(stock + " " + openLine);
                continue;
            }

            LocalDate dateParse = LocalDate.parse(openDate, DateTimeFormatter.ofPattern("MM/dd/yyyy"));

            List<String> dateList = Lists.newArrayList();
            for (int i = 0; i < stockKLines.size(); i++) {
                String latestDate = stockKLines.get(i).getDate();
                LocalDate latestDateParse = LocalDate.parse(latestDate, DateTimeFormatter.ofPattern("MM/dd/yyyy"));
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

            if (CollectionUtils.isEmpty(dateList)) {
                System.out.println("has get " + stock);
                continue;
            }

            List<String> result = Collections.synchronizedList(Lists.newArrayListWithExpectedSize(20));
            //            CountDownLatch cdl = new CountDownLatch(dateList.size());
            HttpClient httpClient = httpClients.take();
            cachedThread.execute(() -> {
                long begin = System.currentTimeMillis();
                for (String date : dateList) {
                    LocalDateTime day = LocalDate.parse(date, Constants.FORMATTER).atTime(0, 0);

                    int hour, minute = 30, seconds = 0;
                    if (day.isAfter(dayLight_1) && day.isBefore(dayLight_2)) {
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
                        result.add(date + "," + trade);
                    } finally {
                        httpClients.offer(httpClient);
                        //                        cdl.countDown();
                    }

                }

                //            cdl.await();
                try {
                    Collections.sort(result, (o1, o2) -> {
                        o1 = o1.substring(0, o1.indexOf(","));
                        o2 = o2.substring(0, o2.indexOf(","));
                        return BaseUtils.dateToInt(o2) - BaseUtils.dateToInt(o1);
                    });
                    FileWriter fw = new FileWriter(Constants.TRADE_OPEN_PATH + year + "/" + stock);
                    openLines.addAll(0, result);
                    for (String str : openLines) {
                        fw.write(str + "\n");
                    }
                    fw.close();
                    System.out.println("stock: " + stock + ", cost: " + ((System.currentTimeMillis() - begin) / 1000) + "s");
                } catch (Exception e) {
                    System.out.println("error: " + result);
                }
            });
        }
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
                    System.err.println("request failed");
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
                //                        System.out.println("finish stock: " + stock + " date: " + date);
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
        System.out.println("============ end ============");
    }
}
