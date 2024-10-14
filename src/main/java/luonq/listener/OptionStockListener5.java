package luonq.listener;

import bean.NearlyOptionData;
import bean.OptionDaily;
import bean.StockEvent;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.eventbus.Subscribe;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import luonq.execute.LoadOptionTradeData;
import luonq.polygon.OptionTradeExecutor3;
import luonq.strategy.Strategy37;
import org.apache.commons.collections4.CollectionUtils;
import util.Constants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 末日期权价差策略
 */
@Slf4j
@Data
public class OptionStockListener5 {

    public Map<String/* stock */, String/* call and put*/> canTradeOptionMap = Maps.newHashMap();
    public Map<String/* stock */, String/* call and put*/> canTradeOption2Map = Maps.newHashMap();
    public Map<String/* stock */, String/* call and put*/> canTradeOptionForFutuMap = Maps.newHashMap();
    public Map<String/* stock */, String/* call and put*/> canTradeOptionForFutu2Map = Maps.newHashMap();
    //    public Map<String/* stock */, String/* call and put*/> canTradeOptionForRtIVMap = Maps.newHashMap();
    public Set<String> canTradeStocks = Sets.newHashSet();
    public Map<String/* stock */, Long/* all last option volume */> stockLastOptionVolMap = Maps.newHashMap();
    public Map<String/* rt iv code */, String/* futu code */> optionCodeMap = Maps.newHashMap();
    public Map<String/* polygon code */, String/* futu code */> polygonForFutuMap = Maps.newHashMap();
    public Map<String/* rt iv code */, Double/* strike price */> optionStrikePriceMap = Maps.newHashMap();
    public Map<String/* rt iv code */, String/* expire date */> optionExpireDateMap = Maps.newHashMap();
    //    public Map<String/* rt iv code */, String/* ikbr code */> rtForIkbrMap = Maps.newHashMap();
    public Map<String/* futu code */, String/* ikbr code */> futuForIkbrMap = Maps.newHashMap();
    public Map<Double/* open strike diff */, String/* stock */> stockToOpenStrikeDiffMap = Maps.newTreeMap((o1, o2) -> o1.compareTo(o2));
    public Map<String/* stock */, Integer/* 1=up -1=down */> secStockUpDownMap = Maps.newHashMap();

    private OptionTradeExecutor3 optionTradeExecutor;

