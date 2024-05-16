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

        LocalDate day = LocalDate.parse(date, Constants.DB_DATE_FORMATTER);
        String upDate = day.plusMonths(2).withDayOfMonth(1).format(Constants.DB_DATE_FORMATTER);
        String url = String.format("https://api.polygon.io/v3/reference/options/contracts?contract_type=call&"
          + "underlying_ticker=%s&expired=true&order=desc&limit=100&sort=expiration_date&expiration_date.lte=%s&expiration_date.gt=%s&strike_price.lte=%d&stike_price.gte=%d"
          + "&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY", code, upDate, date, upPrice, downPrice);

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
            String latestTicker = tickerList.get(tickerList.size() - 1);
            int i;
            for (i = tickerList.size() - 2; i >= 0; i--) {
                String ticker = tickerList.get(i);
                int c_index = ticker.lastIndexOf("C");
                if (!StringUtils.equalsIgnoreCase(latestTicker.substring(0, c_index), ticker.substring(0, c_index))) {
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
     */
    public static void getOptionQuote(List<String> optionCodeList, String date, double price) throws Exception {
        int decade = (int) price;
        int count = String.valueOf(decade).length();

        int standardCount = count + 3;
        String priceStr = String.valueOf(price).replace(".", "");
        int lastCount = standardCount - priceStr.length();
        int digitalPrice = Integer.valueOf(priceStr) * (int) Math.pow(10, lastCount);

        String upOptionCode = null, downOptionCode = null, equalOptionCode = null;
        for (String code : optionCodeList) {
            int c_index = code.lastIndexOf("C");
            String temp = code.substring(c_index + 1);
            int i;
            for (i = 0; i < temp.length(); i++) {
                if (temp.charAt(i) != '0') {
                    break;
                }
            }
            int strikePrice = Integer.parseInt(temp.substring(i));
            if (strikePrice > digitalPrice) {
                upOptionCode = code;
            } else if (strikePrice == digitalPrice) {
                equalOptionCode = code;
            } else if (strikePrice < digitalPrice) {
                downOptionCode = code;
                break;
            }
        }

        List<String> actualOptionCodeList = Lists.newArrayList(upOptionCode, downOptionCode, equalOptionCode)
          .stream().filter(StringUtils::isNotBlank).collect(Collectors.toList());

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
        LocalDateTime close = day.plusDays(1).withHour(closeHour - 1).withMinute(59).withSecond(0);
        String openTS = String.valueOf(open.toInstant(ZoneOffset.of("+8")).toEpochMilli());
        String closeTS = String.valueOf(close.toInstant(ZoneOffset.of("+8")).toEpochMilli());
        String api = "https://api.polygon.io/v3/quotes/";
        String openUrl = String.format("?order=asc&limit=10"
          + "&timestamp.lt=%s000000&timestamp.gt=%s000000"
          + "&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY", closeTS, openTS);
        String closeUrl = String.format("?order=desc&limit=10"
          + "&timestamp.lt=%s000000&timestamp.gt=%s000000"
          + "&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY", closeTS, openTS);

        List<String> urlList = Lists.newArrayList();
        actualOptionCodeList.stream().filter(StringUtils::isNotBlank).forEach(code -> urlList.add(api + code + openUrl));
        actualOptionCodeList.stream().filter(StringUtils::isNotBlank).forEach(code -> urlList.add(api + code + closeUrl));

        CountDownLatch cdl = new CountDownLatch(urlList.size());
        Map<String, List<OptionQuote>> dataMap = Maps.newHashMap();
        for (String code : actualOptionCodeList) {
            String url = api + code + openUrl;
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

//        List<OptionQuoteData> dataList = optionCodeList.stream().filter(c -> dataMap.containsKey(c)).map(c -> dataMap.get(c)).collect(Collectors.toList());
//        for (OptionQuoteData optionQuoteData : dataList) {
//            System.out.println(optionQuoteData);
//        }
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
        dataList.add("01/08/2024	DADA	2.14");
        dataList.add("01/08/2024	DADA	2.14");
        dataList.add("01/09/2024	GRFS	7.42");
        dataList.add("01/10/2024	CHWY	19.8");
        dataList.add("01/11/2024	GRFS	8.58");
        dataList.add("01/12/2024	GRFS	7.28");
        dataList.add("01/16/2024	WIT	5.83");
        dataList.add("01/17/2024	PHUN	0.1613");
        dataList.add("01/18/2024	PLUG	2.3");
        dataList.add("01/19/2024	IRBT	16.91");
        dataList.add("01/22/2024	ADM	56.88");
        dataList.add("01/23/2024	INO	0.61");
        dataList.add("01/24/2024	DD	64.45");
        dataList.add("01/25/2024	COLB	20.69");
        dataList.add("01/26/2024	HUBG	46.8");
        dataList.add("01/29/2024	IRBT	14.07");
        dataList.add("01/30/2024	CALX	33.49");
        dataList.add("01/31/2024	NYCB	5.96");
        dataList.add("02/01/2024	PSNY	1.8");
        dataList.add("02/02/2024	EXPO	72.26");
        dataList.add("02/05/2024	APD	227");
        dataList.add("02/06/2024	CCK	70.84");
        dataList.add("02/07/2024	SNAP	12.03");
        dataList.add("02/08/2024	CENTA	33.28");
        dataList.add("02/09/2024	PLCE	8.5");
        dataList.add("02/12/2024	BIG	4.25");
        dataList.add("02/13/2024	WCC	152");
        dataList.add("02/14/2024	QDEL	46.27");
        dataList.add("02/15/2024	AUPH	6");
        dataList.add("02/16/2024	COO	93.24");
        dataList.add("02/20/2024	NNOX	10.9");
        dataList.add("02/21/2024	AMPL	9.22");
        dataList.add("02/22/2024	GSHD	60.57");
        dataList.add("02/23/2024	WMT	58.6967");
        dataList.add("02/26/2024	PHUN	0.175");
        dataList.add("02/27/2024	TWKS	3.2");
        dataList.add("02/28/2024	EB	6.17");
        dataList.add("02/29/2024	CC	18");
        dataList.add("03/01/2024	NYCB	3.45");
        dataList.add("03/04/2024	SABR	2");
        dataList.add("03/05/2024	GTLB	60");
        dataList.add("03/06/2024	THO	105.945");
        dataList.add("03/07/2024	CDMO	6.05");
        dataList.add("03/08/2024	PBR	14.59");
        dataList.add("03/11/2024	EQT	34.9");
        dataList.add("03/12/2024	ACAD	19.49");
        dataList.add("03/13/2024	SOS	1.52");
        dataList.add("03/14/2024	MOMO	5.9");
        dataList.add("03/15/2024	EVCM	6.9");
        dataList.add("03/18/2024	SAIC	118.54");
        dataList.add("03/19/2024	DLO	15.61");
        dataList.add("03/20/2024	SIG	90");
        dataList.add("03/21/2024	ASO	61.86");
        dataList.add("03/22/2024	LULU	416.25");
        dataList.add("03/25/2024	SIGA	8.11");
        dataList.add("03/26/2024	CDLX	15.74");
        dataList.add("03/27/2024	ODFL	219.135");
        dataList.add("03/28/2024	MLKN	23.9");
        dataList.add("04/01/2024	MMM	91.05");
        dataList.add("04/02/2024	GOEV	2.4801");
        dataList.add("04/03/2024	ULTA	469.57");
        dataList.add("04/04/2024	LW	88.53");
        dataList.add("04/05/2024	ATUS	2.38");
        dataList.add("04/08/2024	SUPN	30.58");
        dataList.add("04/09/2024	TLRY	2.12");
        dataList.add("04/10/2024	SOUN	4.24");
        dataList.add("04/11/2024	KMX	73.38");
        dataList.add("04/12/2024	ANET	280.27");
        dataList.add("04/15/2024	PSNY	1.35");
        dataList.add("04/16/2024	PACB	1.95");
        dataList.add("04/17/2024	SAGE	12.96");
        dataList.add("04/18/2024	LAC	4.84");
        dataList.add("04/19/2024	NFLX	567.88");
        dataList.add("04/22/2024	CNHI	11.3");
        dataList.add("04/23/2024	JBLU	6.11");
        dataList.add("04/24/2024	EVR	176.4");
        dataList.add("04/25/2024	META	421.4");
        dataList.add("04/26/2024	SAIA	452.09");
        dataList.add("04/29/2024	DB	16.14");
        dataList.add("04/30/2024	MED	26.6");
        dataList.add("05/01/2024	LEG	12.1");
        dataList.add("05/02/2024	FSLY	8.15");
        dataList.add("05/03/2024	SPT	33.99");
        dataList.add("05/06/2024	BOWL	10.62");
        dataList.add("05/07/2024	JELD	15.2");
        dataList.add("05/08/2024	DV	18.67");
        dataList.add("05/09/2024	FWRD	12.87");
        dataList.add("05/10/2024	MVIS	1.15");

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

    public static void main(String[] args) throws Exception {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.http").setLevel(Level.INFO);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.INFO);

        init();

        List<String> agl = getOptionCode("AGL", 7.75, "2024-01-05");
        getOptionQuote(agl, "2024-01-05", 7.75);
        //        getOptionCode("DADA", 2.14, "2024-01-08");
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
            List<String> optionCallCode = getOptionCode(code, price, date);
            List<String> optionPutCode = optionCallCode.stream().map(c -> getOptionPutCode(c)).collect(Collectors.toList());

            getOptionQuote(optionCallCode, date);
            System.out.println();
            getOptionQuote(optionPutCode, date);

            System.out.println();
        }

        cachedThread.shutdown();
    }
}
