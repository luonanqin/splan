package luonq.execute;

import bean.OptionDaily;
import bean.Total;
import bean.TradeCalendar;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import luonq.data.ReadFromDB;
import luonq.strategy.Strategy32;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import util.BaseUtils;
import util.Constants;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class LoadOptionTradeData {

    @Autowired
    private ReadFromDB readFromDB;

    public static Map<String/* date */, Double/* rate */> riskFreeRateMap = Maps.newHashMap();
    public static List<String> earningStocks = Lists.newArrayList();
    public static Map<String/* stock */, Double/* lastClose */> stockToLastdayCloseMap = Maps.newHashMap();
    public static Map<String/* optionCode */, List<Double>/* historical iv */> optionToIvListMap = Maps.newHashMap();
    public Map<String/* stock */, List<String>/* call and put optioncode */> stockToOptionCodeMap = Maps.newHashMap();
    public Map<String/* optionCode */, OptionDaily> optionToLastDailyMap = Maps.newHashMap();

    public void load() {
        try {
            loadRiskFreeRate();
            loadWillEarningStock();
            loadLastdayClose();
            loadOptionChain();
            loadHistoricalIv();
            loadLastdayOHLC();
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

    // 加载股票对应的当周call和put期权链
    public void loadOptionChain() throws Exception {
        if (CollectionUtils.isEmpty(earningStocks)) {
            return;
        }

        String today = LocalDate.now().format(Constants.DB_DATE_FORMATTER);
        for (String stock : earningStocks) {
            List<String> lines = BaseUtils.readFile(Constants.USER_PATH + "optionData/optionChain/" + stock + "/" + today);
            stockToOptionCodeMap.put(stock, lines);
        }
        log.info("finish load option chain");
    }

    // 加载期权链的历史5天IV
    public void loadHistoricalIv() throws Exception {
        if (CollectionUtils.isEmpty(earningStocks)) {
            return;
        }

        String today = LocalDate.now().format(Constants.DB_DATE_FORMATTER);
        String lastday = readFromDB.getLastTradeCalendar(today).getDate();

        for (String stock : earningStocks) {
            Map<String, String> fileMap = BaseUtils.getFileMap(Constants.USER_PATH + "optionData/IV/" + stock);
            for (String optionCode : fileMap.keySet()) {
                String filePath = fileMap.get(optionCode);
                List<String> lines = BaseUtils.readFile(filePath);
                Map<String/* date */, Double> ivMap = Maps.newTreeMap(Comparator.comparing(BaseUtils::formatDateToInt));
                for (String line : lines) {
                    String[] split = line.split("\t");
                    ivMap.put(split[0], Double.valueOf(split[1]));
                }

                List<Double> ivList = Lists.newArrayList();
                for (String date : ivMap.keySet()) {
                    ivList.add(ivMap.get(date));
                    if (StringUtils.equals(date, lastday)) {
                        break;
                    }
                }
                Collections.reverse(ivList);
                if (ivList.size() > 5) {
                    ivList = ivList.subList(0, 5);
                }
                optionToIvListMap.put("O:" + optionCode, ivList);
            }
        }
        log.info("finish load historical iv");
    }

    //  加载期权链前日的OHLC
    public void loadLastdayOHLC() throws Exception {
        if (CollectionUtils.isEmpty(earningStocks)) {
            return;
        }

        String today = LocalDate.now().format(Constants.DB_DATE_FORMATTER);
        TradeCalendar last = readFromDB.getLastTradeCalendar(today);
        String lastDay = last.getDate();

        for (String stock : earningStocks) {
            Map<String, Map<String, OptionDaily>> dateToOptionDailyMap = BaseUtils.loadOptionDailyMap(stock);
            if (dateToOptionDailyMap.containsKey(lastDay)) {
                optionToLastDailyMap.putAll(dateToOptionDailyMap.get(lastDay));
            }
        }
        log.info("finish load option lastday OHLC");
    }

    // 计算开盘价波动、计算call和put、计算期权是否可交易
    public void cal(String stock, double open) throws Exception {
        // 开盘价波动
        Double lastClose = stockToLastdayCloseMap.get(stock);
        if (lastClose == null) {
            return;
        }
        double ratio = Math.abs(open - lastClose) / lastClose * 100;
        if (ratio < 2 || open < 5) {
            return;
        }

        // 开盘价附近的call和put
        List<String> callAndPuts = stockToOptionCodeMap.get(stock);
        if (CollectionUtils.isEmpty(callAndPuts)) {
            return;
        }
        String openPrice = String.valueOf(open);
        int decade = (int) open;
        int count = String.valueOf(decade).length();

        int standardCount = count + 3;
        String priceStr = openPrice.replace(".", "");
        int lastCount = standardCount - priceStr.length();
        int digitalPrice = Integer.valueOf(priceStr) * (int) Math.pow(10, lastCount);

        // 计算开盘价和行权价的差值
        int priceDiff = Integer.MAX_VALUE;
        String callOption = "";
        List<String> callList = Lists.newArrayList();
        for (int i = 0; i < callAndPuts.size(); i++) {
            String callAndPut = callAndPuts.get(i);
            String code = callAndPut.split("\\|")[0];
            int strikePrice = Integer.parseInt(code.substring(code.length() - 8));
            callList.add(code);

            int tempDiff = strikePrice - digitalPrice;
            if (tempDiff > 0 && priceDiff > tempDiff) {
                priceDiff = tempDiff;
                if (i + 1 == callAndPuts.size()) {
                    break;
                }
                callOption = code;
            }
        }
        Collections.sort(callList, (o1, o2) -> {
            int strikePrice1 = Integer.parseInt(o1.substring(o1.length() - 8));
            int strikePrice2 = Integer.parseInt(o2.substring(o2.length() - 8));
            return strikePrice1 - strikePrice2;
        });
        String lowerCall = "", higherCall = "";
        for (int i = 1; i < callList.size() - 1; i++) {
            if (StringUtils.equalsIgnoreCase(callList.get(i), callOption)) {
                lowerCall = callList.get(i - 1);
                higherCall = callList.get(i + 1);
            }
        }

        String call = higherCall;
        String put = BaseUtils.getOptionPutCode(lowerCall);
        if (StringUtils.isAnyBlank(call, put)) {
            // todo
            return;
        }

        List<Double> callIvList = optionToIvListMap.get(call);
        List<Double> putIvList = optionToIvListMap.get(put);
        boolean canTradeCall = Strategy32.canTradeForIv(callIvList);
        boolean canTradePut = Strategy32.canTradeForIv(putIvList);
        if (!canTradeCall || !canTradePut) {
            // todo
            //            return;
        }

        OptionDaily callLastDaily = optionToLastDailyMap.get(call);
        OptionDaily putLastDaily = optionToLastDailyMap.get(put);
        if (callLastDaily == null || putLastDaily == null || callLastDaily.getVolume() < 100 || putLastDaily.getVolume() < 100) {
            // todo
            return;
        }

        log.info("{}\t{}\t{}\t{}\t{}\t{}\t{}\t{} can trade", stock, open, call, callIvList, put, putIvList, callLastDaily, putLastDaily);
    }
}