    public void setOptionTradeExecutor(OptionTradeExecutor3 optionTradeExecutor) {
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
        //        ReadWriteOptionTradeInfo.addStockOpenPrice(stock, open);
        // 开盘价波动
        Double lastClose = LoadOptionTradeData.stockToLastdayCloseMap.get(stock);
        String date = LoadOptionTradeData.currentTradeDate;
        if (lastClose == null) {
            return;
        }
        double ratio = Math.abs(open - lastClose) / lastClose * 100;
        if (ratio >= 1 || open < 7) {
            log.info("ratio is illegal. stock={}\topen={}\tlastClose={}\tratio={}", stock, open, lastClose, ratio);
            return;
        }

        // 开盘价附近的call和put
        List<String> callAndPuts = LoadOptionTradeData.stockToPrevOptionCodeMap.get(stock);
        if (CollectionUtils.isEmpty(callAndPuts)) {
            log.info("there is no call and put for open price. stock={}", stock);
            return;
        }

        NearlyOptionData nearlyOptionData = Strategy37.calOpenStrikePrice(date, stock, open, callAndPuts);
        if (nearlyOptionData == null) {
            log.info("there is no out option can be traded");
            return;
        }
        String call = nearlyOptionData.getOutPriceCallOptionCode_1();
        String put = nearlyOptionData.getOutPricePutOptionCode_1();
        String call2 = nearlyOptionData.getOutPriceCallOptionCode_2();
        String put2 = nearlyOptionData.getOutPricePutOptionCode_2();
        log.info("{} open={}\tcall={}\tput={}\tcall2={}\tput2={}", stock, open, call, put, call2, put2);

        List<Double> callIvList = LoadOptionTradeData.optionToIvListMap.get(call);
        List<Double> putIvList = LoadOptionTradeData.optionToIvListMap.get(put);
        if (CollectionUtils.isEmpty(callIvList) || CollectionUtils.isEmpty(putIvList)) {
            log.info("there is no ivlist for stock. call={}\tput={}", call, put);
            return;
        }
        boolean canTradeCall = Strategy37.canTradeForIv(callIvList);
        boolean canTradePut = Strategy37.canTradeForIv(putIvList);
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
        //        long totalLastDailyVolume = callLastDaily.getVolume() + putLastDaily.getVolume();
        Double totalLastClose = BigDecimal.valueOf(callLastDaily.getClose() + putLastDaily.getClose()).setScale(2, RoundingMode.HALF_UP).doubleValue();
        if (totalLastClose.compareTo(0.5) <= 0) {
            log.warn("{}\t{} last total close is illegal. callLastDaily={}\tputLastDaily={}", call, put, callLastDaily, putLastDaily);
            return;
        }

        Double outCall1StrikePrice = nearlyOptionData.getOutCall1StrikePrice();
        Double outPut1StrikePrice = nearlyOptionData.getOutPut1StrikePrice();

        Integer upOrDown = open > lastClose ? 1 : -1;
        double diff;
        if (upOrDown > 0) {
            diff = BigDecimal.valueOf(open / outCall1StrikePrice * 100).setScale(4, RoundingMode.HALF_UP).doubleValue();
        } else {
            diff = BigDecimal.valueOf(outPut1StrikePrice / open * 100).setScale(4, RoundingMode.HALF_UP).doubleValue();
        }
        secStockUpDownMap.put(stock, upOrDown);
        stockToOpenStrikeDiffMap.put(diff, stock);

        log.info("{}\t{}\t{}\t{}\t{}\t{}\t{}\t{} can trade", stock, open, call, callIvList, put, putIvList, callLastDaily, putLastDaily);
        String callCode = call.substring(2);
        String putCode = put.substring(2);
        String call2Code = call2.substring(2);
        String put2Code = put2.substring(2);
        canTradeOptionMap.put(stock, call + "|" + put);
        canTradeOption2Map.put(stock, call2 + "|" + put2);

        optionStrikePriceMap.put(callCode, BigDecimal.valueOf(Double.valueOf(call.substring(call.length() - 8)) / 1000).setScale(1, RoundingMode.DOWN).doubleValue());
        optionStrikePriceMap.put(putCode, BigDecimal.valueOf(Double.valueOf(put.substring(put.length() - 8)) / 1000).setScale(1, RoundingMode.DOWN).doubleValue());
        optionExpireDateMap.put(callCode, LocalDate.parse(call.substring(call.length() - 15, call.length() - 9), DateTimeFormatter.ofPattern("yyMMdd")).format(Constants.DB_DATE_FORMATTER));
        optionExpireDateMap.put(putCode, LocalDate.parse(put.substring(put.length() - 15, put.length() - 9), DateTimeFormatter.ofPattern("yyMMdd")).format(Constants.DB_DATE_FORMATTER));

        String callSuffix = callCode.substring(callCode.length() - 15);
        String putSuffix = putCode.substring(putCode.length() - 15);
        String call2Suffix = call2Code.substring(call2Code.length() - 15);
        String put2Suffix = put2Code.substring(put2Code.length() - 15);
        int plusTimes = 6 - stock.length();
        String blank = "";
        for (int i = 0; i < plusTimes; i++) {
            blank += " ";
        }
        String ibkrCall = stock + blank + callSuffix;
        String ibkrPut = stock + blank + putSuffix;
        String ibkrCall2 = stock + blank + call2Suffix;
        String ibkrPut2 = stock + blank + put2Suffix;

        String callFutuSuffix = callCode.substring(0, callCode.length() - 8);
        String putFutuSuffix = putCode.substring(0, putCode.length() - 8);
        String call2FutuSuffix = call2Code.substring(0, call2Code.length() - 8);
        String put2FutuSuffix = put2Code.substring(0, put2Code.length() - 8);
        String futuCall = callFutuSuffix + Integer.valueOf(callCode.substring(callCode.length() - 8));
        String futuPut = putFutuSuffix + Integer.valueOf(putCode.substring(putCode.length() - 8));
        String futuCall2 = call2FutuSuffix + Integer.valueOf(call2Code.substring(call2Code.length() - 8));
        String futuPut2 = put2FutuSuffix + Integer.valueOf(put2Code.substring(put2Code.length() - 8));
        canTradeOptionForFutuMap.put(stock, futuCall + "|" + futuPut);
        //        optionTradeExecutor.monitorFutuDeep(futuCall);
        //        optionTradeExecutor.monitorFutuDeep(futuPut);
        polygonForFutuMap.put(call, futuCall);
        polygonForFutuMap.put(put, futuPut);
        polygonForFutuMap.put(call2, futuCall2);
        polygonForFutuMap.put(put2, futuPut2);
        //        optionTradeExecutor.monitorPolygonDeep(call);
        //        optionTradeExecutor.monitorPolygonDeep(put);
        //        optionTradeExecutor.monitorIV(futuCall);
        //        optionTradeExecutor.monitorIV(futuPut);

        optionCodeMap.put(ibkrCall, futuCall);
        optionCodeMap.put(ibkrPut, futuPut);
        optionCodeMap.put(ibkrCall2, futuCall2);
        optionCodeMap.put(ibkrPut2, futuPut2);
        futuForIkbrMap.put(futuCall, ibkrCall);
        futuForIkbrMap.put(futuPut, ibkrPut);
        futuForIkbrMap.put(futuCall2, ibkrCall2);
        futuForIkbrMap.put(futuPut2, ibkrPut2);

        canTradeStocks.add(stock); // 这里一定要先确定可交易的股票再开始rt的监听
        //        stockLastOptionVolMap.put(stock, totalLastDailyVolume);
        optionTradeExecutor.addBuyOrder(stock);
        optionTradeExecutor.addSellOrder(stock);
        //        canTradeOptionForRtIVMap.put(stock, ibkrCall + "|" + ibkrPut);
    }


}
