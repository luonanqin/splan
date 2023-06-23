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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by Luonanqin on 2023/4/28.
 */
public class GetHistoricalTrade {

    public static void getData() throws Exception {
        String apiKey = "apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY";
        String api = "https://api.polygon.io/v3/trades/";
        String timeLte = "timestamp.lte=";
        String timeGte = "timestamp.gte=";

        int limit = 100;
        // 2023
        LocalDateTime dayLight1 = LocalDateTime.of(2023, 3, 12, 0, 0, 0);
        LocalDateTime dayLight2 = LocalDateTime.of(2023, 11, 12, 0, 0, 0);
        int year = 2023;

        // 2022
        //        LocalDateTime dayLight1 = LocalDateTime.of(2022, 3, 13, 0, 0, 0);
        //        LocalDateTime dayLight2 = LocalDateTime.of(2022, 11, 6, 0, 0, 0);
        //        int year = 2022;

        //        Map<String, String> hasMergeMap = BaseUtils.getFileMap(Constants.TRADE_OPEN_PATH + year);
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
            if (!stock.equals("FUTU")) {
                //                continue;
            }
            String stockFile = stockMap.get(stock);
            String openFile = openMap.get(stock);
            if (StringUtils.isBlank(openFile)) {
                continue;
            }
            List<StockKLine> stockKLines = BaseUtils.loadDataToKline(stockFile, 2023, 2022);
            List<String> openLines = BaseUtils.readFile(openFile);
            if (CollectionUtils.isEmpty(stockKLines) || CollectionUtils.isEmpty(openLines)) {
                continue;
            }
            String openLine = openLines.get(0);
            String openDate = openLine.split(",")[0];

            LocalDate dateParse = LocalDate.parse(openDate, DateTimeFormatter.ofPattern("MM/dd/yyyy"));

            List<String> dateList = Lists.newArrayList();
            for (int i = 0; i < stockKLines.size(); i++) {
                String latestDate = stockKLines.get(i).getDate();
                LocalDate latestDateParse = LocalDate.parse(latestDate, DateTimeFormatter.ofPattern("MM/dd/yyyy"));
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
            CountDownLatch cdl = new CountDownLatch(dateList.size());
            long begin = System.currentTimeMillis();
            for (String date : dateList) {
                LocalDateTime day = LocalDate.parse(date, Constants.FORMATTER).atTime(0, 0);

                int hour, minute = 30, seconds = 0;
                if (day.isAfter(dayLight1) && day.isBefore(dayLight2)) {
                    hour = 21;
                } else {
                    hour = 22;
                }
                LocalDateTime gte = day.withHour(hour).withMinute(minute).withSecond(seconds);
                LocalDateTime lte = day.withHour(hour).withMinute(minute + 1).withSecond(seconds);

                long gteTimestamp = gte.toInstant(ZoneOffset.of("+8")).toEpochMilli();
                long lteTimestamp = lte.toInstant(ZoneOffset.of("+8")).toEpochMilli();

                HttpClient httpClient = httpClients.take();
                cachedThread.execute(() -> {
                    String url = api + stock + "?" + timeGte + gteTimestamp + "000000&" + timeLte + lteTimestamp + "000000&limit=" + limit + "&" + apiKey;
                    GetMethod get = new GetMethod(url);

                    int totalVolumn = 0;
                    double totalAmount = 0;
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
                                result.add("request failed. date=" + date);
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
                        double avgPrice = totalAmount / totalVolumn;
                        result.add(String.format("%s,%d,%.2f", date, totalVolumn, avgPrice));
                        //                        System.out.println("finish stock: " + stock + " date: " + date);
                    } catch (Exception e) {
                        result.add("request error. date=" + date);
                    } finally {
                        get.releaseConnection();
                        httpClients.offer(httpClient);
                        cdl.countDown();
                    }
                });
            }

            cdl.await();
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
        }
    }

    public static void main(String[] args) throws Exception {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.commons").setLevel(Level.ERROR);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.ERROR);

        getData();
        System.out.println("============ end ============");
    }
}
