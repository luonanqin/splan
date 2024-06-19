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
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.LoggerFactory;
import util.BaseUtils;
import util.Constants;

import java.io.File;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
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

    public static final String QUOTE_DIR = "optionData/optionQuote/";
    public static CloseableHttpClient httpClient = HttpClients.createDefault();
    public static BlockingQueue<HttpClient> queue;
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
            HttpClient e = new HttpClient();
            //            HttpConnectionManagerParams httpConnectionManagerParams = new HttpConnectionManagerParams();
            //            httpConnectionManagerParams.setConnectionTimeout(10000);
            //            httpConnectionManagerParams.setSoTimeout(10000);
            //            e.getHttpConnectionManager().setParams(httpConnectionManagerParams);
            queue.offer(e);
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
                GetMethod req = new GetMethod(url);
                HttpClient httpClient = null;
                try {
                    httpClient = queue.take();
                    httpClient.executeMethod(req);
                    InputStream openContent = req.getResponseBodyAsStream();
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
                GetMethod req = new GetMethod(url);
                HttpClient httpClient = null;
                try {
                    httpClient = queue.take();
                    httpClient.executeMethod(req);
                    InputStream openContent = req.getResponseBodyAsStream();
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

    public static OptionCode getOptionCodeBean(List<String> optionCodeList, double price, int callOrPut) {
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

        if (callOrPut == 0) {
            optionCode = BaseUtils.getOptionPutCode(optionCode);
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

    public static void getOptionQuoteList(OptionCode optionCodeBean, String date) throws Exception {
        if (optionCodeBean == null) {
            return;
        }
        String optionCode = optionCodeBean.getCode();
        String fileName = optionCode.substring(2);
        int _2_index = optionCode.indexOf("2");
        String stock = optionCode.substring(2, _2_index);
        String fileDirPath = Constants.USER_PATH + QUOTE_DIR + stock + "/" + date + "/";
        File fileDir = new File(fileDirPath);
        if (!fileDir.exists()) {
            fileDir.mkdirs();
        }
        String filePath = fileDirPath + fileName;
        File file = new File(filePath);
        if (file.exists()) {
            System.out.println(fileName + " has get quote");
            return;
        }

        List<String> dayAllSeconds = getDayAllSeconds(date);
        List<String> result = Lists.newArrayList();
        if (testIfExistQuote(dayAllSeconds, optionCode)) {
            CountDownLatch cdl = new CountDownLatch(dayAllSeconds.size() - 1);
            Map<String/* seconds */, String/* data */> dataMap = Maps.newHashMap();
            for (int i = 0; i < dayAllSeconds.size() - 1; i++) {
                String begin = dayAllSeconds.get(i);
                String end = dayAllSeconds.get(i + 1);
                cachedThread.execute(() -> {
                    String url = String.format("https://api.polygon.io/v3/quotes/%s?order=asc&limit=1"
                      + "&timestamp.lt=%s&timestamp.gt=%s%s", optionCode, end, begin, apiKey);
                    GetMethod openRequest = new GetMethod(url);
                    HttpClient httpClient = null;
                    try {
                        httpClient = queue.take();

                        httpClient.executeMethod(openRequest);
                        InputStream openContent = openRequest.getResponseBodyAsStream();
                        OptionQuoteResp openResp = JSON.parseObject(openContent, OptionQuoteResp.class);
                        if (openResp == null) {
                            System.out.println(url + " is null");
                        } else {
                            List<OptionQuote> openResults = openResp.getResults();
                            if (CollectionUtils.isNotEmpty(openResults)) {
                                dataMap.put(begin, openResults.get(0).print());
                            }
                        }
                    } catch (Exception e) {
                        System.out.println(begin + " " + url);
                        e.printStackTrace();
                    } finally {
                        queue.offer(httpClient);
                        cdl.countDown();
                        openRequest.releaseConnection();
                    }
                });
            }
            cdl.await();

            result.addAll(dataMap.values());
        }

        BaseUtils.writeFile(filePath, result);
        //        System.out.println(dataMap);
        sortQuote(filePath);
    }

    public static boolean testIfExistQuote(List<String> dayAllSeconds, String optionCode) throws Exception {
        String url = String.format("https://api.polygon.io/v3/quotes/%s?order=asc&limit=1"
          + "&timestamp.lt=%s&timestamp.gt=%s%s", optionCode, dayAllSeconds.get(dayAllSeconds.size() - 1), dayAllSeconds.get(0), apiKey);
        GetMethod get = new GetMethod(url);
        HttpClient httpClient = queue.take();
        try {
            httpClient = queue.take();

            httpClient.executeMethod(get);
            InputStream openContent = get.getResponseBodyAsStream();
            OptionQuoteResp openResp = JSON.parseObject(openContent, OptionQuoteResp.class);
            if (openResp == null) {
                return false;
            } else {
                List<OptionQuote> openResults = openResp.getResults();
                return CollectionUtils.isNotEmpty(openResults);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            queue.offer(httpClient);
            get.releaseConnection();
        }
        return true;
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
                GetMethod openRequest = new GetMethod(api + code + openUrl);
                GetMethod closeRequest = new GetMethod(api + code + closeUrl);
                HttpClient httpClient = null;
                try {
                    httpClient = queue.take();
                    double openBuy = 0, openSell = 0, closeBuy = 0, closeSell = 0;

                    httpClient.executeMethod(openRequest);
                    InputStream openContent = openRequest.getResponseBodyAsStream();
                    OptionQuoteResp openResp = JSON.parseObject(openContent, OptionQuoteResp.class);
                    List<OptionQuote> openResults = openResp.getResults();
                    if (CollectionUtils.isNotEmpty(openResults)) {
                        OptionQuote optionQuote = openResults.get(0);
                        openBuy = optionQuote.getBid_price();
                        openSell = optionQuote.getAsk_price();
                    }

                    httpClient.executeMethod(closeRequest);
                    InputStream closeContent = closeRequest.getResponseBodyAsStream();
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
        dataList.add("01/05/2023	APP	9.71");
        dataList.add("01/06/2023	GBX	30.13");
        dataList.add("01/09/2023	ARWR	30.05");
        dataList.add("01/10/2023	EURN	13.13");
        dataList.add("01/11/2023	ICHR	25.47");
        dataList.add("01/12/2023	LOGI	56.44");
        dataList.add("01/13/2023	DFH	9.5");
        dataList.add("01/17/2023	EDU	39.04");
        dataList.add("01/18/2023	CHGG	21.98");
        dataList.add("01/19/2023	SRDX	27.03");
        dataList.add("01/20/2023	FARO	27.16");
        dataList.add("01/23/2023	CPRX	18.32");
        dataList.add("01/24/2023	PBR	9.63");
        dataList.add("01/25/2023	EXTR	17.44");
        dataList.add("01/26/2023	CVLG	31.77");
        dataList.add("01/27/2023	SNBR	28.05");
        dataList.add("01/30/2023	VERA	7.42");
        dataList.add("01/31/2023	NAMS	13.45");
        dataList.add("02/01/2023	WRK	33.29");
        dataList.add("02/02/2023	VERA	7.13");
        dataList.add("02/03/2023	BILL	97.74");
        dataList.add("02/06/2023	PLCE	40.78");
        dataList.add("02/07/2023	CHGG	16.47");
        dataList.add("02/08/2023	CPRI	52.87");
        dataList.add("02/09/2023	TTGT	39.0");
        dataList.add("02/10/2023	LYFT	11.15");
        dataList.add("02/13/2023	FIS	65.4");
        dataList.add("02/14/2023	OM	23.35");
        dataList.add("02/15/2023	CRDO	10.67");
        dataList.add("02/16/2023	TOST	21.04");
        dataList.add("02/17/2023	UEIC	20.0");
        dataList.add("02/21/2023	CVRX	12.0");
        dataList.add("02/22/2023	ZIP	19.16");
        dataList.add("02/23/2023	BAND	19.89");
        dataList.add("02/24/2023	VICR	43.15");
        dataList.add("02/27/2023	TGNA	16.77");
        dataList.add("02/28/2023	AHCO	17.5");
        dataList.add("03/01/2023	XMTR	22.85");
        dataList.add("03/02/2023	FNKO	7.53");
        dataList.add("03/03/2023	CDNA	9.31");
        dataList.add("03/06/2023	ACRS	7.6");
        dataList.add("03/07/2023	CARA	7.29");
        dataList.add("03/08/2023	UNFI	26.61");
        dataList.add("03/09/2023	ARHS	11.21");
        dataList.add("03/10/2023	DOCU	53.71");
        dataList.add("03/13/2023	WAL	12.89");
        dataList.add("03/14/2023	GTLB	30.98");
        dataList.add("03/15/2023	APEI	7.05");
        dataList.add("03/16/2023	TITN	31.01");
        dataList.add("03/17/2023	MNTK	7.52");
        dataList.add("03/20/2023	PDD	79.93");
        dataList.add("03/21/2023	CTRN	20.39");
        dataList.add("03/22/2023	LAZR	7.18");
        dataList.add("03/23/2023	COIN	61.85");
        dataList.add("03/24/2023	OUST	8.3");
        dataList.add("03/27/2023	FYBR	22.45");
        dataList.add("03/28/2023	HRMY	34.0");
        dataList.add("03/29/2023	LOCO	9.15");
        dataList.add("03/30/2023	RNA	14.84");
        dataList.add("03/31/2023	AEHR	33.27");
        dataList.add("04/03/2023	ASND	67.12");
        dataList.add("04/04/2023	ZIM	18.15");
        dataList.add("04/05/2023	DLO	14.67");
        dataList.add("04/06/2023	LITE	45.92");
        dataList.add("04/10/2023	AUDC	12.9");
        dataList.add("04/11/2023	ADTN	11.28");
        dataList.add("04/12/2023	CUTR	23.29");
        dataList.add("04/13/2023	SRPT	121.88");
        dataList.add("04/14/2023	CTLT	46.77");
        dataList.add("04/17/2023	STT	67.0");
        dataList.add("04/18/2023	CLB	20.83");
        dataList.add("04/19/2023	CDW	163.69");
        dataList.add("04/20/2023	GFF	26.85");
        dataList.add("04/21/2023	MSGE	32.9");
        dataList.add("04/24/2023	AVTE	17.67");
        dataList.add("04/25/2023	TENB	35.152");
        dataList.add("04/26/2023	ENPH	178.63");
        dataList.add("04/27/2023	PI	90.21");
        dataList.add("04/28/2023	NET	44.41");
        dataList.add("05/01/2023	PLRX	24.0");
        dataList.add("05/02/2023	CHGG	9.25");
        dataList.add("05/03/2023	SPT	37.0");
        dataList.add("05/04/2023	EVA	8.7");
        dataList.add("05/05/2023	TRUP	24.0");
        dataList.add("05/08/2023	CTLT	35.6");
        dataList.add("05/09/2023	ENTA	24.0");
        dataList.add("05/10/2023	AMPL	9.31");
        dataList.add("05/11/2023	SONO	17.0");
        dataList.add("05/12/2023	SPNT	8.55");
        dataList.add("05/15/2023	BLBD	24.29");
        dataList.add("05/16/2023	VOXX	9.89");
        dataList.add("05/17/2023	SEAT	8.46");
        dataList.add("05/18/2023	BOOT	65.32");
        dataList.add("05/19/2023	FL	30.65");
        dataList.add("05/22/2023	CHDN	143.52");
        dataList.add("05/23/2023	IART	44.8");
        dataList.add("05/24/2023	NVTS	7.35");
        dataList.add("05/25/2023	APPS	9.14");
        dataList.add("05/26/2023	DLO	11.409");
        dataList.add("05/30/2023	SKY	58.61");
        dataList.add("05/31/2023	AAP	79.23");
        dataList.add("06/01/2023	MDU	20.46");
        dataList.add("06/02/2023	S	13.2");
        dataList.add("06/05/2023	CSTL	17.7");
        dataList.add("06/06/2023	COIN	47.1");
        dataList.add("06/07/2023	UNFI	19.98");
        dataList.add("06/08/2023	HCP	26.21");
        dataList.add("06/09/2023	CMTL	9.81");
        dataList.add("06/12/2023	NDAQ	52.1");
        dataList.add("06/13/2023	MEI	38.68");
        dataList.add("06/14/2023	AGL	18.6");
        dataList.add("06/15/2023	GAMB	9.53");
        dataList.add("06/16/2023	VSTM	9.91");
        dataList.add("06/20/2023	BBU	18.63");
        dataList.add("06/21/2023	QURE	13.18");
        dataList.add("06/22/2023	METC	7.76");
        dataList.add("06/23/2023	SOFI	7.91");
        dataList.add("06/26/2023	MRCY	29.0");
        dataList.add("06/27/2023	XPOF	19.7");
        dataList.add("06/28/2023	AXSM	75.0");
        dataList.add("06/29/2023	BTAI	8.64");
        dataList.add("06/30/2023	ROOT	8.5");
        dataList.add("07/03/2023	LH	206.15");
        dataList.add("07/05/2023	BWA	44.37");
        dataList.add("07/06/2023	CALT	15.84");
        dataList.add("07/07/2023	LEVI	13.0");
        dataList.add("07/10/2023	EXLS	29.894");
        dataList.add("07/11/2023	VRDN	19.7");
        dataList.add("07/12/2023	SILK	25.77");
        dataList.add("07/13/2023	VSAT	30.63");
        dataList.add("07/14/2023	TIXT	11.0");
        dataList.add("07/17/2023	APLS	64.75");
        dataList.add("07/18/2023	MASI	107.0");
        dataList.add("07/19/2023	TOST	24.0");
        dataList.add("07/20/2023	VIR	13.7");
        dataList.add("07/21/2023	IPG	34.78");
        dataList.add("07/24/2023	SAIA	383.39");
        dataList.add("07/25/2023	TBI	14.64");
        dataList.add("07/26/2023	MXL	24.33");
        dataList.add("07/27/2023	SHYF	16.13");
        dataList.add("07/28/2023	SNBR	28.46");
        dataList.add("07/31/2023	XPEV	20.65");
        dataList.add("08/01/2023	TGTX	11.88");
        dataList.add("08/02/2023	CMBM	10.58");
        dataList.add("08/03/2023	ONEW	27.0");
        dataList.add("08/04/2023	IEP	21.5");
        dataList.add("08/07/2023	SAGE	18.8");
        dataList.add("08/08/2023	MRC	8.56");
        dataList.add("08/09/2023	PUBM	14.48");
        dataList.add("08/10/2023	TASK	8.8");
        dataList.add("08/11/2023	JYNT	10.0");
        dataList.add("08/14/2023	HE	20.0");
        dataList.add("08/15/2023	SE	45.095");
        dataList.add("08/16/2023	AAON	63.0333");
        dataList.add("08/17/2023	HE	10.36");
        dataList.add("08/18/2023	KEYS	128.5");
        dataList.add("08/21/2023	CPRT	43.345");
        dataList.add("08/22/2023	DKS	116.75");
        dataList.add("08/23/2023	FL	15.9");
        dataList.add("08/24/2023	AMC	16.31");
        dataList.add("08/25/2023	DOMO	10.4");
        dataList.add("08/28/2023	NVCR	19.74");
        dataList.add("08/29/2023	SCVL	20.0");
        dataList.add("08/30/2023	MCFT	19.25");
        dataList.add("08/31/2023	LE	7.96");
        dataList.add("09/01/2023	PD	22.72");
        dataList.add("09/05/2023	MANU	20.9");
        dataList.add("09/06/2023	AMC	11.7");
        dataList.add("09/07/2023	YEXT	7.05");
        dataList.add("09/08/2023	HOFT	18.0");
        dataList.add("09/11/2023	SJM	131.18");
        dataList.add("09/12/2023	LAW	7.01");
        dataList.add("09/13/2023	AAOI	9.75");
        dataList.add("09/14/2023	IBEX	13.03");
        dataList.add("09/15/2023	PTCT	25.49");
        dataList.add("09/18/2023	MSGE	30.16");
        dataList.add("09/19/2023	RVNC	12.49");
        dataList.add("09/20/2023	EBC	12.21");
        dataList.add("09/21/2023	TVTX	7.55");
        dataList.add("09/22/2023	TVTX	7.38");
        dataList.add("09/25/2023	MORF	33.37");
        dataList.add("09/26/2023	UNFI	15.09");
        dataList.add("09/27/2023	SLNO	23.43");
        dataList.add("09/28/2023	WDAY	202.99");
        dataList.add("09/29/2023	AVD	10.25");
        dataList.add("10/02/2023	ARMK	25.38");
        dataList.add("10/03/2023	ENVX	10.09");
        dataList.add("10/04/2023	LAC	10.5");
        dataList.add("10/05/2023	OMAB	78.75");
        dataList.add("10/06/2023	AEHR	38.7501");
        dataList.add("10/09/2023	ALXO	7.69");
        dataList.add("10/10/2023	AKRO	19.87");
        dataList.add("10/11/2023	SILK	7.0");
        dataList.add("10/12/2023	INMD	24.14");
        dataList.add("10/13/2023	SGH	16.91");
        dataList.add("10/16/2023	VSTO	26.22");
        dataList.add("10/17/2023	NTCT	21.15");
        dataList.add("10/18/2023	TEX	49.66");
        dataList.add("10/19/2023	RTO	28.66");
        dataList.add("10/20/2023	SEDG	75.57");
        dataList.add("10/23/2023	MLI	35.3");
        dataList.add("10/24/2023	TBI	11.11");
        dataList.add("10/25/2023	VICR	39.01");
        dataList.add("10/26/2023	MXL	14.1");
        dataList.add("10/27/2023	PTCT	19.36");
        dataList.add("10/30/2023	ON	73.29");
        dataList.add("10/31/2023	SRPT	57.63");
        dataList.add("11/01/2023	PAYC	152.55");
        dataList.add("11/02/2023	CFLT	15.95");
        dataList.add("11/03/2023	FOXF	58.5");
        dataList.add("11/06/2023	MLTX	36.55");
        dataList.add("11/07/2023	AURA	8.05");
        dataList.add("11/08/2023	SNBR	10.82");
        dataList.add("11/09/2023	CDLX	8.52");
        dataList.add("11/10/2023	GRPN	9.84");
        dataList.add("11/13/2023	VERV	9.5");
        dataList.add("11/14/2023	HROW	9.7");
        dataList.add("11/15/2023	CELH	52.86");
        dataList.add("11/16/2023	PLCE	23.2");
        dataList.add("11/17/2023	AMSWA	9.55");
        dataList.add("11/20/2023	CHGG	9.555");
        dataList.add("11/21/2023	AEO	16.25");
        dataList.add("11/22/2023	GES	20.97");
        dataList.add("11/24/2023	SNEX	59.6333");
        dataList.add("11/27/2023	RDVT	19.59");
        dataList.add("11/28/2023	CTRN	21.57");
        dataList.add("11/29/2023	EGRX	7.02");
        dataList.add("11/30/2023	MOV	25.685");
        dataList.add("12/01/2023	WOR	47.11");
        dataList.add("12/04/2023	ALK	33.67");
        dataList.add("12/05/2023	DBI	8.66");
        dataList.add("12/06/2023	ASAN	19.25");
        dataList.add("12/07/2023	CXM	11.99");
        dataList.add("12/08/2023	HCP	19.8");
        dataList.add("12/11/2023	BMEA	12.75");
        dataList.add("12/12/2023	ORCL	102.7");
        dataList.add("12/13/2023	TH	9.75");
        dataList.add("12/14/2023	APLS	53.27");
        dataList.add("12/15/2023	NYMT	8.885");
        dataList.add("12/18/2023	MIRM	27.37");
        dataList.add("12/19/2023	INMB	9.3");
        dataList.add("12/20/2023	ARGX	345.61");
        dataList.add("12/21/2023	SAVA	25.85");
        dataList.add("12/22/2023	NTES	82.0");
        dataList.add("12/26/2023	ZIM	10.75");
        dataList.add("12/27/2023	IOVA	7.06");
        dataList.add("12/28/2023	ELP	8.44");
        dataList.add("12/29/2023	LYFT	14.9");
        List<String> result = Lists.newArrayList();
        for (String str : dataList) {
            String[] split = str.split("\t");
            String date = split[0];
            date = BaseUtils.formatDate(date);

            result.add(date + "\t" + split[1] + "\t" + split[2]);
        }

        return result;
    }

    public static void calOptionQuote(String code, Double price, String date) throws Exception {
        List<String> optionCode = getOptionCode(code, price, date);
        OptionTrade callTrade = getOptionQuote(optionCode, date, price, 1);

        List<String> putOptionCode = optionCode.stream().map(BaseUtils::getOptionPutCode).collect(Collectors.toList());
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
        LocalDateTime open = day.withHour(openHour).withMinute(30).withSecond(0);
        long openMilli = open.toInstant(ZoneOffset.of("+8")).toEpochMilli();
        List<String> result = Lists.newLinkedList();
        for (int i = 0; i < secondsCount; i++) {
            result.add(String.valueOf(openMilli * 1000000));
            openMilli += 1000;

        }

        return result;
    }

    public static void sortQuote(String filePath) throws Exception {
        List<String> lines = BaseUtils.readFile(new File(filePath));
        if (CollectionUtils.isEmpty(lines)) {
            return;
        }

        Map<Long, String> map = Maps.newTreeMap((o1, o2) -> o1.compareTo(o2));
        for (String line : lines) {
            String[] split = line.split("\t");
            String time = split[0];
            map.put(Long.valueOf(time), line);
        }

        lines.clear();
        for (Long time : map.keySet()) {
            lines.add(map.get(time));
        }
        BaseUtils.writeFile(filePath, lines);
    }

    public static void main(String[] args) throws Exception {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.http").setLevel(Level.INFO);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.INFO);

        init();

        //        List<String> dayAllSeconds = getDayAllSeconds("2024-01-05");
        //        calOptionQuote("AGL", 7.75, "2024-01-05");
        //        getOptionQuoteList("O:AGL240119C00007500", "2024-01-05");
        //        calOptionQuote("DADA", 2.14, "2024-01-08");
        //        calOptionQuote("GRFS", 7.42, "2024-01-09");
        //        calOptionQuote("LW", 88.53, "2024-04-04");
        //        calOptionQuote("LI", 22.72, "2024-05-20");
        List<String> codeList = Lists.newArrayList();
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
            //            calOptionQuote(code, price, date);
            if (!code.equals("AGL11111")) {
                //                continue;
            }

            List<String> optionCode = getOptionCode(code, price, date);
            if (CollectionUtils.isNotEmpty(optionCode)) {
                OptionCode callOptionCodeBean = getOptionCodeBean(optionCode, price, 1);
                if (callOptionCodeBean == null) {
                    continue;
                }
                System.out.println(data + "\t" + callOptionCodeBean.getCode());
                getOptionQuoteList(callOptionCodeBean, date);
                sortQuote(Constants.USER_PATH + QUOTE_DIR + callOptionCodeBean.getCode().substring(2));

                OptionCode putOptionCodeBean = getOptionCodeBean(optionCode, price, 0);
                sortQuote(Constants.USER_PATH + QUOTE_DIR + putOptionCodeBean.getCode().substring(2));
                getOptionQuoteList(putOptionCodeBean, date);
                System.out.println(data + "\t" + putOptionCodeBean.getCode());
            }
            //            System.out.println();
        }

        List<String> optionlist = Lists.newArrayList();

        Map<String, String> codeToDateMap = Maps.newHashMap();
        for (String data : dataList) {
            String[] split = data.split("\t");
            String date = split[0];
            String code = split[1];
            codeToDateMap.put(code, date);
        }
        for (String option : optionlist) {
            OptionCode optionCodeBean = new OptionCode();
            optionCodeBean.setCode(option);
            String optionCode = option.substring(2);
            String date = null;
            for (String code : codeToDateMap.keySet()) {
                if (optionCode.startsWith(code)) {
                    date = codeToDateMap.get(code);
                    break;
                }
            }

            sortQuote(Constants.USER_PATH + QUOTE_DIR + optionCode);
            getOptionQuoteList(optionCodeBean, date);
            sortQuote(Constants.USER_PATH + QUOTE_DIR + optionCode);

            System.out.println(option + " " + date);
        }
        cachedThread.shutdown();
    }
}
