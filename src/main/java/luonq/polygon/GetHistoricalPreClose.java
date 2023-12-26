package luonq.polygon;

import bean.StockKLine;
import bean.Trade;
import bean.TradeResp;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import util.BaseUtils;
import util.Constants;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Created by Luonanqin on 2023/5/14.
 */
public class GetHistoricalPreClose {

//    public static HttpClient httpclient = new HttpClient(new MultiThreadedHttpConnectionManager());

    public static void main(String[] args) throws Exception {
        String apiKey = "apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY";
        String api = "https://api.polygon.io/v3/trades/";
        String timeLte = "timestamp.lte=";
        String timeGte = "timestamp.gte=";
        String limit = "10";

        Map<String, String> hasGetMap = BaseUtils.getFileMap(Constants.TRADE_PATH + "preClose");
        // 获取2022-2023年所有交易日
        List<StockKLine> stockKLines = BaseUtils.loadDataToKline(Constants.HIS_BASE_PATH + "merge/AAPL", 2023, 2021);
        List<String> dateList = stockKLines.stream().map(StockKLine::getDate).collect(Collectors.toList());
        dateList.remove(0);

        // 获取所有股票列表
        Map<String, String> fileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "merge");
        Set<String> stockSet = fileMap.keySet();

        // 每只股票循环查询2022-2023年每个交易日的盘前最后交易价，并写入文件。查询时10个线程并行

