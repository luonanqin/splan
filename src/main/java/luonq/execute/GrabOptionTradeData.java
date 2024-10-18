package luonq.execute;

import bean.OptionChain;
import bean.OptionChainResp;
import bean.OptionDaily;
import bean.Total;
import bean.TradeCalendar;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import luonq.data.ReadFromDB;
import luonq.ivolatility.GetDailyImpliedVolatility;
import luonq.strategy.Strategy32;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import util.BaseUtils;
import util.Constants;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static util.Constants.USER_PATH;

@Slf4j
@Component
public class GrabOptionTradeData {

    @Autowired
    private ReadFromDB readFromDB;

    public List<String> stocks = Lists.newArrayList();
    public Map<String/* stock */, List<String>/* call and put optioncode */> stockToOptionCodeMap = Maps.newHashMap();
    public Map<String/* stock */, List<String>/* call or put optioncode */> stockToSingleOptionCodeMap = Maps.newHashMap();
    public static Map<String/* stock */, Double/* lastClose */> stockToLastdayCloseMap = Maps.newHashMap();
    public String lastTradeDate;
    public String last2TradeDate;
    public static String currentTradeDate;
    public BlockingQueue<CloseableHttpClient> queue;
    public ThreadPoolExecutor threadPool;

    public Set<String> testStocks = Sets.newHashSet();

