package luonq.execute;

import bean.OptionChain;
import bean.OptionChainResp;
import bean.Total;
import bean.TradeCalendar;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import luonq.data.ReadFromDB;
import luonq.ivolatility.GetDailyImpliedVolatility;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.jni.Local;
import util.BaseUtils;
import util.Constants;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class OptionTrade {

    private ReadFromDB readFromDB;
    public Map<String/* date */, Double/* rate */> riskFreeRateMap = Maps.newHashMap();
    public List<String> earningStocks = Lists.newArrayList();
    public Map<String/* stock */, Double/* lastClose */> stockToLastdayCloseMap = Maps.newHashMap();
    public Map<String/* stock */, List<String>/* call and put optioncode */> stockToOptionCodeMap = Maps.newHashMap();
    public Map<String/* optionCode */, Map<String/* yyyy-MM-dd */, Double/* iv */>> ivMap = Maps.newHashMap();
    public Map<String/* data */, List<String>/* last5Days */> last5DaysMap = Maps.newHashMap();

    public void init() {
        try {
            loadRiskFreeRate();
            loadWillEarningStock();
            loadLastdayClose();
        } catch (Exception e) {
            // todo log
        }
    }

    // 加载无风险利率
    public void loadRiskFreeRate() throws Exception {
        riskFreeRateMap = BaseUtils.loadRiskFreeRate();
    }

    // 加载接下来三天内发布财报的有周期权的股票
    public void loadWillEarningStock() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate next1day = today.plusDays(1);
        LocalDate next2day = today.plusDays(2);
        LocalDate next3day = today.plusDays(3);
        List<String> next1dayStocks = readFromDB.getStockForEarning(next1day.format(Constants.DB_DATE_FORMATTER));
        List<String> next2dayStocks = readFromDB.getStockForEarning(next2day.format(Constants.DB_DATE_FORMATTER));
        List<String> next3dayStocks = readFromDB.getStockForEarning(next3day.format(Constants.DB_DATE_FORMATTER));

        Set<String> weekOptionStock = BaseUtils.getWeekOptionStock();
        weekOptionStock.removeAll(next1dayStocks);
        weekOptionStock.removeAll(next2dayStocks);
        weekOptionStock.removeAll(next3dayStocks);

        earningStocks = weekOptionStock.stream().collect(Collectors.toList());
    }

    // 加载前一天的收盘价
    public void loadLastdayClose() throws Exception {
        LocalDate now = LocalDate.now();
        LocalDate yesterday = now.minusDays(1);
        List<Total> latestData;
        while (true) {
            int year = yesterday.getYear();
            String date = yesterday.format(Constants.DB_DATE_FORMATTER);
            latestData = readFromDB.batchGetStockData(year, date, earningStocks);
            if (CollectionUtils.isNotEmpty(latestData)) {
                break;
            } else {
                yesterday = yesterday.minusDays(1);
            }
        }

        stockToLastdayCloseMap = latestData.stream().map(d -> d.toKLine()).collect(Collectors.toMap(d -> d.getCode(), d -> d.getClose()));
    }

    // 加载股票对应的当周call和put期权链
    public void loadOptionChain() throws Exception {
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
                    OptionChain.Detail detail = chain.getDetail();
                    String callCode = detail.getTicker();
                    String putCode = BaseUtils.getOptionPutCode(callCode);
                    callAndPut.add(callCode + "|" + putCode);
                }
                resp.getResults()
            } catch (Exception e) {
                // todo log
            } finally {
                getMethod.releaseConnection();
            }
            stockToOptionCodeMap.put(stock, callAndPut);
        }
    }

    // 加载期权链的历史5天IV
    public void loadHistoricalIv() throws Exception {
        Map<String, String> ivDirMap = BaseUtils.getFileMap(Constants.USER_PATH + "optionData/IV/");
        for (String stock : ivDirMap.keySet()) {
            if (StringUtils.equalsAny(stock, "2022", "2023", "2024")) {
                continue;
            }
            String ivDirPath = ivDirMap.get(stock);
            Map<String, String> ivFileMap = BaseUtils.getFileMap(ivDirPath);
            for (String optionCode : ivFileMap.keySet()) {
                List<String> lines = BaseUtils.readFile(ivFileMap.get(optionCode));
                if (!ivMap.containsKey(optionCode)) {
                    ivMap.put(optionCode, Maps.newTreeMap(Comparator.comparing(BaseUtils::formatDateToInt)));
                }
                for (String line : lines) {
                    String[] split = line.split("\t");
                    ivMap.get(optionCode).put(split[0], Double.valueOf(split[1]));
                }
            }
        }

        String today = LocalDate.now().format(Constants.DB_DATE_FORMATTER);
        List<String> last5Days = last5DaysMap.get(today);
        String code = optionCode.substring(2);
        if (!ivMap.containsKey(code)) {
            Map<String/* optionCode */, String/* date */> optionCodeDateMap = Maps.newHashMap();
            optionCodeDateMap.put(optionCode, today);
            GetDailyImpliedVolatility.getHistoricalIV(optionCodeDateMap);
            ivMap.put(code, Maps.newTreeMap(Comparator.comparing(BaseUtils::formatDateToInt)));
        } else {
            Map<String, Double> ivValueMap = ivMap.get(code);
            if (!ivValueMap.containsKey(last5Days.get(0))) {
                Map<String/* optionCode */, String/* date */> optionCodeDateMap = Maps.newHashMap();
                optionCodeDateMap.put(optionCode, today);
                GetDailyImpliedVolatility.getHistoricalIV(optionCodeDateMap);
            }
        }

        int _2_index = code.indexOf("2");
        String stock = code.substring(0, _2_index);
        List<String> lines = BaseUtils.readFile(Constants.USER_PATH + "optionData/IV/" + stock + "/" + code);
        for (String line : lines) {
            String[] split = line.split("\t");
            ivMap.get(code).put(split[0], Double.valueOf(split[1]));
        }

        Map<String, Double> ivValueMap = ivMap.get(code);
        List<Double> ivList = last5Days.stream().filter(d -> ivValueMap.containsKey(d) && ivValueMap.get(d) != -2).map(d -> ivValueMap.get(d)).collect(Collectors.toList());
    }
}
