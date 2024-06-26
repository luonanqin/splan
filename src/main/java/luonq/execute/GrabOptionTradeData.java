package luonq.execute;

import bean.OptionChain;
import bean.OptionChainResp;
import bean.Total;
import bean.TradeCalendar;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import luonq.data.ReadFromDB;
import luonq.ivolatility.GetDailyImpliedVolatility;
import luonq.strategy.Strategy33;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import util.BaseUtils;
import util.Constants;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class GrabOptionTradeData {

    @Autowired
    private ReadFromDB readFromDB;

    public Map<String/* date */, Double/* rate */> riskFreeRateMap = Maps.newHashMap();
    public List<String> earningStocks = Lists.newArrayList();
    public Map<String/* stock */, Double/* lastClose */> stockToLastdayCloseMap = Maps.newHashMap();
    public Map<String/* stock */, List<String>/* call and put optioncode */> stockToOptionCodeMap = Maps.newHashMap();
    public Map<String/* stock */, List<String>/* call or put optioncode */> stockToSingleOptionCodeMap = Maps.newHashMap();

    public void grab() {
        try {
            //            loadRiskFreeRate();
            loadWillEarningStock();
            //            loadLastdayClose();
            grabOptionChain();
            grabHistoricalIv();
            grabLastdayOHLC();
        } catch (Exception e) {
            log.error("LoadOptionTradeData load error", e);
        }
    }

    // 加载无风险利率
    public void loadRiskFreeRate() throws Exception {
        riskFreeRateMap = BaseUtils.loadRiskFreeRate();
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
        nextdayStocks.add("AAPL"); // todo 测试用，要删

        Set<String> weekOptionStock = BaseUtils.getWeekOptionStock();
        Collection<String> intersection = CollectionUtils.intersection(weekOptionStock, nextdayStocks);
        earningStocks = intersection.stream().collect(Collectors.toList());

        log.info("will earning stocks: {}", earningStocks);
    }

    // 加载前一天的收盘价
    public void loadLastdayClose() throws Exception {
        if (CollectionUtils.isEmpty(earningStocks)) {
            return;
        }

        LocalDate today = LocalDate.now();
        today = today.minusDays(1); // todo 测试用，要删
        TradeCalendar last = readFromDB.getLastTradeCalendar(today.format(Constants.DB_DATE_FORMATTER));
        String lastDate = last.getDate();
        int year = Integer.parseInt(lastDate.substring(0, 4));
        List<Total> latestData = readFromDB.batchGetStockData(year, lastDate, earningStocks);

        stockToLastdayCloseMap = latestData.stream().map(d -> d.toKLine()).collect(Collectors.toMap(d -> d.getCode(), d -> d.getClose()));

        log.info("load lastday close finish");
    }

    // 抓取股票对应的当周call和put期权链
    public void grabOptionChain() throws Exception {
        if (CollectionUtils.isEmpty(earningStocks)) {
            return;
        }

        String today = LocalDate.now().format(Constants.DB_DATE_FORMATTER);
        LocalDate day = LocalDate.now();
        while (true) {
            String tempDate = day.format(Constants.DB_DATE_FORMATTER);
            TradeCalendar tradeCalendar = readFromDB.getTradeCalendar(tempDate);
            if (tradeCalendar == null) {
                day = day.minusDays(1);
                break;
            }
            day = day.plusDays(1);
        }
        String date = day.format(Constants.DB_DATE_FORMATTER);
        HttpClient httpClient = new HttpClient();
        for (String stock : earningStocks) {
            String url = String.format("https://api.polygon.io/v3/snapshot/options/%s?expiration_date=%s&contract_type=call&order=asc&limit=100&sort=strike_price&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY", stock, date);
            GetMethod getMethod = new GetMethod(url);
            List<String> callAndPut = Lists.newArrayList();
            try {
                httpClient.executeMethod(getMethod);
                InputStream content = getMethod.getResponseBodyAsStream();
                OptionChainResp resp = JSON.parseObject(content, OptionChainResp.class);
                for (OptionChain chain : resp.getResults()) {
                    OptionChain.Detail detail = chain.getDetails();
                    String callCode = detail.getTicker();
                    String putCode = BaseUtils.getOptionPutCode(callCode);
                    callAndPut.add(callCode + "|" + putCode);
                }
                resp.getResults();
            } catch (Exception e) {
                // todo log
            } finally {
                getMethod.releaseConnection();
            }
            stockToOptionCodeMap.put(stock, callAndPut);
            stockToSingleOptionCodeMap.put(stock, callAndPut.stream().flatMap(cp -> Arrays.stream(cp.split("\\|"))).collect(Collectors.toList()));

            String chainDir = Constants.USER_PATH + "optionData/optionChain/" + stock + "/";
            BaseUtils.createDirectory(chainDir);
            BaseUtils.writeFile(chainDir + today, callAndPut);
            log.info("finish grab option chain: {}, size: {}", stock, callAndPut.size());
        }
    }

    // 抓取期权链的历史5天IV
    public void grabHistoricalIv() throws Exception {
        String today = LocalDate.now().format(Constants.DB_DATE_FORMATTER);
        List<TradeCalendar> calendars = readFromDB.getLastNTradeCalendar(today, 5);

        Map<String, List<String>> last5DaysMap = Maps.newHashMap();
        last5DaysMap.put(today, calendars.stream().map(TradeCalendar::getDate).collect(Collectors.toList()));

        Map<String, String> nextDayMap = Maps.newHashMap();
        for (int i = 0; i < calendars.size() - 1; i++) {
            nextDayMap.put(calendars.get(i + 1).getDate(), calendars.get(i).getDate());
        }

        for (String stock : earningStocks) {
            List<String> callAndPut = stockToOptionCodeMap.get(stock);
            if (CollectionUtils.isEmpty(callAndPut)) {
                // todo
                continue;
            }
            List<String> optionCodeList = callAndPut.stream().flatMap(cp -> Arrays.stream(cp.split("\\|"))).collect(Collectors.toList());

            Map<String/* optionCode */, String/* date */> optionCodeDateMap = Maps.newHashMap();
            for (String optionCode : optionCodeList) {
                optionCodeDateMap.put(optionCode, today);
            }
            GetDailyImpliedVolatility.getHistoricalIV(optionCodeDateMap, last5DaysMap, nextDayMap);

            log.info("finish grab historical iv: {}", stock);
        }
    }

    // 抓取期权链前日的OHLC
    public void grabLastdayOHLC() throws Exception {
        String today = LocalDate.now().format(Constants.DB_DATE_FORMATTER);
        TradeCalendar last = readFromDB.getLastTradeCalendar(today);
        String lastDay = last.getDate();

        for (String stock : stockToSingleOptionCodeMap.keySet()) {
            List<String> optionCodeList = stockToSingleOptionCodeMap.get(stock);
            for (String code : optionCodeList) {
                Strategy33.requestOptionDaily(code, lastDay);
            }

            log.info("finish grab lastday OHLC: {}", stock);
        }
    }
}
