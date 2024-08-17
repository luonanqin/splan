package luonq.polygon;

import bean.Trade;
import bean.TradeResp;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.LoggerFactory;
import util.BaseUtils;
import util.Constants;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

public class GetOptionTrade {

    public static final String ERROR_MSG = "can't get pre market data";
    public static BlockingQueue<HttpClient> clients;
    public static ThreadPoolExecutor cachedThread;
    public static String urlString = "https://api.polygon.io/v3/trades/%s?order=asc&timestamp.gte=%s&timestamp.lte=%s&limit=100&sort=timestamp&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY";

    //    public static void init() {
    //        int threadCount = 100;
    //        int corePoolSize = threadCount;
    //        int maximumPoolSize = corePoolSize;
    //        long keepAliveTime = 60L;
    //        LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
    //        cachedThread = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS, workQueue);
    //        clients = new LinkedBlockingQueue<>(threadCount);
    //        for (int i = 0; i < threadCount; i++) {
    //            clients.offer(new HttpClient());
    //        }
    //    }

    public static void main(String[] args) throws Exception {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.http").setLevel(Level.INFO);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.INFO);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.commons").setLevel(Level.ERROR);

        //        init();
        //        Map<String, String> dailyFileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "week/");

        //        Set<String> stockSets = dailyFileMap.keySet();

        //        for (String stock : stockSets) {
        //            getData(stock);
        //        }
        List<String> list = getData(HttpClients.createDefault(), "O:AAPL240816C00225000", "1723815000000000000", "1723816800000000000");
        BaseUtils.writeFile(Constants.USER_PATH + "optionData/trade/AAPL/2024-08-16/test", list);
    }

    public static List<String> getData(CloseableHttpClient httpClient, String option, String beginTime, String endTime) {
        List<String> lines = Lists.newArrayList();

        String url = String.format(urlString, option, beginTime, endTime);
        TradeResp tradeResp = getTradeResp(url, httpClient);
        if (tradeResp == null) {
            return null;
        } else if (CollectionUtils.isNotEmpty(tradeResp.getResults())) {
            List<Trade> results = tradeResp.getResults();
            for (Trade result : results) {
                lines.add(result.getSip_timestamp() + "\t" + result.getPrice());
            }
        }

        return lines;
    }

    public static List<String> getAllData(CloseableHttpClient httpClient, String option, String beginTime, String endTime) {
        List<String> lines = Lists.newArrayList();

        String url = String.format(urlString, option, beginTime, endTime);
        while (true) {
            TradeResp tradeResp = getTradeResp(url, httpClient);
            if (tradeResp == null) {
                return null;
            } else if (CollectionUtils.isNotEmpty(tradeResp.getResults())) {
                List<Trade> results = tradeResp.getResults();
                for (Trade result : results) {
                    lines.add(result.getSip_timestamp() + "\t" + result.getPrice());
                }
                String next_url = tradeResp.getNext_url();
                if (StringUtils.isBlank(next_url)) {
                    break;
                } else {
                    url = next_url + "&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY";
                }
            }
        }

        return lines;
    }

    private static TradeResp getTradeResp(String url, CloseableHttpClient httpClient) {
        HttpGet get = new HttpGet(url);

        try {
            CloseableHttpResponse execute = null;
            int code = 0;
            for (int i = 0; i < 3; i++) {
                execute = httpClient.execute(get);
                code = execute.getStatusLine().getStatusCode();
                if (code == 200) {
                    break;
                }
            }
            if (code != 200) {
                return null;
            }

            InputStream stream = execute.getEntity().getContent();
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
