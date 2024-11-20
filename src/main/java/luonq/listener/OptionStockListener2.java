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
import luonq.execute.ReadWriteOptionTradeInfo;
import luonq.polygon.OptionTradeExecutor2;
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
public class OptionStockListener2 {

    public Map<String/* stock */, String/* call and put*/> canTradeOptionMap = Maps.newHashMap();
    public Map<String/* stock */, String/* call and put*/> canTradeOptionForFutuMap = Maps.newHashMap();
    public Map<String/* stock */, String/* call and put*/> canTradeOptionForRtIVMap = Maps.newHashMap();
    public Set<String> canTradeStocks = Sets.newHashSet();
    public Map<String/* stock */, Long/* all last option volume */> stockLastOptionVolMap = Maps.newHashMap();
    public Map<String/* rt iv code */, String/* futu code */> optionCodeMap = Maps.newHashMap();
    public Map<String/* polygon code */, String/* futu code */> polygonForFutuMap = Maps.newHashMap();
    public Map<String/* rt iv code */, Double/* strike price */> optionStrikePriceMap = Maps.newHashMap();
    public Map<String/* rt iv code */, String/* expire date */> optionExpireDateMap = Maps.newHashMap();
    public Map<String/* rt iv code */, String/* ikbr code */> rtForIkbrMap = Maps.newHashMap();
    public Map<String/* futu code */, String/* ikbr code */> futuForIkbrMap = Maps.newHashMap();
    public Map<String/* stock */, Integer/* 1=up -1=down */> openUpDownMap = Maps.newHashMap();
    public Map<String/* stock */, List<String>/* up down option */> upDownOptionMap = Maps.newHashMap();

    private OptionTradeExecutor2 optionTradeExecutor;

    public void setOptionTradeExecutor(OptionTradeExecutor2 optionTradeExecutor) {
        this.optionTradeExecutor = optionTradeExecutor;
    }

    @Subscribe
    public void onMessageEvent(StockEvent event) {
        try {
            cal(event.getStock(), event.getPrice());
        } catch (Exception e) {
            log.error("process message error. event={}", event);
        }
    }