        int threadCount = 20;
        int corePoolSize = threadCount;
        int maximumPoolSize = corePoolSize;
        long keepAliveTime = 60L;
        LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
        Executor cachedThread = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.MILLISECONDS, workQueue);
        BlockingQueue<String> queue = new LinkedBlockingQueue<>(threadCount);
        BlockingQueue<HttpClient> clients = new LinkedBlockingQueue<>(threadCount);
        for (int i = 0; i < threadCount; i++) {
            queue.offer("");
            clients.offer(new HttpClient());
        }

        // 2022
        LocalDateTime summerTime = BaseUtils.getSummerTime(null);
        LocalDateTime winterTime = BaseUtils.getWinterTime(null);

        for (String stock : stockSet) {
            if (!stock.equals("MSI")) {
                                continue;
            }

            String hasGetFile = hasGetMap.get(stock);
            if (StringUtils.isNotBlank(hasGetFile)) {
                List<String> lines = BaseUtils.readFile(hasGetFile);
                if (lines.get(lines.size() - 1).equals("finish")) {
//                    System.out.println("has get " + stock);
//                    continue;
                }
            }

            long begin = System.currentTimeMillis();
            List<String> result = Lists.newLinkedList();
            List<String> sync = Collections.synchronizedList(result);
            CountDownLatch cdl = new CountDownLatch(dateList.size());
            for (String date : dateList) {
                if (!date.equals("01/03/2023")) {
                    continue;
                }
                LocalDateTime day = LocalDate.parse(date, Constants.FORMATTER).atTime(0, 0);
                int hour, minute = 30, seconds = 0;

                if (day.isAfter(summerTime) && day.isBefore(winterTime)) {
                    hour = 21;
                } else {
                    hour = 22;
                }

                LocalDateTime open = day.withHour(hour).withMinute(minute).withSecond(seconds);
                LocalDateTime preGte = day.withHour(hour - 5).withMinute(minute - 29).withSecond(seconds);
                long openTS = open.toInstant(ZoneOffset.of("+8")).toEpochMilli();
                long preGteTS = preGte.toInstant(ZoneOffset.of("+8")).toEpochMilli();

                String preUrl = api + stock + "?order=desc&" + timeGte + preGteTS + "000000&" + timeLte + openTS + "000000&limit=" + limit + "&" + apiKey;
//                queue.take();
                HttpClient client = clients.take();
                cachedThread.execute(() -> {
                    try {
                        String preTrade = getTrade(preUrl, client);
                        if (NumberUtils.isCreatable(preTrade)) {
                            String str = date + "\t" + preTrade;
                            sync.add(str);
                            //                                                        System.out.println(str);
                        }
                    } finally {
                        cdl.countDown();
//                        queue.offer("");
                        clients.offer(client);
                    }
                });
//                System.out.println(date);
            }

            cdl.await();
            Collections.sort(sync, (o1, o2) -> {
                String date1 = o1.split("\t")[0];
                String date2 = o2.split("\t")[0];
                return BaseUtils.dateToInt(date2) - BaseUtils.dateToInt(date1);
            });

            sync.add("finish");
            try {
                BaseUtils.writeFile(Constants.TRADE_PATH + "preClose/" + stock, sync);
            } catch (Exception e) {
                e.printStackTrace();
            }
            long cost = System.currentTimeMillis() - begin;
            System.out.println("finish " + stock + " " + cost / 1000);
        }
        // 然后再执行下一只股票

        //        Map<String, List<String>> dateToStocksMap = Maps.newTreeMap();
        //        List<String> lines = BaseUtils.readFile(Constants.TEST_PATH + "overBollingOpen");
        //        for (String line : lines) {
        //            String[] split = line.split(",");
        //            String date = split[0].trim().substring(5);
        //            String stock = split[1].trim().substring(6);
        //            if (!dateToStocksMap.containsKey(date)) {
        //                dateToStocksMap.put(date, Lists.newArrayList());
        //            }
        //            dateToStocksMap.get(date).add(stock);
        //        }
        //
        //        for (String date : dateToStocksMap.keySet()) {
        //            List<String> stocks = dateToStocksMap.get(date);
        //
        //            LocalDateTime day = LocalDate.parse(date, Constants.FORMATTER).atTime(0, 0);
        //            int hour, minute = 30, seconds = 0;
        //            if (day.isAfter(summerTime) && day.isBefore(winterTime)) {
        //                hour = 21;
        //            } else {
        //                hour = 22;
        //            }
        //
        //            LocalDateTime open = day.withHour(hour).withMinute(minute).withSecond(seconds);
        //            LocalDateTime preGte = day.withHour(hour).withMinute(minute - 29).withSecond(seconds);
        //            LocalDateTime openLte = day.withHour(hour).withMinute(minute + 1).withSecond(seconds);
        //
        //            long openTS = open.toInstant(ZoneOffset.of("+8")).toEpochMilli();
        //            long preGteTS = preGte.toInstant(ZoneOffset.of("+8")).toEpochMilli();
        //            long openLteTS = openLte.toInstant(ZoneOffset.of("+8")).toEpochMilli();
        //
        //            for (String stock : stocks) {
        //                String preUrl = api + stock + "?order=desc&" + timeGte + preGteTS + "000000&" + timeLte + openTS + "000000&limit=" + limit + "&" + apiKey;
        //                String openUrl = api + stock + "?order=asc&" + timeGte + openTS + "000000&" + timeLte + openLteTS + "000000&limit=" + limit + "&" + apiKey;
        //
        //                String preTrade = getTrade(preUrl);
        //                String openTrade = getTrade(openUrl);
        //
        //                System.out.println("date=" + date + " stock=" + stock + " preTrade=" + preTrade + " openTrade=" + openTrade);
        //            }
        //        }
    }

    private static String getTrade(String preUrl, HttpClient httpclient) {
//        HttpClient httpclient = new HttpClient();
        GetMethod get = new GetMethod(preUrl);
        try {
            int code = 0;
            for (int i = 0; i < 3; i++) {
//                long l = System.currentTimeMillis();
                code = httpclient.executeMethod(get);
//                System.out.println("cost=" + (System.currentTimeMillis() - l));
                if (code == 200) {
                    break;
                }
            }
            if (code != 200) {
                return "request error";
            }

            InputStream stream = get.getResponseBodyAsStream();
            TradeResp tickerResp = JSON.parseObject(stream, TradeResp.class);
            List<Trade> results = tickerResp.getResults();
            if (CollectionUtils.isNotEmpty(results)) {
                Trade trade = results.get(0);
                return String.valueOf(trade.getPrice());
            } else {
                return "no data";
            }
        } catch (Exception e) {
            return e.getMessage();
        } finally {
            get.releaseConnection();
        }
    }
}
