package luonq.strategy;

import bean.OptionContracts;
import bean.OptionContractsResp;
import bean.OptionQuote;
import bean.OptionQuoteData;
import bean.OptionQuoteResp;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Strategy28 {

    public static CloseableHttpClient httpClient = HttpClients.createDefault();
    public static BlockingQueue<CloseableHttpClient> queue;
    public static ThreadPoolExecutor cachedThread;

    public static void init() {
        int threadCount = 15;
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

    /*
     * 获取期权代码，开盘价小于10等于的，行权价上限10下限0。开盘价大于10小于等于20的，行权价上限20下限10，以此类推
     * code=AAPL price=102.02 date=2024-04-01
     */
    public static List<String> getOptionCode(String code, double price, String date) throws Exception {
        int decade = (int) price / 10;
        int upPrice = (decade + 1) * 10;
        int downPrice = (decade == 0 ? 1 : decade) * 10;

        LocalDate day = LocalDate.parse(date, Constants.DB_DATE_FORMATTER);
        String upDate = day.plusMonths(1).withDayOfMonth(1).format(Constants.DB_DATE_FORMATTER);
        String url = String.format("https://api.polygon.io/v3/reference/options/contracts?contract_type=call&"
          + "underlying_ticker=%s&expired=true&order=desc&limit=10&sort=expiration_date&expiration_date.lte=%s&expiration_date.gt=%s&strike_price.lte=%d&stike_price.gte=%d"
          + "&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY", code, upDate, date, upPrice, downPrice);

        System.out.println(url);

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
            System.out.println(tickerList);

            return tickerList;
        } finally {
            get.releaseConnection();
        }
    }

    public static void getOptionQuote(List<String> optionCodeList, String date) throws Exception {
        int year = Integer.valueOf(date.substring(0, 4));
        LocalDateTime summerTime = BaseUtils.getSummerTime(year);
        LocalDateTime winterTime = BaseUtils.getWinterTime(year);

        LocalDateTime day = LocalDate.parse(date, Constants.DB_DATE_FORMATTER).atTime(0, 0);
        int openHour, closeHour;
        if (day.isAfter(summerTime) && day.isBefore(winterTime)) {
            openHour = 21;
            closeHour = 4;
        } else {
            openHour = 22;
            closeHour = 5;
        }

        LocalDateTime open = day.withHour(openHour).withMinute(30).withSecond(5);
        LocalDateTime close = day.plusDays(1).withHour(closeHour - 1).withMinute(59).withSecond(55);
        String openTS = String.valueOf(open.toInstant(ZoneOffset.of("+8")).toEpochMilli());
        String closeTS = String.valueOf(close.toInstant(ZoneOffset.of("+8")).toEpochMilli());
        String api = "https://api.polygon.io/v3/quotes/";
        String openUrl = String.format("?order=asc&limit=1"
          + "&timestamp.lt=%s000000&timestamp.gt=%s000000"
          + "&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY", closeTS, openTS);
        String closeUrl = String.format("?order=desc&limit=1"
          + "&timestamp.lt=%s000000&timestamp.gt=%s000000"
          + "&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY", closeTS, openTS);

        CountDownLatch cdl = new CountDownLatch(optionCodeList.size());
        Map<String, OptionQuoteData> dataMap = Maps.newHashMap();
        for (String code : optionCodeList) {
            cachedThread.execute(() -> {
                HttpGet openRequest = new HttpGet(api + code + openUrl);
                HttpGet closeRequest = new HttpGet(api + code + closeUrl);
                CloseableHttpClient httpClient = null;
                try {
                    httpClient = queue.take();
                    double openBuy = 0, openSell = 0, closeBuy = 0, closeSell = 0;

                    CloseableHttpResponse openExecute = httpClient.execute(openRequest);
                    InputStream openContent = openExecute.getEntity().getContent();
                    OptionQuoteResp openResp = JSON.parseObject(openContent, OptionQuoteResp.class);
                    List<OptionQuote> openResults = openResp.getResults();
                    if (CollectionUtils.isNotEmpty(openResults)) {
                        OptionQuote optionQuote = openResults.get(0);
                        openBuy = optionQuote.getBid_price();
                        openSell = optionQuote.getAsk_price();
                    }

                    CloseableHttpResponse closeExecute = httpClient.execute(closeRequest);
                    InputStream closeContent = closeExecute.getEntity().getContent();
                    OptionQuoteResp closeResp = JSON.parseObject(closeContent, OptionQuoteResp.class);
                    List<OptionQuote> closeResults = closeResp.getResults();
                    if (CollectionUtils.isNotEmpty(closeResults)) {
                        OptionQuote optionQuote = closeResults.get(0);
                        closeBuy = optionQuote.getBid_price();
                        closeSell = optionQuote.getAsk_price();
                    }

                    OptionQuoteData optionQuoteData = new OptionQuoteData();
                    optionQuoteData.setCode(code);
                    optionQuoteData.setOpenBuy(openBuy);
                    optionQuoteData.setOpenSell(openSell);
                    optionQuoteData.setCloseBuy(closeBuy);
                    optionQuoteData.setCloseSell(closeSell);

                    dataMap.put(code, optionQuoteData);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    queue.offer(httpClient);
                    cdl.countDown();
                    openRequest.releaseConnection();
                    closeRequest.releaseConnection();
                }
            });
        }
        cdl.await();

        List<OptionQuoteData> dataList = optionCodeList.stream().map(c -> dataMap.get(c)).collect(Collectors.toList());
        for (OptionQuoteData optionQuoteData : dataList) {
            System.out.println(optionQuoteData);
        }
    }

    public static void main(String[] args) throws Exception {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.http").setLevel(Level.INFO);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.INFO);

        init();

        //        getOptionCode("AGL", 7.75, "2024-01-05");
        //        getOptionCode("DADA", 2.14, "2024-01-08");
        List<String> codeList = Lists.newArrayList();
        codeList.add("O:DADA240119C00010000");
        codeList.add("O:DADA240119C00007500");
        codeList.add("O:DADA240119C00005000");
        codeList.add("O:DADA240119C00002500");
        codeList.add("O:DADA240119C00002000");
        codeList.add("O:DADA240119C00001500");
        codeList.add("O:DADA240119C00001000");
        codeList.add("O:DADA240119C00000500");
        codeList.add("O:DADA240119P00010000");
        codeList.add("O:DADA240119P00007500");
        codeList.add("O:DADA240119P00005000");
        codeList.add("O:DADA240119P00002500");
        codeList.add("O:DADA240119P00002000");
        codeList.add("O:DADA240119P00001500");
        codeList.add("O:DADA240119P00001000");
        codeList.add("O:DADA240119P00000500");

        getOptionQuote(codeList, "2024-01-08");
        cachedThread.shutdown();
    }
}
