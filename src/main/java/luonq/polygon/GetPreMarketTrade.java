package luonq.polygon;

import bean.Trade;
import bean.TradeResp;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.alibaba.fastjson.JSON;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.slf4j.LoggerFactory;
import util.BaseUtils;
import util.Constants;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class GetPreMarketTrade {

    public static final String ERROR_MSG = "can't get pre market data";
    public static BlockingQueue<HttpClient> clients;
    public static ThreadPoolExecutor cachedThread;
    public static String urlPrefix = "https://api.polygon.io/v3/trades/";
    public static String urlSuffix = "?order=desc&timestamp.gte=1713187770000000000&timestamp.lte=1713187800000000000&limit=100&sort=timestamp&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY";

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

    public static void main(String[] args) throws Exception {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.commons").setLevel(Level.ERROR);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.ERROR);

        init();
        Map<String, String> dailyFileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "week/");

        Set<String> stockSets = dailyFileMap.keySet();

        for (String stock : stockSets) {
            getData(stock);
        }
    }

    public static void getData(String stock) {
        try {
            HttpClient httpClient = clients.take();
            cachedThread.execute(() -> {
                try {
                    String url = urlPrefix + stock + urlSuffix;
                    TradeResp tradeResp = getTradeResp(url, httpClient);
                    if (tradeResp == null) {
                        return;
                    } else if (CollectionUtils.isNotEmpty(tradeResp.getResults())) {
                        List<Trade> results = tradeResp.getResults();
                        Trade trade = results.get(0);
                        System.out.println(stock + "\t" + trade.getPrice());
                    }
                    return;
                } finally {
                    clients.offer(httpClient);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static TradeResp getTradeResp(String url, HttpClient httpclient) {
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
            TradeResp tradeResp = JSON.parseObject(stream, TradeResp.class);
            return tradeResp;
        } catch (Exception e) {
            //            log.error("GetHistoricalMinAggregateTrade.getTrade error. url={}", url, e);
            e.printStackTrace();
        } finally {
            get.releaseConnection();
        }
        return null;
    }

}
