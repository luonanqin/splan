package luonq.strategy;

import bean.OptionCode;
import bean.OptionContracts;
import bean.OptionContractsResp;
import bean.OptionQuote;
import bean.OptionQuoteData;
import bean.OptionQuoteResp;
import bean.OptionTrade;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
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

    /*
     * 获取期权代码，开盘价小于10等于的，行权价上限10下限0。开盘价大于10小于等于20的，行权价上限20下限10，以此类推
     * code=AAPL price=102.02 date=2024-04-01
     */
    public static List<String> getOptionCode(String code, double price, String date) throws Exception {
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
          + "underlying_ticker=%s&expired=%s&order=desc&limit=100&sort=expiration_date&expiration_date.lte=%s&expiration_date.gt=%s&strike_price.lte=%d&stike_price.gte=%d"
          + "&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY", code, String.valueOf(expired), upDate, date, upPrice, downPrice);

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
            get.releaseConnection();
        }
    }

    /*
     * 根据开盘价，计算该价格前后对应的行权价及期权代码，获取这些期权的开盘报价及收盘报价
     * 1.开盘报价列表选开盘后的前十个
     * 2.收盘报价列表选收盘前一分钟之后的前十个，超过十个选十个，不满十个按实际情况选择
     * callOrPut = 1 is call, = 0 is put
     */
    public static OptionTrade getOptionQuote(List<String> optionCodeList, String date, double price, int callOrPut) throws Exception {
        OptionCode optionCodeBean = getOptionCodeBean(optionCodeList, price, callOrPut);
        if (optionCodeBean == null) {
            return null;
        }

        //        List<String> actualOptionCodeList = Lists.newArrayList(upOptionCode, downOptionCode, equalOptionCode)
        //          .stream().filter(StringUtils::isNotBlank).collect(Collectors.toList());
        List<String> actualOptionCodeList = Lists.newArrayList(optionCodeBean.getCode());

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

        LocalDateTime open = day.withHour(openHour).withMinute(30).withSecond(0);
        LocalDateTime openEnd = day.withHour(openHour).withMinute(59).withSecond(0);
        LocalDateTime beforeClose = day.plusDays(1).withHour(closeHour - 1).withMinute(59).withSecond(0);
        LocalDateTime close = day.plusDays(1).withHour(closeHour).withMinute(0).withSecond(0);
        String openTS = String.valueOf(open.toInstant(ZoneOffset.of("+8")).toEpochMilli());
        String openEndTS = String.valueOf(openEnd.toInstant(ZoneOffset.of("+8")).toEpochMilli());
        String beforeCloseTS = String.valueOf(beforeClose.toInstant(ZoneOffset.of("+8")).toEpochMilli());
        String closeTS = String.valueOf(close.toInstant(ZoneOffset.of("+8")).toEpochMilli());
        String api = "https://api.polygon.io/v3/quotes/";
        String openUrl = String.format("?order=asc&limit=100"
          + "&timestamp.lt=%s000000&timestamp.gt=%s000000"
          + "&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY", openEndTS, openTS);
        String closeUrl = String.format("?order=desc&limit=1"
          + "&timestamp.lt=%s000000&timestamp.gt=%s000000"
          + "&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY", beforeCloseTS, openEndTS);

        List<String> openUrlList = actualOptionCodeList.stream().filter(StringUtils::isNotBlank).map(code -> api + code + openUrl).collect(Collectors.toList());
        List<String> closeUrlList = actualOptionCodeList.stream().filter(StringUtils::isNotBlank).map(code -> api + code + closeUrl).collect(Collectors.toList());

        CountDownLatch cdl = new CountDownLatch(actualOptionCodeList.size() * 2);
        Map<String, List<OptionQuote>> dataMap = Maps.newHashMap();
        for (String url : openUrlList) {
            cachedThread.execute(() -> {
                HttpGet req = new HttpGet(url);
                CloseableHttpClient httpClient = null;
                try {
                    httpClient = queue.take();
                    CloseableHttpResponse openExecute = httpClient.execute(req);
                    InputStream openContent = openExecute.getEntity().getContent();
                    OptionQuoteResp openResp = JSON.parseObject(openContent, OptionQuoteResp.class);
                    List<OptionQuote> openResults = openResp.getResults();
                    if (CollectionUtils.isNotEmpty(openResults)) {
                        openResults.stream().forEach(o -> {
                            o.setType("open");
                        });
                        dataMap.put(url, openResults);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    queue.offer(httpClient);
                    cdl.countDown();
                    req.releaseConnection();
                }
            });
        }
        for (String url : closeUrlList) {
            cachedThread.execute(() -> {
                HttpGet req = new HttpGet(url);
                CloseableHttpClient httpClient = null;
                try {
                    httpClient = queue.take();
                    CloseableHttpResponse openExecute = httpClient.execute(req);
                    InputStream openContent = openExecute.getEntity().getContent();
                    OptionQuoteResp openResp = JSON.parseObject(openContent, OptionQuoteResp.class);
                    List<OptionQuote> openResults = openResp.getResults();
                    if (CollectionUtils.isNotEmpty(openResults)) {
                        openResults.stream().forEach(o -> {
                            o.setType("close");
                        });
                        dataMap.put(url, openResults);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    queue.offer(httpClient);
                    cdl.countDown();
                    req.releaseConnection();
                }
            });
        }
        cdl.await();

        OptionTrade optionTrade = new OptionTrade();
        int strikePriceDiff = Math.abs(optionCodeBean.getNextDigitalStrikePrice() - optionCodeBean.getActualDigitalStrikePrice());
        int strikeDigitalPriceDiff = Math.abs(optionCodeBean.getDigitalStrikePrice() - optionCodeBean.getActualDigitalStrikePrice());
        optionTrade.setStrikePriceDiffRatio((double) strikeDigitalPriceDiff / (double) strikePriceDiff);
        optionTrade.setCode(optionCodeBean.getCode());
        for (String openurl : openUrlList) {
            int start = openurl.indexOf(api) + api.length();
            int end = openurl.indexOf("?");
            String code = openurl.substring(start, end);

            List<OptionQuote> optionQuotes = dataMap.get(openurl);
            if (CollectionUtils.isNotEmpty(optionQuotes)) {
                //                System.out.println(code);
                for (OptionQuote optionQuote : optionQuotes) {
                    //                    System.out.println(optionQuote);
                    if (optionQuote.getAsk_size() > 5 && optionQuote.getBid_size() > 5) {
                        //                        System.out.println(optionQuote);
                        double ask_price = optionQuote.getAsk_price();
                        double bid_price = optionQuote.getBid_price();
                        optionTrade.setBuy(BigDecimal.valueOf((ask_price + bid_price) / 2).setScale(2, RoundingMode.HALF_UP).doubleValue());
                        break;
                    }
                }
            }
        }
        for (String closeurl : closeUrlList) {
            int start = closeurl.indexOf(api) + api.length();
            int end = closeurl.indexOf("?");
            String code = closeurl.substring(start, end);

            List<OptionQuote> optionQuotes = dataMap.get(closeurl);
            if (CollectionUtils.isNotEmpty(optionQuotes)) {
                //                System.out.println(code);
                for (OptionQuote optionQuote : optionQuotes) {
                    //                    System.out.println(optionQuote);
                    //                    if (optionQuote.getAsk_size() > 0 && optionQuote.getBid_size() > 0) {
                    //                    System.out.println(optionQuote);
                    double ask_price = optionQuote.getAsk_price();
                    double bid_price = optionQuote.getBid_price();
                    optionTrade.setSell(BigDecimal.valueOf((ask_price + bid_price) / 2).setScale(2, RoundingMode.HALF_UP).doubleValue());
                    break;
                    //                    }
                }
            }
        }

        return optionTrade;
    }

    private static OptionCode getOptionCodeBean(List<String> optionCodeList, double price, int callOrPut) {
        // 找出当前价前后的行权价及等于当前价的行权价
        int decade = (int) price;
        int count = String.valueOf(decade).length();

        int standardCount = count + 3;
        String priceStr = String.valueOf(price).replace(".", "");
        int lastCount = standardCount - priceStr.length();
        int digitalPrice = Integer.valueOf(priceStr) * (int) Math.pow(10, lastCount);

        String upOptionCode = null, downOptionCode = null, equalOptionCode = null;
        int priceDiff = Integer.MAX_VALUE;
        String optionCode = null;
        int nextStrikePrice = 0, actualStrikePrice = 0;
        for (int j = 0; j < optionCodeList.size(); j++) {
            String code = optionCodeList.get(j);
            int index = code.length() - 8;
            String temp = code.substring(index);
            int i;
            for (i = 0; i < temp.length(); i++) {
                if (temp.charAt(i) != '0') {
                    break;
                }
            }
            int strikePrice = Integer.parseInt(temp.substring(i));
            //            if (strikePrice > digitalPrice) {
            //                if (priceDiff > strikePrice - digitalPrice) {
            //                    priceDiff = strikePrice - digitalPrice;
            //                    upOptionCode = code;
            //                    optionCode = code;
            //                }
            //            } else if (strikePrice == digitalPrice) {
            //                equalOptionCode = code;
            //                optionCode = code;
            //                break;
            //            } else if (strikePrice < digitalPrice) {
            //                if (priceDiff > digitalPrice - strikePrice) {
            //                    priceDiff = digitalPrice - strikePrice;
            //                    optionCode = code;
            //                    downOptionCode = code;
            //                }
            //                break;
            //            }

            // call
            if (callOrPut == 1) {
                if (strikePrice > digitalPrice) {
                    actualStrikePrice = strikePrice;
                    if (j + 1 == optionCodeList.size()) {
                        return null;
                    }
                    nextStrikePrice = Integer.parseInt(optionCodeList.get(j + 1).substring(index).substring(i));
                    optionCode = code;
                } else {
                    break;
                }
            }
            // put
            if (callOrPut == 0) {
                if (strikePrice < digitalPrice) {
                    actualStrikePrice = strikePrice;
                    if (j - 1 < 0) {
                        return null;
                    }
                    nextStrikePrice = Integer.parseInt(optionCodeList.get(j - 1).substring(index).substring(i));
                    optionCode = code;
                    break;
                }
            }
        }

        if (StringUtils.isEmpty(optionCode)) {
            return null;
        }

        OptionCode optionCodeBean = new OptionCode();
        optionCodeBean.setCode(optionCode);
        optionCodeBean.setContractType(callOrPut == 1 ? "call" : "put");
        optionCodeBean.setStrikePrice(price);
        optionCodeBean.setActualDigitalStrikePrice(actualStrikePrice);
        optionCodeBean.setNextDigitalStrikePrice(nextStrikePrice);
        optionCodeBean.setDigitalStrikePrice(digitalPrice);
        return optionCodeBean;
    }

    public static void getOptionQuoteList(String optionCode, String date) throws Exception {
        List<String> dayAllSeconds = getDayAllSeconds(date);
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
        LocalDateTime close = day.plusDays(1).withHour(closeHour - 1).withMinute(59).withSecond(59);
        String openTS = String.valueOf(open.toInstant(ZoneOffset.of("+8")).toEpochMilli());
        String closeTS = String.valueOf(close.toInstant(ZoneOffset.of("+8")).toEpochMilli());

        CountDownLatch cdl = new CountDownLatch(dayAllSeconds.size() - 1);
        Map<String/* seconds */, String/* data */> dataMap = Maps.newTreeMap(Comparator.comparing(Long::valueOf));
        for (int i = 0; i < dayAllSeconds.size() - 1; i++) {
            String begin = dayAllSeconds.get(i);
            String end = dayAllSeconds.get(i + 1);
            cachedThread.execute(() -> {
                String url = String.format("https://api.polygon.io/v3/quotes/%s?order=asc&limit=1"
                  + "&timestamp.lt=%s&timestamp.gt=%s%s", optionCode, end, begin, apiKey);
                HttpGet openRequest = new HttpGet(url);
                CloseableHttpClient httpClient = null;
                try {
                    httpClient = queue.take();

                    CloseableHttpResponse openExecute = httpClient.execute(openRequest);
                    InputStream openContent = openExecute.getEntity().getContent();
                    OptionQuoteResp openResp = JSON.parseObject(openContent, OptionQuoteResp.class);
                    List<OptionQuote> openResults = openResp.getResults();
                    if (CollectionUtils.isNotEmpty(openResults)) {
                        dataMap.put(begin, openResults.get(0).print());
                    }

                    //                    dataMap.put(code, optionQuoteData);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    queue.offer(httpClient);
                    cdl.countDown();
                    openRequest.releaseConnection();
                }
            });
        }
        cdl.await();

        System.out.println(dataMap);
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

        List<OptionQuoteData> dataList = optionCodeList.stream().filter(c -> dataMap.containsKey(c)).map(c -> dataMap.get(c)).collect(Collectors.toList());
        for (OptionQuoteData optionQuoteData : dataList) {
            System.out.println(optionQuoteData);
        }
    }

    public static List<String> buildTestData() {
        List<String> dataList = Lists.newArrayList();
        dataList.add("01/04/2024	MBLY	28.33");
        dataList.add("01/05/2024	AGL	7.75");
        dataList.add("01/08/2024	PRTA	31.54");
        dataList.add("01/09/2024	GRFS	7.42");
        dataList.add("01/10/2024	AEHR	18.77");
        dataList.add("01/11/2024	RELL	10.95");
        dataList.add("01/12/2024	GRFS	7.28");
        dataList.add("01/16/2024	XPEV	10.99");
        dataList.add("01/17/2024	DH	7.76");
        dataList.add("01/18/2024	HUM	392.44");
        dataList.add("01/19/2024	IRBT	16.91");
        dataList.add("01/22/2024	ADM	56.88");
        dataList.add("01/23/2024	LOGI	84.67");
        dataList.add("01/24/2024	DD	64.45");
        dataList.add("01/25/2024	COLB	20.69");
        dataList.add("01/26/2024	HUBG	46.8");
        dataList.add("01/29/2024	IRBT	14.07");
        dataList.add("01/30/2024	CALX	33.49");
        dataList.add("01/31/2024	EXTR	13.51");
        dataList.add("02/01/2024	HWKN	56.84");
        dataList.add("02/02/2024	EXPO	72.26");
        dataList.add("02/05/2024	APD	227.0");
        dataList.add("02/06/2024	SCSC	31.01");
        dataList.add("02/07/2024	SNAP	12.03");
        dataList.add("02/08/2024	CENTA	33.28");
        dataList.add("02/09/2024	PLCE	8.5");
        dataList.add("02/12/2024	MNDY	199.0");
        dataList.add("02/13/2024	WCC	152.0");
        dataList.add("02/14/2024	QDEL	46.27");
        dataList.add("02/15/2024	NUS	13.37");
        dataList.add("02/16/2024	COO	93.24");
        dataList.add("02/20/2024	RAPT	8.46");
        dataList.add("02/21/2024	AMPL	9.22");
        dataList.add("02/22/2024	GSHD	60.57");
        dataList.add("02/23/2024	WMT	58.6967");
        dataList.add("02/26/2024	EPIX	7.22");
        dataList.add("02/27/2024	AAN	8.84");
        dataList.add("02/28/2024	IAS	11.35");
        dataList.add("02/29/2024	CC	18.0");
        dataList.add("03/01/2024	JAKK	27.0");
        dataList.add("03/04/2024	GIII	30.96");
        dataList.add("03/05/2024	GTLB	60.0");
        dataList.add("03/06/2024	BMEA	14.59");
        dataList.add("03/07/2024	VSCO	18.69");
        dataList.add("03/08/2024	ASLE	7.3");
        dataList.add("03/11/2024	NECB	14.41");
        dataList.add("03/12/2024	ACAD	19.49");
        dataList.add("03/13/2024	DLTR	129.15");
        dataList.add("03/14/2024	PGY	13.1");
        dataList.add("03/15/2024	JBL	128.73");
        dataList.add("03/18/2024	SAIC	118.54");
        dataList.add("03/19/2024	DLO	15.61");
        dataList.add("03/20/2024	SIG	90.0");
        dataList.add("03/21/2024	DBI	8.3");
        dataList.add("03/22/2024	NKTX	10.0");
        dataList.add("03/25/2024	AEHR	11.67");
        dataList.add("03/26/2024	CDLX	15.74");
        dataList.add("03/27/2024	ODFL	219.135");
        dataList.add("03/28/2024	MLKN	23.9");
        dataList.add("04/01/2024	IRON	26.5");
        dataList.add("04/02/2024	VERV	8.67");
        dataList.add("04/03/2024	ULTA	469.57");
        dataList.add("04/04/2024	LW	88.53");
        dataList.add("04/05/2024	KEN	23.12");
        dataList.add("04/08/2024	PERI	13.09");
        dataList.add("04/09/2024	NEOG	12.76");
        dataList.add("04/10/2024	SGH	22.88");
        dataList.add("04/11/2024	LOVE	19.24");
        dataList.add("04/12/2024	CALT	19.24");
        dataList.add("04/15/2024	TRMD	33.08");
        dataList.add("04/16/2024	INO	8.39");
        dataList.add("04/17/2024	SAGE	12.96");
        dataList.add("04/18/2024	EFX	215.63");
        dataList.add("04/19/2024	NFLX	567.88");
        dataList.add("04/22/2024	CNHI	11.3");
        dataList.add("04/23/2024	XRX	14.6");
        dataList.add("04/24/2024	EVR	176.4");
        dataList.add("04/25/2024	JAKK	19.15");
        dataList.add("04/26/2024	SAIA	452.09");
        dataList.add("04/29/2024	DB	16.14");
        dataList.add("04/30/2024	MED	26.6");
        dataList.add("05/01/2024	CVRX	9.51");
        dataList.add("05/02/2024	FSLY	8.15");
        dataList.add("05/03/2024	SPT	33.99");
        dataList.add("05/06/2024	EYPT	13.89");
        dataList.add("05/07/2024	ENTA	11.6");
        dataList.add("05/08/2024	DV	18.67");
        dataList.add("05/09/2024	FWRD	12.87");
        dataList.add("05/10/2024	PGNY	23.9");
        dataList.add("05/13/2024	URGN	10.9");
        dataList.add("05/14/2024	MNSO	22.72");
        dataList.add("05/15/2024	DLO	9.39");
        dataList.add("05/16/2024	SPIR	8.5");
        dataList.add("05/17/2024	GME	21.86");
        dataList.add("05/20/2024	LI	22.72");
        List<String> result = Lists.newArrayList();
        for (String str : dataList) {
            String[] split = str.split("\t");
            String date = split[0];
            date = BaseUtils.formatDate(date);

            result.add(date + "\t" + split[1] + "\t" + split[2]);
        }

        return result;
    }

    public static String getOptionPutCode(String optionCallCode) {
        int c_index = optionCallCode.lastIndexOf("C");
        StringBuffer sb = new StringBuffer(optionCallCode);
        return sb.replace(c_index, c_index + 1, "P").toString();
    }

    public static void calOptionQuote(String code, Double price, String date) throws Exception {
        List<String> optionCode = getOptionCode(code, price, date);
        OptionTrade callTrade = getOptionQuote(optionCode, date, price, 1);

        List<String> putOptionCode = optionCode.stream().map(Strategy28::getOptionPutCode).collect(Collectors.toList());
        OptionTrade putTrade = getOptionQuote(putOptionCode, date, price, 0);

        if (callTrade == null || putTrade == null) {
            return;
        }

        String call = callTrade.getCode();
        String put = putTrade.getCode();
        double callBuy = callTrade.getBuy();
        double callSell = callTrade.getSell();
        double callStrikeDiffRatio = callTrade.getStrikePriceDiffRatio();
        double putBuy = putTrade.getBuy();
        double putSell = putTrade.getSell();
        double putStrikeDiffRatio = putTrade.getStrikePriceDiffRatio();
        if (callBuy == 0 || callSell == 0 || putBuy == 0 || putSell == 0) {
            return;
        }

        System.out.println(call + "\t" + callBuy + "\t" + callSell + "\t" + callStrikeDiffRatio + "\t" + put + "\t" + putBuy + "\t" + putSell + "\t" + putStrikeDiffRatio);
    }

    /**
     * 获取每天的开盘到收盘的每一秒，day=2024-01-02
     */
    public static List<String> getDayAllSeconds(String date) {
        int year = Integer.valueOf(date.substring(0, 4));
        LocalDateTime summerTime = BaseUtils.getSummerTime(year);
        LocalDateTime winterTime = BaseUtils.getWinterTime(year);

        LocalDateTime day = LocalDate.parse(date, Constants.DB_DATE_FORMATTER).atTime(0, 0);
        int openHour;
        if (day.isAfter(summerTime) && day.isBefore(winterTime)) {
            openHour = 21;
        } else {
            openHour = 22;
        }

        int secondsCount = 6 * 3600 + 1800;
        LocalDateTime open = day.withHour(openHour).withMinute(0).withSecond(0);
        long openMilli = open.toInstant(ZoneOffset.of("+8")).toEpochMilli();
        List<String> result = Lists.newLinkedList();
        for (int i = 0; i < secondsCount; i++) {
            result.add(String.valueOf(openMilli * 1000000));
            openMilli += 1000;

        }

        return result;
    }

    public static void main(String[] args) throws Exception {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.http").setLevel(Level.INFO);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.INFO);

        init();

        List<String> dayAllSeconds = getDayAllSeconds("2024-01-05");
        //        calOptionQuote("AGL", 7.75, "2024-01-05");
        getOptionQuoteList("O:AGL240119C00007500", "2024-01-05");
        //        calOptionQuote("DADA", 2.14, "2024-01-08");
        //        calOptionQuote("GRFS", 7.42, "2024-01-09");
        //        calOptionQuote("LW", 88.53, "2024-04-04");
        //        calOptionQuote("LI", 22.72, "2024-05-20");
        List<String> codeList = Lists.newArrayList();
        //        codeList.add("O:DB240503P00016000");
        //        codeList.add("O:DADA240119C00010000");
        //        codeList.add("O:DADA240119C00007500");
        //        codeList.add("O:DADA240119C00005000");
        //        codeList.add("O:DADA240119C00002500");
        //        codeList.add("O:DADA240119C00002000");
        //        codeList.add("O:DADA240119C00001500");
        //        codeList.add("O:DADA240119C00001000");
        //        codeList.add("O:DADA240119C00000500");
        //        codeList.add("O:DADA240119P00010000");
        //        codeList.add("O:DADA240119P00007500");
        //        codeList.add("O:DADA240119P00005000");
        //        codeList.add("O:DADA240119P00002500");
        //        codeList.add("O:DADA240119P00002000");
        //        codeList.add("O:DADA240119P00001500");
        //        codeList.add("O:DADA240119P00001000");
        //        codeList.add("O:DADA240119P00000500");

        //        getOptionQuote(codeList, "2024-04-29");

        List<String> dataList = buildTestData();
        for (String data : dataList) {
            String[] split = data.split("\t");
            String date = split[0];
            String code = split[1];
            Double price = Double.valueOf(split[2]);
            //            List<String> optionCallCode = getOptionCode(code, price, date);
            //            List<String> optionPutCode = optionCallCode.stream().map(c -> getOptionPutCode(c)).collect(Collectors.toList());
            //
            //            getOptionQuote(optionCallCode, date);
            //            System.out.println();
            //            getOptionQuote(optionPutCode, date);

            //            System.out.println(code);
            calOptionQuote(code, price, date);
            //            System.out.println();
        }

        cachedThread.shutdown();
    }
}
