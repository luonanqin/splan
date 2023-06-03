package luonq.polygon;

import bean.StockKLine;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Sets;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang3.StringUtils;
import util.BaseUtils;
import util.Constants;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by Luonanqin on 2023/5/5.
 */
public class GetTodayOpen {

    public static String apiKey = "apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY";
    public static String api = "https://api.polygon.io/v1/open-close/";
    public static Set<String> invalidStock = Sets.newHashSet(
      "SCOB", "NVCN", "STOR", "MBAC", "DHHC", "MSAC", "STRE", "HLBZ", "MSDA", "GIAC", "WPCA",
      "SJI", "WPCB", "LGTO", "CNCE", "NVSA", "GRAY", "LHCG", "RACY", "KMPH", "PTOC", "LHDX", "TPBA",
      "AYLA", "IPAX", "KSI", "LYLT", "MCAE", "OIIM", "VVNT", "KNBE", "SMIH", "KVSC", "WQGA", "ELVT",
      "FOXW", "THAC", "BCOR", "FPAC", "SVFB", "SVFA", "PDOT", "AIMC", "DCT", "GSQD", "SESN", "COUP", "JCIC", "LION",
      "ARCK", "COWN", "DNZ", "CPAR", "CPAQ", "MCG", "MIT", "RKTA", "EVOP", "VGFC", "AAWW", "TZPS", "RCII", "OBSV",
      "MTP", "MEAC", "SJR", "APEN", "BLI", "CENQ", "JATT", "TYDE", "MLAI", "HERA", "VORB", "JMAC", "VPCB", "ABGI",
      "PFDR", "PFHD", "ESM", "HORI", "NGC", "FINM", "SGFY", "BNFT", "UMPQ", "DLCA", "DCRD", "DTRT", "FRON", "IBER",
      "ATCO", "FRSG", "PONO", "ACDI", "SPKB", "MFGP", "TBSA", "NAAC", "ALBO", "ACQR", "CIXX", "GEEX", "BSGA");

    public static double getTodayOpen(String stock, String date, HttpClient httpClient) {
        String url = api + stock + "/" + date + "?adjust=true&" + apiKey;
        GetMethod get = new GetMethod(url);

        try {
            httpClient.executeMethod(get);
            InputStream stream = get.getResponseBodyAsStream();
            Map<String, Object> result = JSON.parseObject(stream, Map.class);
            String status = MapUtils.getString(result, "status");
            if (StringUtils.equals(status, "NOT_FOUND")) {
                //                System.out.println(stock + " " + status);
                return 0;
            }
            if (!StringUtils.equals(status, "OK") && StringUtils.equals(status, "NOT_FOUND")) {
                System.err.println(stock + " date=" + date + " status=" + status);
                return 0;
            }

            String symbol = MapUtils.getString(result, "symbol");
            if (!symbol.equals(stock)) {
                System.err.println(stock + " date=" + date + " symbol=" + symbol);
                return 0;
            }

            String from = MapUtils.getString(result, "from");
            if (!from.equals(date)) {
                System.err.println(stock + " date=" + date + " from=" + from);
                return 0;
            }

            double open = MapUtils.getDouble(result, "open", 0d);
            return open;
        } catch (Exception e) {
            System.out.println(stock + " " + e.getMessage());
            return 0;
        } finally {
            get.releaseConnection();
        }
    }

    public static void main(String[] args) throws Exception {
        LocalDate today = LocalDate.now();
        //        today = today.minusDays(1);
        String todayDate = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        int threadCount = 200;
        int corePoolSize = threadCount;
        int maximumPoolSize = corePoolSize;
        long keepAliveTime = 160L;
        LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
        Executor cachedThread = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS, workQueue);
        BlockingQueue<HttpClient> queue = new LinkedBlockingQueue<>(threadCount);
        for (int i = 0; i < threadCount; i++) {
            queue.offer(new HttpClient());
        }

        Map<String, String> stockMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "2023daily/");
        Set<String> stockSet = Sets.newHashSet();
        for (String stock : stockMap.keySet()) {
            String file = stockMap.get(stock);
            List<StockKLine> lineList = BaseUtils.loadDataToKline(file, 2023);
            if (CollectionUtils.isEmpty(lineList)) {
                continue;
            }
            double close = lineList.get(0).getClose();
            if (close > 7) {
                stockSet.add(stock);
            }
        }

//        execute(todayDate, cachedThread, queue, stockSet);

        TimerTask task1 = new MyTimerTask(todayDate, cachedThread, queue, stockSet);
        TimerTask task2 = new MyTimerTask(todayDate, cachedThread, queue, stockSet);
        Timer timer = new Timer();
        Date date1 = Date.from(LocalDateTime.of(2023, 5, 17, 21, 29, 0).toInstant(ZoneOffset.ofHours(8)));
        Date date2 = Date.from(LocalDateTime.of(2023, 5, 17, 21, 30, 0).toInstant(ZoneOffset.ofHours(8)));
        timer.schedule(task1, date1);
        timer.schedule(task2, date2);
    }

    private static void execute(String todayDate, Executor cachedThread, BlockingQueue<HttpClient> queue, Set<String> stockSet) throws InterruptedException {
        long l1 = System.currentTimeMillis();
        for (String stock : stockSet) {
            if (invalidStock.contains(stock)) {
                //                System.out.println("invalid stock: " + stock);
                continue;
            }
            if (!stock.equals("AAPL")) {
                //                continue;
            }
            HttpClient httpClient = queue.take();
            cachedThread.execute(() -> {
                try {
                    //                    for (int i = 0; i < 10; i++) {
//                    long l = System.currentTimeMillis();
                    double open = getTodayOpen(stock, todayDate, httpClient);
//                    System.out.println(stock + " " + open + " " + (System.currentTimeMillis() - l));
                    System.out.println(stock + " " + open);
                    //                    }
                } catch (Exception e) {
                    System.out.println(stock + " " + e.getMessage());
                } finally {
                    //                    System.out.println("get success " + stock);
                    queue.offer(httpClient);
                }
            });
        }
        System.out.println("cost: " + (System.currentTimeMillis() - l1));
    }

    private static class MyTimerTask extends TimerTask {
        private final String todayDate;
        private final Executor cachedThread;
        private final BlockingQueue<HttpClient> queue;
        private final Set<String> stockSet;

        public MyTimerTask(String todayDate, Executor cachedThread, BlockingQueue<HttpClient> queue, Set<String> stockSet) {
            this.todayDate = todayDate;
            this.cachedThread = cachedThread;
            this.queue = queue;
            this.stockSet = stockSet;
        }

        @Override
        public void run() {
            try {
                System.out.println("start task");
                execute(todayDate, cachedThread, queue, stockSet);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