    // 计算开盘价波动、计算call和put、计算期权是否可交易
    public void cal(String stock, double open) throws Exception {
        if (!LoadOptionTradeData.stocks.contains(stock)) {
            return;
        }
        ReadWriteOptionTradeInfo.addStockOpenPrice(stock, open);
        // 开盘价波动
        Double lastClose = LoadOptionTradeData.stockToLastdayCloseMap.get(stock);
        if (lastClose == null) {
            return;
        }
        double ratio = Math.abs(open - lastClose) / lastClose * 100;
        if (ratio < 3 || open < 7) {
            log.info("ratio is illegal. stock={}\topen={}\tlastClose={}\tratio={}", stock, open, lastClose, ratio);
            return;
        }

        // 开盘价附近的call和put
        List<String> callAndPuts = LoadOptionTradeData.stockToOptionCodeMap.get(stock);
        if (CollectionUtils.isEmpty(callAndPuts)) {
            log.info("there is no call and put for open price. stock={}", stock);
            return;
        }
        String openPrice = String.format("%.2f", open);
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

            int tempDiff = Math.abs(strikePrice - digitalPrice);
            if (priceDiff >= tempDiff) {
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
        String lower = "", higher = "";
        for (int i = 1; i < callList.size() - 1; i++) {
            if (StringUtils.equalsIgnoreCase(callList.get(i), callOption)) {
                lower = callList.get(i - 1);
                higher = callList.get(i + 1);
            }
        }
        if (StringUtils.isAnyBlank(higher, lower)) {
            log.info("there is no higher and lower to trade. stock={}", stock);
            return;
        }

        int lowerPrice = Integer.valueOf(lower.substring(lower.length() - 8));
        int higherPrice = Integer.valueOf(higher.substring(higher.length() - 8));

        int strikePrice = Integer.parseInt(callOption.substring(callOption.length() - 8));
        String upStrike = "";
        String downStrike = "";
        if (digitalPrice != strikePrice) {
            if (digitalPrice < strikePrice) {
                upStrike = callOption;
                downStrike = lower;
                double downDiffRatio = (double) (strikePrice - digitalPrice) / (double) (strikePrice - lowerPrice);
                if (downDiffRatio < 0.25) {
                    upStrike = higher;
                }
            } else {
                upStrike = higher;
                downStrike = callOption;
                double downDiffRatio = (double) (digitalPrice - strikePrice) / (double) (higherPrice - strikePrice);
                if (downDiffRatio < 0.25) {
                    downStrike = lower;
                }
            }
        } else if (digitalPrice == strikePrice) {
            upStrike = higher;
            downStrike = lower;
        }
        log.info("{} open={}\tcallOption={}\tupStrike={}\tdownStrike={}", stock, open, callOption, upStrike, downStrike);

        String call = upStrike;
        String put = BaseUtils.getOptionPutCode(downStrike);

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
        long totalLastDailyVolume = callLastDaily.getVolume() + putLastDaily.getVolume();
        Double totalLastClose = BigDecimal.valueOf(callLastDaily.getClose() + putLastDaily.getClose()).setScale(2, RoundingMode.HALF_UP).doubleValue();
        if (totalLastClose.compareTo(0.5) <= 0) {
            log.warn("{}\t{} last total close is illegal. callLastDaily={}\tputLastDaily={}", call, put, callLastDaily, putLastDaily);
            return;
        }

        log.info("{}\t{}\t{}\t{}\t{}\t{}\t{}\t{} can trade", stock, open, call, callIvList, put, putIvList, callLastDaily, putLastDaily);
        String callCode = call.substring(2);
        String putCode = put.substring(2);
        canTradeOptionMap.put(stock, call + "|" + put);

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

        String futuCall = convertToFutu(call);
        String futuPut = convertToFutu(put);
        canTradeOptionForFutuMap.put(stock, futuCall + "|" + futuPut);
        optionTradeExecutor.monitorFutuDeep(futuCall);
        optionTradeExecutor.monitorFutuDeep(futuPut);
        polygonForFutuMap.put(call, futuCall);
        polygonForFutuMap.put(put, futuPut);
        //        optionTradeExecutor.monitorPolygonDeep(call);
        //        optionTradeExecutor.monitorPolygonDeep(put);
        optionTradeExecutor.monitorIV(futuCall);
        optionTradeExecutor.monitorIV(futuPut);

        optionCodeMap.put(rtCall, futuCall);
        optionCodeMap.put(rtPut, futuPut);
        rtForIkbrMap.put(rtCall, rtCall.replaceAll("\\+", " "));
        rtForIkbrMap.put(rtPut, rtPut.replaceAll("\\+", " "));
        futuForIkbrMap.put(futuCall, rtCall.replaceAll("\\+", " "));
        futuForIkbrMap.put(futuPut, rtPut.replaceAll("\\+", " "));

        canTradeStocks.add(stock); // 这里一定要先确定可交易的股票再开始rt的监听
        stockLastOptionVolMap.put(stock, totalLastDailyVolume);
        optionTradeExecutor.addBuyOrder(stock);
        optionTradeExecutor.addSellOrder(stock);
        canTradeOptionForRtIVMap.put(stock, rtCall + "|" + rtPut);

        Integer upOrDown = open > lastClose ? 1 : -1;
        openUpDownMap.put(stock, upOrDown);

        List<String> greekList = monitorUpDownOption(callList, upStrike, downStrike, callAndPuts, call);
        if (CollectionUtils.isNotEmpty(greekList)) {
            upDownOptionMap.put(stock, greekList);
        }
    }

    private List<String> monitorUpDownOption(List<String> callList, String upStrike, String downStrike, List<String> callAndPuts, String call) {
        List<String> greekList = Lists.newArrayList();
        try {
            Map<String, Integer> optionIndexMap = Maps.newHashMap();
            for (int i = 0; i < callList.size(); i++) {
                optionIndexMap.put(callList.get(i), i);
            }
            Integer upIndex = optionIndexMap.get(upStrike);
            Integer downIndex = optionIndexMap.get(downStrike);

            int startIndex = downIndex - 3;
            startIndex = startIndex < 0 ? 0 : startIndex;
            int endIndex = upIndex + 3;
            endIndex = endIndex >= callAndPuts.size() ? callAndPuts.size() - 1 : endIndex;

            for (int i = startIndex; i <= endIndex; i++) {
                String callAndPut = callAndPuts.get(i);
                String code = callAndPut.split("\\|")[0];
                if (StringUtils.equalsAnyIgnoreCase(call, code)) {
                    continue;
                }
                String futuCall = convertToFutu(code);
                String futuPut = convertToFutu(BaseUtils.getOptionPutCode(code));
                optionTradeExecutor.monitorIV(futuCall);
                optionTradeExecutor.monitorIV(futuPut);
                greekList.add(futuCall);
                greekList.add(futuPut);
            }
        } catch (Exception e) {
            log.error("monitorUpDownOption error. callList={}", callList, e);
        }
        return greekList;
    }

    private String convertToFutu(String code) {
        String option = code.substring(2);
        String futuSuffix = option.substring(0, option.length() - 8);
        String futu = futuSuffix + Integer.valueOf(option.substring(option.length() - 8));
        return futu;
    }

}
