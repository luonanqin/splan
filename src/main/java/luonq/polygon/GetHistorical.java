package luonq.polygon;

import bean.StockKLine;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang3.StringUtils;
import util.BaseUtils;
import util.Constants;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by Luonanqin on 2023/5/5.
 */
public class GetHistorical {

    public static String apiKey = "apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY";
    public static String api = "https://api.polygon.io/v1/open-close/";

    public static StockKLine getHistoricalDaily(String stock, String date, HttpClient httpClient) {
        String url = api + stock + "/" + date + "?adjust=true&" + apiKey;
        GetMethod get = new GetMethod(url);

        try {
            httpClient.executeMethod(get);
            InputStream stream = get.getResponseBodyAsStream();
            Map<String, Object> result = JSON.parseObject(stream, Map.class);
            String status = MapUtils.getString(result, "status");
            if (StringUtils.equals(status, "NOT_FOUND")) {
                return null;
            }
            if (!StringUtils.equals(status, "OK") && StringUtils.equals(status, "NOT_FOUND")) {
                System.err.println(stock + " date=" + date + " status=" + status);
                return null;
            }

            if (StringUtils.equals(status, "ERROR")) {
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
            return kLine;
        } catch (Exception e) {
            System.out.println(stock + " error:" + e.getMessage());
        } finally {
            get.releaseConnection();
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
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

        LocalDate init = LocalDate.of(2000, 1, 1);
        List<String> dateList = Lists.newArrayList();
        while (true) {
            if (init.isBefore(yesterday)) {
                dateList.add(yesterday.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                yesterday = yesterday.minusDays(1);
            } else {
                break;
            }
        }
        Set<String> stocks = Sets.newHashSet("LGMK", "HLBZ", "LPCN", "BBIG", "XBIO", "JATT", "TGAA", "GRAY", "GREE", "SDIG", "SMFL", "SMFG", "VERO", "LCI", "TYDE", "DRMA", "BLIN", "HEPA", "SESN", "CR", "LITM", "SNGX", "GE", "MULN", "CGNX", "ML", "MDRR", "PR", "VAL", "EBF", "MTP", "CYCN", "XELA", "ENVX", "EQT", "GLMD", "DCFC", "POAI", "BNOX", "FRLN", "CINC", "NISN", "REFR", "CAPR", "SYRS", "ALPP", "RETO", "VISL", "GNLN", "JXJT", "SAFE", "EZFL", "IDRA", "CRESY", "IMPP", "ZEV", "EAST", "BIOC", "IFBD", "STAR", "AWH", "TNXP", "WORX", "VLON", "PSTV", "SFT", "AGRX", "MBIO", "APRE", "GAME", "VERB", "CFRX", "BLBX", "COMS", "RKDA", "WISH", "NXTP", "TR", "ARVL", "EJH", "MEIP", "ENSC", "NYMT", "PNTM", "ASNS", "AKAN", "RDFN", "GMBL", "VYNE", "MNST", "LCAA", "FRSX", "CRBP", "ATNX", "OIG", "REED", "OUST", "ALLR", "NAOV", "KRBP", "ICMB", "XOS", "GFAI", "GNUS", "BGXX", "FTFT", "AMST", "FCUV", "VBIV", "BIIB", "MINM", "CLXT", "DGLY", "MRKR");
        for (String stock : stocks) {
            if (!stock.equals("LGMK")) {
                continue;
            }
            List<StockKLine> stockKLines = Lists.newArrayList();
            List<StockKLine> kLines = Collections.synchronizedList(stockKLines);
            CountDownLatch cdl = new CountDownLatch(dateList.size());
            long l = System.currentTimeMillis();
            for (String date : dateList) {
                HttpClient httpClient = queue.take();
                cachedThread.execute(() -> {
                    try {
                        StockKLine kLine = getHistoricalDaily(stock, date, httpClient);
                        if (kLine != null) {
                            System.out.println(kLine);
                            kLines.add(kLine);
                        }
                    } catch (Exception e) {
                        System.out.println(stock + " error:" + e.getMessage());
                    } finally {
                        cdl.countDown();
                        queue.offer(httpClient);
                    }
                });
            }
            cdl.await();
            Collections.sort(kLines, (o1, o2) -> {
                String date1 = o1.getDate();
                String date2 = o2.getDate();
                return BaseUtils.dateToInt(date2) - BaseUtils.dateToInt(date1);
            });
            BaseUtils.writeStockKLine(Constants.HIS_BASE_PATH + "merge/" + stock, kLines);
            long l1 = System.currentTimeMillis();
            System.out.println("get success " + stock + " cost:" + ((l1 - l) / 1000));
        }
    }
}
