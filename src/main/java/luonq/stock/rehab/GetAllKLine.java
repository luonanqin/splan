package luonq.stock.rehab;

import bean.StockKLine;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;
import util.BaseUtils;
import util.Constants;

import java.io.File;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class GetAllKLine {

    public static BlockingQueue<HttpClient> queue;
    public static ThreadPoolExecutor cachedThread;
    public static String apiKey = "&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY";
    public static String api = "https://api.polygon.io/v1/open-close/";

    public static void init() throws Exception {
        int threadCount = 20;
        int corePoolSize = threadCount;
        int maximumPoolSize = corePoolSize;
        long keepAliveTime = 60L;
        LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
        cachedThread = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS, workQueue);
        queue = new LinkedBlockingQueue<>(threadCount);
        for (int i = 0; i < threadCount; i++) {
            queue.offer(new HttpClient());
        }
    }

    public static void reGrabAllKLine(String stock, List<String> dateList) throws Exception {
        String backupPath = Constants.HIS_BASE_PATH + "merge_backup/" + stock;
        String path = Constants.HIS_BASE_PATH + "merge/" + stock;
        File backup = new File(backupPath);
        File file = new File(path);
        if (file.exists() && backup.exists()) {
            System.out.println(stock + " has reGrab");
            return;
        }
        if (!file.exists()) {
            System.out.println(stock + " has moved");
        } else {
            boolean moveRes = file.renameTo(backup);
            if (!moveRes) {
                System.out.println("backup failed: " + stock);
                return;
            } else {
                System.out.println("backup success: " + stock);
            }
        }

        List<StockKLine> historicalDaily = getHistoricalDaily(stock, dateList);
        List<String> lines = historicalDaily.stream().map(StockKLine::toString).collect(Collectors.toList());
        BaseUtils.writeFile(Constants.HIS_BASE_PATH + "merge/" + stock, lines);

        System.out.println("finish " + stock);
    }

    public static List<StockKLine> getHistoricalDaily(String stock, List<String> addDate) throws Exception {
        List<StockKLine> list = Collections.synchronizedList(Lists.newArrayList());

        CountDownLatch cdl = new CountDownLatch((addDate.size()));
        for (String date : addDate) {
            HttpClient httpClient = queue.take();
            cachedThread.execute(() -> {
                String url = api + stock + "/" + date + "?adjust=true&" + apiKey;
                GetMethod get = new GetMethod(url);

                try {
                    httpClient.executeMethod(get);
                    InputStream stream = get.getResponseBodyAsStream();
                    Map<String, Object> result = JSON.parseObject(stream, Map.class);
                    String status = MapUtils.getString(result, "status");
                    if (StringUtils.equals(status, "NOT_FOUND")) {
                        return;
                    }
                    if (!StringUtils.equals(status, "OK") && StringUtils.equals(status, "NOT_FOUND")) {
                        System.err.println(stock + " date=" + date + " status=" + status);
                        return;
                    }

                    String symbol = MapUtils.getString(result, "symbol");
                    if (!symbol.equals(stock)) {
                        System.err.println(stock + " date=" + date + " symbol=" + symbol);
                        return;
                    }

                    String from = MapUtils.getString(result, "from");
                    if (!from.equals(date)) {
                        System.err.println(stock + " date=" + date + " from=" + from);
                        return;
                    }

                    double open = MapUtils.getDouble(result, "open");
                    double close = MapUtils.getDouble(result, "close");
                    double high = MapUtils.getDouble(result, "high");
                    double low = MapUtils.getDouble(result, "low");
                    BigDecimal volume = BigDecimal.valueOf(MapUtils.getDouble(result, "volume"));

                    String formatDate = BaseUtils.unformatDate(date);
                    StockKLine kLine = StockKLine.builder().date(formatDate).open(open).close(close).high(high).low(low).volume(volume).build();
                    list.add(kLine);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    get.releaseConnection();
                    queue.offer(httpClient);
                    cdl.countDown();
                }
            });
        }

        cdl.await();
        Collections.sort(list, (o1, o2) -> {
            String date1 = o1.getDate();
            String date2 = o2.getDate();
            return BaseUtils.dateToInt(date2) - BaseUtils.dateToInt(date1);
        });
        return list;
    }

    public static void main(String[] args) throws Exception {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.http").setLevel(Level.INFO);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.INFO);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.commons").setLevel(Level.ERROR);

        init();

        List<StockKLine> stockKLines = BaseUtils.loadDataToKline(Constants.HIS_BASE_PATH + "merge/AAPL", 2024, 2010);
        List<String> dateList = stockKLines.stream().map(StockKLine::getFormatDate).collect(Collectors.toList());
        List<String> list = Lists.newArrayList("ACB", "ACMR", "AMC", "AMZN", "APLD", "CELH", "CGC", "DXCM", "EDU", "FTNT", "GME", "GOOG", "GOOGL", "GSK", "HIVE", "HUT", "INO", "IVR", "NLY", "PANW", "SHOP", "SNDL", "SPCE", "TSLA", "WKHS", "WMT");
        for (String stock : list) {
            long start = System.currentTimeMillis();
            reGrabAllKLine(stock, dateList);
            long end = System.currentTimeMillis();
            System.out.println(end - start);
        }
    }
}
