package luonq.polygon;

import bean.OptionChain;
import bean.OptionChainResp;
import bean.StockKLine;
import bean.TickerDetailV3;
import bean.TickerDetailV3Resp;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import util.BaseUtils;
import util.Constants;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static util.Constants.USER_PATH;

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

    public List<TickerDetailV3> getDetailList(List<String> urlList) throws Exception {
        List<TickerDetailV3> detailV3s = Lists.newArrayListWithExpectedSize(0);
        if (CollectionUtils.isEmpty(urlList)) {
            return detailV3s;
        }

        CountDownLatch cdl = new CountDownLatch(urlList.size());
        for (String url : urlList) {
            CloseableHttpClient httpClient = queue.take();

            threadPool.execute(() -> {
                HttpGet getMethod = new HttpGet(url);
                List<String> callAndPut = Lists.newArrayList();
                try {
                    CloseableHttpResponse execute = httpClient.execute(getMethod);
                    InputStream content = execute.getEntity().getContent();
                    TickerDetailV3Resp tickerResp = JSON.parseObject(content, TickerDetailV3Resp.class);
                    TickerDetailV3 tickerDetailV3 = tickerResp.getResults();
                    detailV3s.add(tickerDetailV3);
                } catch (Exception e) {
                } finally {
                    getMethod.releaseConnection();
                    queue.offer(httpClient);
                    cdl.countDown();
                    //                    System.out.println("option chain cdl:" + cdl.getCount());
                }
            });
        }
        cdl.await();
        return detailV3s;
    }

    public static void main(String[] args) throws Exception {
        String market = "XNYS";

        String apiKeyParam = "apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY";

        //        List<String> stockList = BaseUtils.getHasOptionStockList(market);
        Map<String, String> fileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "2025/dailyKLine");
        List<String> stockList = Lists.newArrayList(fileMap.keySet());
        List<Integer> yearList = Lists.newArrayList(2022, 2023, 2024);

        HttpClient httpclient = new HttpClient();
        for (Integer year : yearList) {
            List<StockKLine> aaplList = BaseUtils.loadDataToKline(Constants.HIS_BASE_PATH + year + "/dailyKLline/AAPL", year + 1, year);
            List<String> dateList = aaplList.stream().map(StockKLine::getFormatDate).collect(Collectors.toList());

            for (String stock : stockList) {
                Map<String, String> dateToSharingMap = Maps.newHashMap();

                List<String> urlList = Lists.newArrayList();
                dateList.forEach(date -> urlList.add("https://api.polygon.io/v3/reference/tickers/" + stock + "?date=" + date + apiKeyParam));


            }
        }
        FileWriter fw;
        BufferedReader br;
        String fileName = String.format("%s.txt", market);
        try {
            fw = new FileWriter(fileName);
            br = new BufferedReader(new FileReader(Constants.HIS_BASE_PATH + "open/" + fileName));

            List<String> hasDownload = Lists.newArrayList();
            String download;
            while (StringUtils.isNotBlank(download = br.readLine())) {
                String code = download.substring(0, download.indexOf("\t"));
                hasDownload.add(code);
            }

            for (String stock : stockList) {
                if (hasDownload.contains(stock)) {
                    System.out.println("has downloaded: " + stock);
                    continue;
                }

                String url = "https://api.polygon.io/v3/reference/tickers/" + stock + "?" + apiKeyParam;
                GetMethod get = new GetMethod(url);
                int code = httpclient.executeMethod(get);
                if (code != 200) {
                    System.err.println(stock + " request failed. code=" + code);
                    continue;
                }

                InputStream stream = get.getResponseBodyAsStream();
                TickerDetailV3Resp tickerResp = JSON.parseObject(stream, TickerDetailV3Resp.class);
                TickerDetailV3 tickerDetailV3 = tickerResp.getResults();
                String data;
                if (tickerDetailV3 == null || StringUtils.isBlank(tickerDetailV3.getList_date()) || StringUtils.isBlank(tickerDetailV3.getTicker())) {
                    data = stock + "\t" + null + "\n";
                } else {
                    String listDate = tickerDetailV3.getList_date();
                    String tickerName = tickerDetailV3.getTicker();

                    data = tickerName + "\t" + listDate + "\n";
                }
                fw.write(data);
                fw.flush();

                System.out.print(data);
                TimeUnit.SECONDS.sleep(14);

                stream.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
