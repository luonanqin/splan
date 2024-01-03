package luonq.polygon;

import bean.StockKLine;
import bean.Trade;
import bean.TradeResp;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Created by Luonanqin on 2023/5/14.
 */
public class GetHistoricalOpenFirstTrade {

    public static boolean retry = false;

    public static void getData() throws Exception {
        String apiKey = "apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY";
        String api = "https://api.polygon.io/v3/trades/";
        String timeLte = "timestamp.lte=";
        String timeGte = "timestamp.gte=";
        String limit = "10";

        // 计算读取最新k线的目录及读取年份
        LocalDate today = LocalDate.now();
        int curYear = today.getYear(), lastYear = curYear - 1;
        LocalDate firstWorkDay = BaseUtils.getFirstWorkDay();
        String kLinePath, openFirstTradePath;
        if (curYear == 2023) {
            kLinePath = Constants.HIS_BASE_PATH + "2023daily";
            openFirstTradePath = Constants.TRADE_PATH + "openFirstTrade/";
        } else {
            if (today.isAfter(firstWorkDay)) {
                kLinePath = Constants.HIS_BASE_PATH + curYear + "/dailyKLine";
                openFirstTradePath = Constants.HIS_BASE_PATH + curYear + "/openFirstTrade/";
            } else {
                if (lastYear == 2023) {
                    kLinePath = Constants.HIS_BASE_PATH + "2023daily/";
                    openFirstTradePath = Constants.TRADE_PATH + "openFirstTrade/";
                } else {
                    kLinePath = Constants.HIS_BASE_PATH + lastYear + "/dailyKLine";
                    openFirstTradePath = Constants.HIS_BASE_PATH + lastYear + "/openFirstTrade/";
                }
            }
        }

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

        LocalDateTime summerTime = BaseUtils.getSummerTime(null);
        LocalDateTime winterTime = BaseUtils.getWinterTime(null);

        Map<String, String> stockOpenFirstMap = BaseUtils.getFileMap(openFirstTradePath);
        Map<String, String> stockMap = BaseUtils.getFileMap(kLinePath);
        Set<String> test = Sets.newHashSet("AAPL");
        for (String stock : stockMap.keySet()) {
            try {
                if (!test.contains(stock)) {
                    continue;
                }

                String file = stockOpenFirstMap.get(stock);
                if (StringUtils.isBlank(file)) {
                    continue;
                }
                List<String> lines = BaseUtils.readFile(file);
                String latestDate = "01/01/2000";
                if (CollectionUtils.isNotEmpty(lines)) {
                    String[] split = lines.get(0).split(",");
                    if (split.length < 3) {
                        continue;
                    }
                    latestDate = split[0];
                }

                String stockFile = stockMap.get(stock);
                List<StockKLine> stockKLines = BaseUtils.loadDataToKline(stockFile, lastYear);
                List<String> tradeDateList = stockKLines.stream().map(StockKLine::getDate).collect(Collectors.toList());

                List<String> dateList = Lists.newArrayList();
                LocalDate latestDay = LocalDate.parse(latestDate, Constants.FORMATTER);
                for (String tradeDate : tradeDateList) {
                    LocalDate tradeDay = LocalDate.parse(tradeDate, Constants.FORMATTER);
                    if (latestDay.isEqual(tradeDay) && retry) {
                        dateList.add(latestDate);
                        lines.remove(0);
                        continue;
                    }
                    if (latestDay.isBefore(tradeDay)) {
                        dateList.add(tradeDate);
                    } else {
                        break;
                    }
                }
                if (CollectionUtils.isEmpty(dateList)) {
                    //                    System.out.println("has get " + stock);
                    continue;
                }

                HttpClient httpClient = clients.take();
                cachedThread.execute(() -> {
                    long begin = System.currentTimeMillis();
                    List<String> result = Lists.newLinkedList();
                    List<String> sync = Collections.synchronizedList(result);
                    try {
                        for (String date : dateList) {
                            LocalDateTime day = LocalDate.parse(date, Constants.FORMATTER).atTime(0, 0);
                            int hour, minute = 30, seconds = 0;

                            if (day.isAfter(summerTime) && day.isBefore(winterTime)) {
                                hour = 21;
                            } else {
                                hour = 22;
                            }

                            LocalDateTime open = day.withHour(hour).withMinute(minute).withSecond(seconds);
                            LocalDateTime openFirstLte = day.withHour(hour).withMinute(minute + 29).withSecond(seconds);
                            long openTS = open.toInstant(ZoneOffset.of("+8")).toEpochMilli();
                            long openFirstLteTS = openFirstLte.toInstant(ZoneOffset.of("+8")).toEpochMilli();

                            String preUrl = api + stock + "?order=asc&" + timeGte + openTS + "000000&" + timeLte + openFirstLteTS + "000000&limit=" + limit + "&sort=timestamp&" + apiKey;
                            String preTrade = getTrade(preUrl, httpClient);
                            if (!preTrade.contains(",")) {
                                System.out.println(stock + " " + preTrade);
                                continue;
                            }
                            String str = date + "," + preTrade;
                            sync.add(str);
                        }
                    } finally {
                        clients.offer(httpClient);
                    }

                    Collections.sort(sync, (o1, o2) -> {
                        String date1 = o1.split(",")[0];
                        String date2 = o2.split(",")[0];
                        return BaseUtils.dateToInt(date2) - BaseUtils.dateToInt(date1);
                    });

                    try {
                        lines.addAll(0, sync);
                        BaseUtils.writeFile(openFirstTradePath + stock, lines);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    long cost = System.currentTimeMillis() - begin;
                    System.out.println("finish " + stock + " " + cost / 1000);
                });
            } catch (Exception e) {
                System.out.println("error stock: " + stock);
                e.printStackTrace();
            }
        }
        cachedThread.shutdown();
    }

    private static String getTrade(String preUrl, HttpClient httpclient) {
        GetMethod get = new GetMethod(preUrl);
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
            TradeResp tickerResp = JSON.parseObject(stream, TradeResp.class);
            List<Trade> results = tickerResp.getResults();
            if (CollectionUtils.isNotEmpty(results)) {
                Trade trade = results.get(0);
                double price = trade.getPrice();
                long participantTimestamp = trade.getParticipant_timestamp();
                long nano = participantTimestamp % 1000000000L;
                long second = participantTimestamp / 1000000000L;
                LocalDateTime time = LocalDateTime.ofEpochSecond(second, (int) nano, ZoneOffset.of("+8"));
                String format = time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                return price + "," + format;
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
        System.out.println("============ end ============");
    }
}
