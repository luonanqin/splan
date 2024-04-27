package luonq.polygon;

import bean.AggregateTrade;
import bean.AggregateTradeResp;
import bean.StockKLine;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;
import util.BaseUtils;
import util.Constants;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by Luonanqin on 2023/5/14.
 */
@Slf4j
public class GetHistoricalOpen5MinTrade {

    public static boolean retry = false;

    public static void getData() throws Exception {
        String apiKey = "apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY";
        String api = "https://api.polygon.io/v2/aggs/ticker/";

        // 计算读取最新k线的目录及读取年份
        LocalDate today = LocalDate.now();
        int curYear = 2023, lastYear = curYear - 1;
        LocalDate firstWorkDay = BaseUtils.getFirstWorkDay();
        String kLinePath, open5MinTradePath;
        kLinePath = Constants.HIS_BASE_PATH + "merge";
        open5MinTradePath = Constants.HIS_BASE_PATH + curYear + "/open5MinTrade/";

        int threadCount = 100;
        int corePoolSize = threadCount;
        int maximumPoolSize = corePoolSize;
        long keepAliveTime = 60L;
        LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
        ThreadPoolExecutor cachedThread = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS, workQueue);
        BlockingQueue<HttpClient> clients = new LinkedBlockingQueue<>(threadCount);
        for (int i = 0; i < threadCount; i++) {
            clients.offer(new HttpClient());
        }

        LocalDateTime summerTime = BaseUtils.getSummerTime(curYear);
        LocalDateTime winterTime = BaseUtils.getWinterTime(curYear);

        Map<String, String> open5MinMap = BaseUtils.getFileMap(open5MinTradePath);
        Map<String, String> stockMap = BaseUtils.getFileMap(kLinePath);
        for (String stock : stockMap.keySet()) {
            try {
                if (!stock.equals("AAPL")) {
                    //                    continue;
                }

                String stockFile = stockMap.get(stock);
                String openFirstFile = open5MinMap.get(stock);

                List<StockKLine> kLines = BaseUtils.loadDataToKline(stockFile, curYear, lastYear);
                if (CollectionUtils.isEmpty(kLines)) {
                    continue;
                }

                List<String> lines;
                if (StringUtils.isBlank(openFirstFile)) {
                    lines = Lists.newArrayListWithExpectedSize(0);
                } else {
                    lines = BaseUtils.readFile(openFirstFile);
                }

                List<String> dateList = Lists.newArrayList();
                if (CollectionUtils.isNotEmpty(lines)) {
                    String openLine = lines.get(0);
                    String[] split = openLine.split(",");
                    if (split.length < 3) {
                        continue;
                    }
                    String openDate = split[0];

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
                            lines.remove(0);
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

                HttpClient httpClient = clients.take();
                cachedThread.execute(() -> {
                    long begin = System.currentTimeMillis();
                    List<String> result = Lists.newLinkedList();
                    List<String> sync = Collections.synchronizedList(result);
                    try {
                        for (String date : dateList) {
                            LocalDateTime day = LocalDate.parse(date, Constants.FORMATTER).atTime(0, 0);
                            int hour, minute = 35, seconds = 0;

                            if (day.isAfter(summerTime) && day.isBefore(winterTime)) {
                                hour = 21;
                            } else {
                                hour = 22;
                            }

                            LocalDateTime open = day.withHour(hour).withMinute(minute).withSecond(seconds);
                            long openTS = open.toInstant(ZoneOffset.of("+8")).toEpochMilli();

                            // https://api.polygon.io/v2/aggs/ticker/TSLA/range/1/minute/1709908200000/1709908200000?adjusted=true&sort=asc&limit=120&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY
                            String url = api + stock + "/range/1/minute/" + openTS + "/" + openTS + "?adjusted=true&sort=asc&limit=120&" + apiKey;
                            String tradeData = getTrade(url, httpClient);
                            String str = date + "," + tradeData;
                            sync.add(str);
                        }
                    } finally {
                        clients.offer(httpClient);
                    }

                    Collections.sort(sync, (o1, o2) -> {
                        String date1 = o1.split(",")[0].substring(0, 10);
                        String date2 = o2.split(",")[0].substring(0, 10);
                        return BaseUtils.dateToInt(date2) - BaseUtils.dateToInt(date1);
                    });

                    try {
                        lines.addAll(0, sync);
                        BaseUtils.writeFile(open5MinTradePath + stock, lines);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    long cost = System.currentTimeMillis() - begin;
                    log.info("finish " + stock + " " + cost / 1000);
                });
            } catch (Exception e) {
                log.error("error stock: " + stock);
                e.printStackTrace();
            }
        }
        cachedThread.shutdown();
    }

    private static String getTrade(String url, HttpClient httpclient) {
        GetMethod get = new GetMethod(url);
        try {
            int code = 0;
            for (int i = 0; i < 3; i++) {
                code = httpclient.executeMethod(get);
                if (code == 200) {
                    break;
                }
            }
            if (code != 200) {
                return "request error";
            }

            InputStream stream = get.getResponseBodyAsStream();
            AggregateTradeResp tickerResp = JSON.parseObject(stream, AggregateTradeResp.class);
            List<AggregateTrade> results = tickerResp.getResults();
            if (CollectionUtils.isNotEmpty(results)) {
                AggregateTrade trade = results.get(0);
                return trade.toString();
            } else {
                return "no data";
            }
        } catch (Exception e) {
            return e.getMessage();
        } finally {
            get.releaseConnection();
        }
    }

    public static void main(String[] args) throws Exception {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.commons").setLevel(Level.ERROR);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.ERROR);

        getData();
        log.info("============ end ============");
    }
}
