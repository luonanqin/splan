package luonq.strategy;

import bean.OptionContracts;
import bean.OptionContractsResp;
import bean.StockKLine;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.LoggerFactory;
import util.BaseUtils;
import util.Constants;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 以05-23号的收盘价作为输入，获取24号的期权代码
 */
public class Strategy32 {

    public static BlockingQueue<CloseableHttpClient> queue;
    public static ThreadPoolExecutor cachedThread;
    public static String apiKey = "&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY";

    public static void init() {
        int threadCount = 100;
        int corePoolSize = threadCount;
        int maximumPoolSize = corePoolSize;
        long keepAliveTime = 60L;
        LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
        cachedThread = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS, workQueue);
        queue = new LinkedBlockingQueue<>(threadCount);
        for (int i = 0; i < threadCount; i++) {
            queue.offer(HttpClients.createDefault());
        }
    }

    public static List<String> getOptionCode(CloseableHttpClient httpClient, String code, double price, String date) throws Exception {
        int decade = (int) price / 10;
        int upPrice = (decade + 1) * 10;
        int downPrice = (decade == 0 ? 1 : decade) * 10;

        LocalDate today = LocalDate.now();
        LocalDate day = LocalDate.parse(date, Constants.DB_DATE_FORMATTER);
        String upDate = day.plusMonths(2).withDayOfMonth(1).format(Constants.DB_DATE_FORMATTER);

        boolean expired = true;
        if (today.isBefore(day.plusDays(30))) {
            expired = false;
            upDate = day.plusMonths(1).withDayOfMonth(1).format(Constants.DB_DATE_FORMATTER);
        }
        String url = String.format("https://api.polygon.io/v3/reference/options/contracts?contract_type=call&"
          + "underlying_ticker=%s&expired=%s&order=desc&limit=1&sort=expiration_date&expiration_date.lte=%s&expiration_date.gt=%s&strike_price.lte=%d&stike_price.gte=%d"
          + "&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY", code, true, upDate, date, upPrice, downPrice);

        //        System.out.println(url);

        HttpGet get = new HttpGet(url);
        try {
            CloseableHttpResponse execute = httpClient.execute(get);
            InputStream stream = execute.getEntity().getContent();
            OptionContractsResp resp = JSON.parseObject(stream, OptionContractsResp.class);
            String status = resp.getStatus();
            if (!StringUtils.equalsIgnoreCase(status, "OK")) {
                System.out.println("get failed. " + url);
                return Lists.newArrayListWithExpectedSize(0);
            }

            List<OptionContracts> results = resp.getResults();
            List<String> tickerList = results.stream().map(OptionContracts::getTicker).collect(Collectors.toList());
            //            System.out.println(tickerList);
            if (CollectionUtils.isEmpty(tickerList)) {
                return Lists.newArrayListWithExpectedSize(0);
            }

            String latestTicker = tickerList.get(tickerList.size() - 1);
            int i;
            for (i = tickerList.size() - 2; i >= 0; i--) {
                String ticker = tickerList.get(i);
                if (!StringUtils.equalsIgnoreCase(latestTicker.substring(0, latestTicker.length() - 8), ticker.substring(0, ticker.length() - 8))) {
                    break;
                }
            }
            tickerList = tickerList.subList(i + 1, tickerList.size());

            return tickerList;
        } finally {
            queue.offer(httpClient);
            get.releaseConnection();
        }
    }

    public static void main(String[] args) throws Exception {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.http").setLevel(Level.INFO);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.INFO);

        init();

        Map<String, String> klineFileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "2024/dailyKLine");
        int year = 2024;
        Set<String> optionStock = BaseUtils.getOptionStock();
        for (String stock : optionStock) {
            if (!stock.equals("AAPL")) {
//                continue;
            }
            String filePath = klineFileMap.get(stock);

            List<StockKLine> stockKLines = BaseUtils.loadDataToKline(filePath, year, year - 1);
            if (CollectionUtils.isEmpty(stockKLines)) {
                continue;
            }
            StockKLine stockKLine = stockKLines.get(0);
            String date = stockKLine.getFormatDate();
            if (!date.equals("2024-05-23")) {
                continue;
            }

            double close = stockKLine.getClose();
            CloseableHttpClient httpClient = queue.take();
            List<String> optionCodeList = getOptionCode(httpClient, stock, close, date);
            for (String optionCode : optionCodeList) {
                if (optionCode.contains("240524")) {
                    System.out.println(stock + "\t" + optionCode);
                    break;
                }
            }
        }
    }
}
