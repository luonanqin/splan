package luonq.polygon;

import bean.StockKLine;
import com.google.common.collect.Lists;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.httpclient.HttpClient;
import util.BaseUtils;
import util.Constants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class CheckDaily {

    public static void main(String[] args) throws Exception {
        int threadCount = 50;
        int corePoolSize = threadCount;
        int maximumPoolSize = corePoolSize;
        long keepAliveTime = 60L;
        LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
        ThreadPoolExecutor cachedThread = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS, workQueue);
        BlockingQueue<HttpClient> queue = new LinkedBlockingQueue<>(threadCount);
        for (int i = 0; i < threadCount; i++) {
            queue.offer(new HttpClient());
        }

        Map<String, String> stockMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "merge");
        for (String stock : stockMap.keySet()) {
            String file = stockMap.get(stock);
            List<StockKLine> lines = BaseUtils.loadDataToKline(file, 2022, 2021);
            if (CollectionUtils.isEmpty(lines)) {
                continue;
            }
            StockKLine stockKLine = lines.get(0);
            HttpClient httpClient = queue.take();
            cachedThread.execute(() -> {
                try {
                    List<StockKLine> dataList = GetHistoricalDaily.getHistoricalDaily(stock, Lists.newArrayList("2022-12-30"), httpClient);
                    if (CollectionUtils.isEmpty(dataList)) {
//                        System.out.println("no data " + stock);
                        return;
                    }
                    StockKLine data = dataList.get(0);
                    double open1 = BigDecimal.valueOf(data.getOpen()).setScale(2, RoundingMode.HALF_DOWN).doubleValue();
                    double open2 = BigDecimal.valueOf(stockKLine.getOpen()).setScale(2, RoundingMode.HALF_DOWN).doubleValue();
                    if (open1 != open2) {
                        System.out.println(stock + " " + open1 + "=" + open2 + " " + data + " " + stockKLine);
                    }
                } catch (Exception e) {
                    System.out.println(stock + " " + e.getMessage());
                } finally {
                    queue.offer(httpClient);
                }
            });
        }
        cachedThread.shutdown();
        System.out.println("end");
    }
}
