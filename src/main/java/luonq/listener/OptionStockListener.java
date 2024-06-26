package luonq.listener;

import bean.OptionDaily;
import bean.StockEvent;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.eventbus.Subscribe;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import luonq.execute.LoadOptionTradeData;
import luonq.strategy.Strategy32;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import util.BaseUtils;
import util.Constants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Data
public class OptionStockListener {

    public Map<String/* stock */, String/* call and put*/> canTradeOptionMap = Maps.newHashMap();
    public Map<String/* stock */, String/* call and put*/> canTradeOptionForFutuMap = Maps.newHashMap();
    public Map<String/* stock */, String/* call and put*/> canTradeOptionForRtIVMap = Maps.newHashMap();
    public Set<String> canTradeStocks = Sets.newHashSet();
    public Map<String/* rt iv code */, String/* futu code */> optionCodeMap = Maps.newHashMap();
    public Map<String/* rt iv code */, Double/* strike price */> optionStrikePriceMap = Maps.newHashMap();
    public Map<String/* rt iv code */, String/* expire date */> optionExpireDateMap = Maps.newHashMap();

    @Subscribe
    public void onMessageEvent(StockEvent event) {
        try {
            cal(event.getStock(), event.getPrice());
        } catch (Exception e) {
            e.printStackTrace();
            // todo
        }
    }

    // 计算开盘价波动、计算call和put、计算期权是否可交易
    public void cal(String stock, double open) throws Exception {
        if (!LoadOptionTradeData.earningStocks.contains(stock)) {
            return;
        }
        // 开盘价波动
        Double lastClose = LoadOptionTradeData.stockToLastdayCloseMap.get(stock);
        if (lastClose == null) {
            return;
        }
        double ratio = Math.abs(open - lastClose) / lastClose * 100;
        if (ratio < 3 || open < 5) {
            log.info("ratio is illegal. stock={}\topen={}\tlastClose={}\tratio={}", stock, open, lastClose, ratio);
            return;
        }

        // 开盘价附近的call和put
        List<String> callAndPuts = LoadOptionTradeData.stockToOptionCodeMap.get(stock);
        if (CollectionUtils.isEmpty(callAndPuts)) {
            log.info("there is no call and put for open price. stock={}", stock);
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
        if (StringUtils.isBlank(callOption)) {
            log.info("{} has no option to calculate", stock);
            return;
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
        log.info("{} open={}\tcallOption={}\thigherCall={}\tlowerCall={}", stock, open, callOption, higherCall, lowerCall);

        if (StringUtils.isAnyBlank(higherCall, lowerCall)) {
            log.info("there is no higherCall and lowerCall to trade. stock={}", stock);
            return;
        }
        String call = higherCall;
        String put = BaseUtils.getOptionPutCode(lowerCall);

        List<Double> callIvList = LoadOptionTradeData.optionToIvListMap.get(call);
        List<Double> putIvList = LoadOptionTradeData.optionToIvListMap.get(put);
        if (CollectionUtils.isEmpty(callIvList) || CollectionUtils.isEmpty(putIvList)) {
            log.info("there is no ivlist for stock. call={}\tput={}", call, put);
            return;
        }
        boolean canTradeCall = Strategy32.canTradeForIv(callIvList);
        boolean canTradePut = Strategy32.canTradeForIv(putIvList);
        if (!canTradeCall || !canTradePut) {
            log.warn("{}:{}\t{}:{} can't trade due to ivlist", call, callIvList, put, putIvList);
            return;
        }

        OptionDaily callLastDaily = LoadOptionTradeData.optionToLastDailyMap.get(call);
        OptionDaily putLastDaily = LoadOptionTradeData.optionToLastDailyMap.get(put);
        if (callLastDaily == null || putLastDaily == null || callLastDaily.getVolume() < 100 || putLastDaily.getVolume() < 100) {
            log.warn("{}\t{} last volume is illegal. callLastDaily={}\tputLastDaily={}", call, put, callLastDaily, putLastDaily);
            return;
        }

        log.info("{}\t{}\t{}\t{}\t{}\t{}\t{}\t{} can trade", stock, open, call, callIvList, put, putIvList, callLastDaily, putLastDaily);
        String callCode = call.substring(2);
        String putCode = put.substring(2);
        canTradeOptionMap.put(stock, callCode + "|" + putCode);

        optionStrikePriceMap.put(callCode, BigDecimal.valueOf(Double.valueOf(call.substring(call.length() - 8)) / 1000).setScale(1, RoundingMode.DOWN).doubleValue());
        optionStrikePriceMap.put(putCode, BigDecimal.valueOf(Double.valueOf(put.substring(put.length() - 8)) / 1000).setScale(1, RoundingMode.DOWN).doubleValue());
        optionExpireDateMap.put(callCode, LocalDate.parse(call.substring(call.length() - 15, call.length() - 9), DateTimeFormatter.ofPattern("yyMMdd")).format(Constants.DB_DATE_FORMATTER));
        optionExpireDateMap.put(putCode, LocalDate.parse(put.substring(put.length() - 15, put.length() - 9), DateTimeFormatter.ofPattern("yyMMdd")).format(Constants.DB_DATE_FORMATTER));

        String callSuffix = callCode.substring(callCode.length() - 15);
        String putSuffix = putCode.substring(putCode.length() - 15);
        int plusTimes = 6 - stock.length();
        String plus = "";
        for (int i = 0; i < plusTimes; i++) {
            plus += "+";
        }
        String rtCall = stock + plus + callSuffix;
        String rtPut = stock + plus + putSuffix;
        canTradeOptionForRtIVMap.put(stock, rtCall + "|" + rtPut);

        String callFutuSuffix = callCode.substring(0, callCode.length() - 8);
        String putFutuSuffix = putCode.substring(0, putCode.length() - 8);
        String futuCall = callFutuSuffix + Integer.valueOf(callCode.substring(callCode.length() - 8));
        String futuPut = putFutuSuffix + Integer.valueOf(putCode.substring(putCode.length() - 8));
        canTradeOptionForFutuMap.put(stock, futuCall + "|" + futuPut);

        optionCodeMap.put(rtCall, futuCall);
        optionCodeMap.put(rtPut, futuPut);

        canTradeStocks.add(stock);
    }


}
