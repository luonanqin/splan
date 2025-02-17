package luonq.polygon;

import bean.StockKLine;
import bean.TickerDetailV3;
import bean.TickerDetailV3Resp;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.LoggerFactory;
import util.BaseUtils;
import util.Constants;

import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * https://api.polygon.io/v3/reference/tickers/AAPL?date=2025-02-04&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY
 * Created by Luonanqin on 2023/1/17.
 */
public class ListTickerDetailV3 {

    public static BlockingQueue<CloseableHttpClient> queue;
    public static ThreadPoolExecutor threadPool;

    public static void init() {
        int threadCount = 100;
        int corePoolSize = threadCount;
        int maximumPoolSize = corePoolSize;
        long keepAliveTime = 60L;
        LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
        threadPool = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS, workQueue);
        queue = new LinkedBlockingQueue<>(threadCount);
        for (int i = 0; i < threadCount; i++) {
            queue.offer(HttpClients.createDefault());
        }
    }

    public static List<TickerDetailV3> getDetailList(Map<String, String> urlMap) throws Exception {
        List<TickerDetailV3> detailV3s = Lists.newArrayListWithExpectedSize(0);
        if (MapUtils.isEmpty(urlMap)) {
            return detailV3s;
        }

        CountDownLatch cdl = new CountDownLatch(urlMap.size());
        for (String date : urlMap.keySet()) {
            String url = urlMap.get(date);
            CloseableHttpClient httpClient = queue.take();

            threadPool.execute(() -> {
                HttpGet getMethod = new HttpGet(url);
                try {
                    CloseableHttpResponse execute = httpClient.execute(getMethod);
                    InputStream content = execute.getEntity().getContent();
                    TickerDetailV3Resp tickerResp = JSON.parseObject(content, TickerDetailV3Resp.class);
                    TickerDetailV3 tickerDetailV3 = tickerResp.getResults();
                    tickerDetailV3.setDate(date);
                    detailV3s.add(tickerDetailV3);
                } catch (Exception e) {
                } finally {
                    getMethod.releaseConnection();
                    queue.offer(httpClient);
                    cdl.countDown();
                }
            });
        }
        cdl.await();
        return detailV3s;
    }

    public static void main(String[] args) throws Exception {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.commons").setLevel(Level.ERROR);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache").setLevel(Level.ERROR);

        init();
        String apiKeyParam = "apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY";

        //        List<String> stockList = BaseUtils.getHasOptionStockList(market);
        Map<String, String> fileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "2025/dailyKLine");
        List<String> stockList = Lists.newArrayList(fileMap.keySet());
        List<Integer> yearList = Lists.newArrayList(2022, 2023, 2024);

        for (Integer year : yearList) {
            List<StockKLine> aaplList = BaseUtils.loadDataToKline(Constants.HIS_BASE_PATH + "/merge/AAPL", year, year - 1);
            List<String> dateList = aaplList.stream().map(StockKLine::getFormatDate).collect(Collectors.toList());

            for (String stock : stockList) {
                String filePath = Constants.HIS_BASE_PATH + year + "/sharing/" + stock;
                if (BaseUtils.fileExist(filePath)) {
                    continue;
                }
                Map<String, TickerDetailV3> dateToSharingMap = Maps.newTreeMap(Comparator.comparing(BaseUtils::formatDateToInt));

                Map<String, String> urlMap = Maps.newHashMap();
                dateList.forEach(date -> urlMap.put(date, "https://api.polygon.io/v3/reference/tickers/" + stock + "?date=" + date + "&" + apiKeyParam));

                List<TickerDetailV3> detailList = getDetailList(urlMap);
                if (CollectionUtils.isEmpty(detailList)) {
                    BaseUtils.writeFile(filePath, Lists.newArrayList());
                    continue;
                }
                for (TickerDetailV3 d : detailList) {
                    if (d != null) {
                        dateToSharingMap.put(d.getDate(), d);
                    }
                }

                List<String> list = Lists.newArrayList();
                for (String date : dateToSharingMap.keySet()) {
                    TickerDetailV3 detail = dateToSharingMap.get(date);
                    list.add(date + "\t" + detail.getShare_class_shares_outstanding() + "\t" + detail.getMarket_cap());
                }
                BaseUtils.writeFile(filePath, list);
                System.out.println(System.currentTimeMillis() + " " + stock);
            }
        }
    }
}
