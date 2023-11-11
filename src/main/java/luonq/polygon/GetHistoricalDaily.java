package luonq.polygon;

import bean.StockKLine;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;
import util.BaseUtils;
import util.Constants;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by Luonanqin on 2023/5/5.
 */
public class GetHistoricalDaily {

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

    public static List<StockKLine> getHistoricalDaily(String stock, List<String> addDate, HttpClient httpClient) {
        List<StockKLine> list = Lists.newArrayList();
        for (String date : addDate) {
            String url = api + stock + "/" + date + "?adjust=true&" + apiKey;
            GetMethod get = new GetMethod(url);

            try {
                httpClient.executeMethod(get);
                InputStream stream = get.getResponseBodyAsStream();
                Map<String, Object> result = JSON.parseObject(stream, Map.class);
                String status = MapUtils.getString(result, "status");
                if (StringUtils.equals(status, "NOT_FOUND")) {
                    continue;
                }
                if (!StringUtils.equals(status, "OK") && StringUtils.equals(status, "NOT_FOUND")) {
                    System.err.println(stock + " date=" + date + " status=" + status);
                    return null;
                }

                String symbol = MapUtils.getString(result, "symbol");
                if (!symbol.equals(stock)) {
                    System.err.println(stock + " date=" + date + " symbol=" + symbol);
                    return null;
                }

                String from = MapUtils.getString(result, "from");
                if (!from.equals(date)) {
                    System.err.println(stock + " date=" + date + " from=" + from);
                    return null;
                }

                double open = MapUtils.getDouble(result, "open");
                double close = MapUtils.getDouble(result, "close");
                double high = MapUtils.getDouble(result, "high");
                double low = MapUtils.getDouble(result, "low");
                BigDecimal volume = BigDecimal.valueOf(MapUtils.getDouble(result, "volume"));

                String formatDate = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd")).format(Constants.FORMATTER);
                StockKLine kLine = StockKLine.builder().date(formatDate).open(open).close(close).high(high).low(low).volume(volume).build();
                list.add(kLine);
            } catch (Exception e) {
                System.out.println(stock + " " + e.getMessage());
            } finally {
                get.releaseConnection();
            }
        }

        return list;
    }

    public static void getData() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        int threadCount = 100;
        int corePoolSize = threadCount;
        int maximumPoolSize = corePoolSize;
        long keepAliveTime = 60L;
        LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
        Executor cachedThread = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS, workQueue);
        BlockingQueue<HttpClient> queue = new LinkedBlockingQueue<>(threadCount);
        for (int i = 0; i < threadCount; i++) {
            queue.offer(new HttpClient());
        }

        Map<String, String> stockMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "2023daily/");
        for (String stock : stockMap.keySet()) {
            if (invalidStock.contains(stock)) {
//                System.out.println("invalid stock: " + stock);
                continue;
            }
            if (!stock.equals("AAPL")) {
                //                continue;
            }
            String file = stockMap.get(stock);
            List<StockKLine> stockKLines = BaseUtils.loadDataToKline(file, 2023);
            List<String> addDate = Lists.newArrayList();
            if (CollectionUtils.isEmpty(stockKLines)) {
                System.out.println("no file " + stock);
                continue;
            }

            StockKLine stockKLine = stockKLines.get(0);
            String date = stockKLine.getDate();
            LocalDate latestDate = LocalDate.parse(date, Constants.FORMATTER);
            while (latestDate.isBefore(yesterday)) {
                latestDate = latestDate.plusDays(1);
                addDate.add(latestDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            }

            if (CollectionUtils.isEmpty(addDate)) {
                System.out.println("has get " + stock);
                continue;
            }
            HttpClient httpClient = queue.take();
            cachedThread.execute(() -> {
                List<StockKLine> dataList = null;
                try {
                    dataList = getHistoricalDaily(stock, addDate, httpClient);
                    if (CollectionUtils.isEmpty(dataList)) {
                        System.out.println("no data " + stock);
                        return;
                    }
                    for (StockKLine kLine : dataList) {
                        stockKLines.add(0, kLine);
                    }
                    BaseUtils.writeStockKLine(file, stockKLines);
                } catch (Exception e) {
                    System.out.println(stock + " " + e.getMessage());
                } finally {
                    if (CollectionUtils.isNotEmpty(dataList)) {
                        System.out.println("get success " + stock);
                    }
                    queue.offer(httpClient);
                }
            });
        }
        while (true) {
            if (queue.size() == threadCount) {
                break;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.commons").setLevel(Level.ERROR);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.ERROR);

        getData();
        System.out.println("============ end ============");
    }
}
