package luonq.polygon;

import bean.AggregateTrade;
import bean.AggregateTradeResp;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;
import util.BaseUtils;
import util.Constants;

import java.io.InputStream;
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
 * 获取周线
 * https://api.polygon.io/v2/aggs/ticker/AAPL/range/1/week/2000-04-22/2024-01-02?adjusted=true&sort=asc&limit=120&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY
 * Created by Luonanqin on 2023/5/14.
 */
@Slf4j
public class GetHistoricalWeekAggregateTrade {

    public static final String ERROR_MSG = "can't get min aggreate";
    public static boolean retry = false;
    public static Set<String> hasGetStock = Sets.newHashSet();
    public static BlockingQueue<HttpClient> clients;
    public static ThreadPoolExecutor cachedThread;
    public static String apiKey = "&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY";
    public static String api = "https://api.polygon.io/v2/aggs/ticker/";
    public static String BEGIN_DATE = "2000-01-01";
    public static String minAggregatePath = "/Users/Luonanqin/study/intellij_idea_workspaces/temp/week/";

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
        if (hasGetStock.contains(stock)) {
            return;
        }

        try {
            List<String> lines = Lists.newArrayListWithExpectedSize(0);

            HttpClient httpClient = clients.take();
            cachedThread.execute(() -> {
                long begin = System.currentTimeMillis();
                try {
                    String url = api + stock + "/range/1/week/" + BEGIN_DATE + "/" + date + "?adjusted=true&sort=asc&limit=120" + apiKey;
                    while (true) {
                        AggregateTradeResp tradeResp = getTradeResp(url, httpClient);
                        if (tradeResp == null) {
                            lines.add(ERROR_MSG);
                            break;
                        } else if (CollectionUtils.isNotEmpty(tradeResp.getResults())) {
                            List<AggregateTrade> results = tradeResp.getResults();
                            results.stream().forEach(r -> {
                                long t = r.getT() / 1000;
                                String datetime = LocalDateTime.ofEpochSecond(t, 0, ZoneOffset.of("+8")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                                lines.add(datetime + "\t" + r.toString());
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
                    BaseUtils.writeFile(minAggregatePath + "/" + stock, lines);
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
        Map<String, String> stockFileMap = BaseUtils.getFileMap(minAggregatePath);
        hasGetStock = stockFileMap.keySet();

        Map<String, String> fileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "merge");
        for (String stock : fileMap.keySet()) {
            long begin = System.currentTimeMillis();
            System.out.println("begin " + stock);
            if (!stock.equals("AAPL")) {
                //                continue;
            }

            getData(stock, "2024-04-22");
            long end = System.currentTimeMillis();
            long cost = (end - begin) / 1000;
            System.out.println("finish " + stock + " " + cost);
        }
        cachedThread.shutdown();

        log.info("============ end ============");
    }
}