    public void init() {
        int threadCount = 100;
        int corePoolSize = threadCount;
        int maximumPoolSize = corePoolSize;
        long keepAliveTime = 60L;
        LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
        threadPool = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS, workQueue);
        queue = new LinkedBlockingQueue<>(threadCount);
        for (int i = 0; i < threadCount; i++) {
            queue.offer(HttpClients.createDefault());
        }
    }

    public void grab() {
        try {
            init();
            calLastTradeDate();
            calCurrentTradeDate();
            //            loadWillEarningStock();
            loadPennyStock();
            loadLastdayClose();
            grabOptionChain();
            grabLastdayOHLC();
            grabPrevLastdayOHLC();
            grabOptionId();
            grabHistoricalIv();
        } catch (Exception e) {
            log.error("LoadOptionTradeData load error", e);
        }
    }

    public void calLastTradeDate() {
        LocalDateTime now = LocalDateTime.now();
        String today = LocalDate.now().format(Constants.DB_DATE_FORMATTER);
        TradeCalendar tradeCalendar = readFromDB.getTradeCalendar(today);
        TradeCalendar lastTradeCalendar = readFromDB.getLastTradeCalendar(today);
        TradeCalendar last2TradeCalendar = readFromDB.getLastTradeCalendar(lastTradeCalendar.getDate());
        LocalDateTime preMarketOpen = LocalDateTime.of(LocalDate.now(), LocalTime.of(13, 0, 0)); // 前一交易日数据的入库时间

        if (tradeCalendar == null) {
            lastTradeDate = last2TradeCalendar.getDate();
        } else {
            if (now.isBefore(preMarketOpen)) {
                lastTradeDate = last2TradeCalendar.getDate();
            } else if (now.isAfter(preMarketOpen)) {
                lastTradeDate = lastTradeCalendar.getDate();
            }
        }

        TradeCalendar last3TradeCalendar = readFromDB.getLastTradeCalendar(last2TradeCalendar.getDate());
        if (tradeCalendar == null) {
            last2TradeDate = last3TradeCalendar.getDate();
        } else {
            if (now.isBefore(preMarketOpen)) {
                last2TradeDate = last3TradeCalendar.getDate();
            } else if (now.isAfter(preMarketOpen)) {
                last2TradeDate = last2TradeCalendar.getDate();
            }
        }
    }

    public void calCurrentTradeDate() {
        TradeCalendar nextTradeCalendar = readFromDB.getNextTradeCalendar(lastTradeDate);
        currentTradeDate = nextTradeCalendar.getDate();
    }

    public void loadPennyStock() {
        stocks = BaseUtils.getPennyOptionStock().stream().collect(Collectors.toList());
        log.info("penny stocks: {}", stocks);
    }

    // 加载接下来三天内发布财报的有周期权的股票
    public void loadWillEarningStock() throws Exception {
        LocalDate today = LocalDate.now();
        String next1day = "";
        String next2day = "";
        String next3day = "";
        LocalDate nextDay = today.plusDays(1);
        for (int i = 0; i < 3; i++) {
            while (true) {
                String nextDate = nextDay.format(Constants.DB_DATE_FORMATTER);
                TradeCalendar tradeCalendar = readFromDB.getTradeCalendar(nextDate);
                if (tradeCalendar != null) {
                    if (i == 0) {
                        next1day = nextDate;
                    } else if (i == 1) {
                        next2day = nextDate;
                    } else if (i == 2) {
                        next3day = nextDate;
                    }
                    nextDay = nextDay.plusDays(1);
                    break;
                } else {
                    nextDay = nextDay.plusDays(1);
                }
            }
        }
        List<String> next1dayStocks = readFromDB.getStockForEarning(next1day);
        List<String> next2dayStocks = readFromDB.getStockForEarning(next2day);
        List<String> next3dayStocks = readFromDB.getStockForEarning(next3day);
        Set<String> nextdayStocks = Sets.newHashSet();
        next1dayStocks.forEach(s -> nextdayStocks.add(s));
        next2dayStocks.forEach(s -> nextdayStocks.add(s));
        next3dayStocks.forEach(s -> nextdayStocks.add(s));
        //        nextdayStocks.clear(); // todo 测试用，要删
        //        nextdayStocks.add("AAPL"); // todo 测试用，要删

        Set<String> weekOptionStock = BaseUtils.getWeekOptionStock();
        Collection<String> intersection = CollectionUtils.intersection(weekOptionStock, nextdayStocks);
        stocks = intersection.stream().collect(Collectors.toList());

        // todo 测试非财报日使用
        stocks.clear();
        stocks = BaseUtils.getPennyOptionStock().stream().collect(Collectors.toList());

        log.info("will earning stocks: {}", stocks);
    }

    // 加载前一天的收盘价
    public void loadLastdayClose() throws Exception {
        if (CollectionUtils.isEmpty(stocks)) {
            return;
        }

        //        LocalDate today = LocalDate.now();

        //        today = today.minusDays(1); // todo 测试用，要删
        //        TradeCalendar last = readFromDB.getLastTradeCalendar(today.format(Constants.DB_DATE_FORMATTER));
        //        String lastDate = last.getDate();
        int year = Integer.parseInt(lastTradeDate.substring(0, 4));
        List<Total> latestData = readFromDB.batchGetStockData(year, lastTradeDate, stocks);

        stockToLastdayCloseMap = latestData.stream().map(d -> d.toKLine()).collect(Collectors.toMap(d -> d.getCode(), d -> d.getClose()));

        log.info("load lastday close finish");
    }


    // 抓取股票对应的当周call和put期权链，如果是周五就抓取下周的期权链
    public void grabOptionChain() throws Exception {
        if (CollectionUtils.isEmpty(stocks)) {
            return;
        }

        LocalDate day = LocalDate.now();

        // 如果当前时间小于当天收盘时间，则当天交易日要减一天，避免0点过后抓取时间错误
        LocalDateTime marketClose = LocalDateTime.of(LocalDate.now(), LocalTime.of(5, 0, 0)); // 当天交易收盘时间
        if (LocalDateTime.now().isBefore(marketClose)) {
            day = day.minusDays(1);
        }

        String today = day.format(Constants.DB_DATE_FORMATTER);
        TradeCalendar nextTradeCalendar = readFromDB.getNextTradeCalendar(today);
        LocalDate nextDay = LocalDate.parse(nextTradeCalendar.getDate(), Constants.DB_DATE_FORMATTER);
        String date = day.format(Constants.DB_DATE_FORMATTER);
        /**
         * 如果当天在当周的次序大于下一交易日在下载的次序，则当天可能是最后一个交易日或周末，需要抓取下周的期权链。比如：
         * 1.今天周四，下一交易日是周五，则期权链抓本周的
         * 2.今天周四，下一交易日是下周一，则期权链抓下周的
         * 3.今天周六，下一交易日是下周一，则期权链抓下周的
         * 4.今天周五，下一交易日是下周一，则期权链抓下周的
         * 5.今天周一，下一交易日是周二，则期权链抓本周的
         */
        if (day.getDayOfWeek().getValue() > nextDay.getDayOfWeek().getValue()) {
            day = nextDay;
        }

        while (true) {
            String tempDate = day.format(Constants.DB_DATE_FORMATTER);
            TradeCalendar tradeCalendar = readFromDB.getTradeCalendar(tempDate);
            if (tradeCalendar != null) {
                date = tempDate;
            }
            day = day.plusDays(1);
            if (day.getDayOfWeek().getValue() == 7) {
                break;
            }
        }
        CountDownLatch cdl = new CountDownLatch(stocks.size());
        String expirationDate = date;
        for (String stock : stocks) {
            if (CollectionUtils.isNotEmpty(testStocks) && !testStocks.contains(stock)) {
                cdl.countDown();
                continue;
            }

            //            Double lastClose = stockToLastdayCloseMap.get(stock);
            //            if (lastClose == null) {
            //                cdl.countDown();
            //                continue;
            //            }

            CloseableHttpClient httpClient = queue.take();
            threadPool.execute(() -> {
                String url = String.format("https://api.polygon.io/v3/snapshot/options/%s?expiration_date=%s&contract_type=call&order=asc&limit=100&sort=strike_price&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY", stock, expirationDate);
                HttpGet getMethod = new HttpGet(url);
                List<String> callAndPut = Lists.newArrayList();
                try {
                    while (true) {
                        CloseableHttpResponse execute = httpClient.execute(getMethod);
                        InputStream content = execute.getEntity().getContent();
                        OptionChainResp resp = JSON.parseObject(content, OptionChainResp.class);
                        for (OptionChain chain : resp.getResults()) {
                            OptionChain.Detail detail = chain.getDetails();
                            //                            double strike_price = detail.getStrike_price();
                            //                            double ratio = Math.abs(strike_price - lastClose) / lastClose;
                            //                            if (ratio > 0.1) {
                            //                            continue;
                            //                            }
                            String callCode = detail.getTicker();
                            String putCode = BaseUtils.getOptionPutCode(callCode);
                            callAndPut.add(callCode + "|" + putCode);
                        }
                        String nextUrl = resp.getNext_url();
                        if (StringUtils.isBlank(nextUrl)) {
                            break;
                        } else {
                            getMethod.releaseConnection();
                            getMethod = new HttpGet(nextUrl + "&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY");
                        }
                    }
                    stockToOptionCodeMap.put(stock, callAndPut);
                    stockToSingleOptionCodeMap.put(stock, callAndPut.stream().flatMap(cp -> Arrays.stream(cp.split("\\|"))).collect(Collectors.toList()));

                    String chainDir = Constants.USER_PATH + "optionData/optionChain/" + stock + "/";
                    BaseUtils.createDirectory(chainDir);
                    BaseUtils.writeFile(chainDir + today, callAndPut);
                    log.info("finish grab option chain: {}, size: {}", stock, callAndPut.size());
                } catch (Exception e) {
                    log.error("grabOptionChain error. url={}", url, e);
                } finally {
                    getMethod.releaseConnection();
                    queue.offer(httpClient);
                    cdl.countDown();
                    //                    System.out.println("option chain cdl:" + cdl.getCount());
                }
            });
        }
        cdl.await();
        log.info("finish grab option chain");
    }

    // 抓取期权链对应的optionid
    public void grabOptionId() throws Exception {
        Map<String, String> optionIdMap = GetDailyImpliedVolatility.getOptionIdMap();
        for (String stock : stocks) {
            if (CollectionUtils.isNotEmpty(testStocks) && !testStocks.contains(stock)) {
                continue;
            }
            if (!stockToOptionCodeMap.containsKey(stock)) {
                continue;
            }
            List<String> callAndPuts = stockToOptionCodeMap.get(stock);
            if (CollectionUtils.isEmpty(callAndPuts)) {
                continue;
            }

            String expirationDate = "";
            int max = Integer.MIN_VALUE, min = Integer.MAX_VALUE;
            boolean needGrab = false;
            for (String callAndPut : callAndPuts) {
                String call = callAndPut.split("\\|")[0];
                String put = callAndPut.split("\\|")[1];
                if (optionIdMap.containsKey(call) && optionIdMap.containsKey(put)) {
                    continue;
                }
                String date = call.substring(call.length() - 15, call.length() - 9);
                Integer strikePrice = Integer.valueOf(call.substring(call.length() - 8));
                expirationDate = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyMMdd")).format(Constants.DB_DATE_FORMATTER);
                if (strikePrice > max) {
                    max = strikePrice;
                }
                if (strikePrice < min) {
                    min = strikePrice;
                }
                needGrab = true;
            }
            if (!needGrab) {
                log.info("has grab {} option id", stock);
                continue;
            }
            String strikePriceFrom = BigDecimal.valueOf((double) min / 1000d).setScale(1, RoundingMode.HALF_DOWN).toString();
            String strikePriceTo = BigDecimal.valueOf((double) max / 1000d).setScale(1, RoundingMode.HALF_UP).toString();

            GetDailyImpliedVolatility.getOptionId(stock, expirationDate, strikePriceFrom, strikePriceTo, lastTradeDate);
            log.info("finish grab option Id: {}", stock);
        }
        log.info("finish grab option Id");
    }

    // 抓取期权链的历史5天IV
    public void grabHistoricalIv() throws Exception {
        List<TradeCalendar> calendars = readFromDB.getLastNTradeCalendar(currentTradeDate, 5);

        Map<String, List<String>> last5DaysMap = Maps.newHashMap();
        last5DaysMap.put(currentTradeDate, calendars.stream().map(TradeCalendar::getDate).collect(Collectors.toList()));

        Map<String, String> nextDayMap = Maps.newHashMap();
        for (int i = 0; i < calendars.size() - 1; i++) {
            nextDayMap.put(calendars.get(i + 1).getDate(), calendars.get(i).getDate());
        }

        Strategy32.clearOptionDailyCache();
        Set<String> hasGet = Sets.newHashSet();
        for (String stock : stocks) {
            if (CollectionUtils.isNotEmpty(testStocks) && !testStocks.contains(stock)) {
                continue;
            }
            if (hasGet.contains(stock)) {
                continue;
            }

            List<String> callAndPut = stockToOptionCodeMap.get(stock);
            if (CollectionUtils.isEmpty(callAndPut)) {
                log.error("there is no option code to grab historical iv. stock={}", stock);
                continue;
            }
            List<String> optionCodeList = callAndPut.stream().flatMap(cp -> Arrays.stream(cp.split("\\|"))).collect(Collectors.toList());

            Map<String/* optionCode */, String/* date */> optionCodeDateMap = Maps.newHashMap();
            for (String optionCode : optionCodeList) {
                OptionDaily optionDaily = Strategy32.getOptionDaily(optionCode, lastTradeDate);
                if (optionDaily == null || optionDaily.getVolume() < 100) {
                    continue;
                }

                // 如果抓取的数据包含要抓取的交易日，并且iv!=-2，则不用再抓
                boolean hasGrab = false;
                List<String> lines = BaseUtils.readFile(Constants.USER_PATH + "optionData/IV/" + stock + "/" + optionCode.substring(2));
                for (String line : lines) {
                    if (line.contains(lastTradeDate) && !line.contains("-2")) {
                        hasGrab = true;
                    }
                }
                if (hasGrab) {
                    continue;
                }

                optionCodeDateMap.put(optionCode, currentTradeDate);
            }
            if (MapUtils.isNotEmpty(optionCodeDateMap)) {
                //                for (String s : optionCodeDateMap.keySet()) {
                //                    System.out.println("grab iv:" + s);
                //                }
                GetDailyImpliedVolatility.getHistoricalIV(optionCodeDateMap, last5DaysMap, nextDayMap, lastTradeDate);
            }

            log.info("finish grab historical iv: {}", stock);
        }
        log.info("finish grab historical iv");
    }

    // 抓取期权链前日的OHLC
    public void grabLastdayOHLC() throws Exception {
        CountDownLatch cdl = new CountDownLatch(stockToSingleOptionCodeMap.size());
        for (String stock : stockToSingleOptionCodeMap.keySet()) {
            if (CollectionUtils.isNotEmpty(testStocks) && !testStocks.contains(stock)) {
                cdl.countDown();
                continue;
            }

            List<String> optionCodeList = stockToSingleOptionCodeMap.get(stock);

            CloseableHttpClient httpClient = queue.take();
            threadPool.execute(() -> {
                try {
                    for (String code : optionCodeList) {
                        if (Strategy32.getOptionDaily(code, lastTradeDate) != null) {
                            continue;
                        }
                        //                        Strategy33.requestOptionDaily(code, lastTradeDate);
                        OptionDaily optionDaily = Strategy32.requestOptionDailyList(httpClient, lastTradeDate, code);
                        Strategy32.writeOptionDaily(optionDaily, code, lastTradeDate);
                    }
                    log.info("finish grab lastday OHLC: {}", stock);
                } catch (Exception e) {
                    log.info("grabLastdayOHLC error. stock={}", stock, e);
                } finally {
                    queue.offer(httpClient);
                    cdl.countDown();
                    System.out.println("lastday OHLC cdl:" + cdl.getCount());
                }
            });
        }
        cdl.await();
        log.info("finish grab lastday OHLC");
    }

    // 抓取前日期权链前日的OHLC
    public void grabPrevLastdayOHLC() throws Exception {
        CountDownLatch cdl = new CountDownLatch(stockToSingleOptionCodeMap.size());
        for (String stock : stockToSingleOptionCodeMap.keySet()) {

            if (CollectionUtils.isNotEmpty(testStocks) && !testStocks.contains(stock)) {
                cdl.countDown();
                continue;
            }

            String chainDir = USER_PATH + "optionData/optionChain/" + stock + "/";
            String filePath = chainDir + last2TradeDate;
            List<String> callAndPuts = BaseUtils.readFile(filePath);

            List<String> optionCodeList = callAndPuts.stream().flatMap(s -> Arrays.stream(s.split("\\|"))).collect(Collectors.toList());

            CloseableHttpClient httpClient = queue.take();
            threadPool.execute(() -> {
                try {
                    for (String code : optionCodeList) {
                        if (Strategy32.getOptionDaily(code, lastTradeDate) != null) {
                            continue;
                        }
                        //                        Strategy33.requestOptionDaily(code, lastTradeDate);
                        OptionDaily optionDaily = Strategy32.requestOptionDailyList(httpClient, lastTradeDate, code);
                        Strategy32.writeOptionDaily(optionDaily, code, lastTradeDate);
                    }
                    log.info("finish grab lastday OHLC: {}", stock);
                } catch (Exception e) {
                    log.info("grabLastdayOHLC error. stock={}", stock, e);
                } finally {
                    queue.offer(httpClient);
                    cdl.countDown();
                    System.out.println("lastday OHLC cdl:" + cdl.getCount());
                }
            });
        }
        cdl.await();
        log.info("finish grab lastday OHLC");
    }
}
