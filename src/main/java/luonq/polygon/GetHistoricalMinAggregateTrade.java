package luonq.polygon;

import bean.AggregateTrade;
import bean.AggregateTradeResp;
import bean.StockKLine;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by Luonanqin on 2023/5/14.
 */
@Slf4j
public class GetHistoricalMinAggregateTrade {

    public static final String ERROR_MSG = "can't get min aggreate";
    public static boolean retry = false;
    public static Map<String, Set<String>> hasGetDateMap = Maps.newHashMap();
    public static BlockingQueue<HttpClient> clients;
    public static ThreadPoolExecutor cachedThread;
    public static String apiKey = "&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY";
    public static String api = "https://api.polygon.io/v2/aggs/ticker/";


    public static void init() {
        int threadCount = 100;
        int corePoolSize = threadCount;
        int maximumPoolSize = corePoolSize;
        long keepAliveTime = 60L;
        LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
        cachedThread = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS, workQueue);
        clients = new LinkedBlockingQueue<>(threadCount);
        for (int i = 0; i < threadCount; i++) {
            clients.offer(new HttpClient());
        }
    }

    public static void getData(String stock, String date) throws Exception {
        // 计算读取最新k线的目录及读取年份
        int curYear = Integer.valueOf(date.substring(0, 4));
        String minAggregatePath = "/Users/Luonanqin/study/intellij_idea_workspaces/temp/" + curYear + "/" + stock + "/";
        BaseUtils.createDirectory(minAggregatePath);

        Set<String> hasGetDate = hasGetDateMap.get(stock);
        if (CollectionUtils.isEmpty(hasGetDate)) {
            Map<String, String> fileMap = BaseUtils.getFileMap(minAggregatePath);
            hasGetDate = fileMap.keySet();
            hasGetDateMap.put(stock, hasGetDate);
        }

        if (hasGetDate.contains(date)) {
            return;
        }

        LocalDateTime summerTime = BaseUtils.getSummerTime(curYear);
        LocalDateTime winterTime = BaseUtils.getWinterTime(curYear);

        try {
            List<String> lines = Lists.newArrayListWithExpectedSize(0);

            HttpClient httpClient = clients.take();
            cachedThread.execute(() -> {
                long begin = System.currentTimeMillis();
                try {
                    LocalDateTime day = LocalDate.parse(date, Constants.DB_DATE_FORMATTER).atTime(0, 0);
                    int hour, minute = 30, seconds = 0;

                    if (day.isAfter(summerTime) && day.isBefore(winterTime)) {
                        hour = 21;
                    } else {
                        hour = 22;
                    }
                    LocalDateTime open = day.withHour(hour).withMinute(minute).withSecond(seconds);
                    LocalDateTime close = open.plusHours(6).plusMinutes(29);

                    long openTS = open.toInstant(ZoneOffset.of("+8")).toEpochMilli();
                    long closeTS = close.toInstant(ZoneOffset.of("+8")).toEpochMilli();

                    // https://api.polygon.io/v2/aggs/ticker/TSLA/range/1/minute/1709908200000/1709908200000?adjusted=true&sort=asc&limit=120&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY
                    String url = api + stock + "/range/1/minute/" + openTS + "/" + closeTS + "?adjusted=true&sort=asc&limit=60" + apiKey;
                    while (true) {
                        AggregateTradeResp tradeResp = getTradeResp(url, httpClient);
                        if (tradeResp == null) {
                            lines.add(ERROR_MSG);
                            break;
                        } else if (CollectionUtils.isNotEmpty(tradeResp.getResults())) {
                            List<AggregateTrade> results = tradeResp.getResults();
                            results.stream().forEach(r -> {
                                long t = r.getT() / 1000;
                                String datetime = LocalDateTime.ofEpochSecond(t, 0, ZoneOffset.of("+8")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                                lines.add(datetime + "\t" + r.getC());
                            });
                            if (StringUtils.isEmpty(tradeResp.getNext_url())) {
                                break;
                            }

                            url = tradeResp.getNext_url() + apiKey;
                        } else if (CollectionUtils.isEmpty(tradeResp.getResults())) {
                            break;
                        }
                    }
                } finally {
                    clients.offer(httpClient);
                }

                try {
                    if (lines.contains(ERROR_MSG)) {
                        return;
                    }
                    BaseUtils.writeFile(minAggregatePath + "/" + date, lines);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                long cost = System.currentTimeMillis() - begin;
                //                log.info("finish " + stock + " " + cost / 1000);
            });
        } catch (Exception e) {
            log.error("error stock: " + stock);
            e.printStackTrace();
        }
    }

    private static AggregateTradeResp getTradeResp(String url, HttpClient httpclient) {
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
                return null;
            }

            InputStream stream = get.getResponseBodyAsStream();
            AggregateTradeResp tickerResp = JSON.parseObject(stream, AggregateTradeResp.class);
            return tickerResp;
        } catch (Exception e) {
            log.error("GetHistoricalMinAggregateTrade.getTrade error. url={}", url, e);
        } finally {
            get.releaseConnection();
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.commons").setLevel(Level.ERROR);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.ERROR);

        init();
//        Map<String, String> fileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "2024/dailyKLine/");
        Map<String, String> fileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "merge");
        for (String stock : fileMap.keySet()) {
            long begin = System.currentTimeMillis();
            System.out.println("begin " + stock);
            if (!stock.equals("AAPL")) {
//                                continue;
            }

            List<StockKLine> stockKLines = BaseUtils.loadDataToKline(fileMap.get(stock), 2022, 2021);
            for (StockKLine stockKLine : stockKLines) {
                double open = stockKLine.getOpen();
                if (open < 6) {
                    continue;
                }

                String date = stockKLine.getFormatDate();
                getData(stock, date);
            }
            long end = System.currentTimeMillis();
            long cost = (end - begin) / 1000;
            System.out.println("finish " + stock + " " + cost);
        }
        cachedThread.shutdown();

        log.info("============ end ============");
    }
}
