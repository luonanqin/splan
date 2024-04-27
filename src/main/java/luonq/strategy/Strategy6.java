package luonq.strategy;

import bean.StockKLine;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import util.BaseUtils;
import util.Constants;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 测试
 * 1.价格大于6
 * 2.成交量大于10w
 * 3.当日有长下影线
 * 4.前日下跌
 * 5.当日下跌
 * 6*当日上涨
 * <p>
 * Created by Luonanqin on 2023/7/18.
 */
public class Strategy6 {

    public static String TEST_STOCK = "";
    public static String TEST_DATE = "";


    public static void main(String[] args) throws Exception {
        String mergePath = Constants.HIS_BASE_PATH + "merge/";
        Map<String, String> dailyFileMap = BaseUtils.getFileMap(mergePath);
        Set<String> stockSet = dailyFileMap.keySet();
        BaseUtils.filterStock(stockSet);

        List<Double> ratioRange = Lists.newArrayList(0.5, 0.6, 0.7, 0.8, 0.9);
        //        for (Double ratio : ratioRange) {
        int successCount = 0, failedCount = 0;

        for (String stock : stockSet) {
            String file = dailyFileMap.get(stock);
            if (StringUtils.isBlank(file)) {
                continue;
            }
            if (StringUtils.isNotBlank(TEST_STOCK) && !stock.equals(TEST_STOCK)) {
                continue;
            }

            List<StockKLine> stockKLines = BaseUtils.loadDataToKline(file, 2022, 0);
            //            String bollPath = Constants.INDICATOR_BOLL_PATH + "daily/" + stock;
            //            List<BOLL> bolls = BaseUtils.readBollFile(bollPath, 2022, 0);
            //            Map<String, BOLL> dateToMaMap = bolls.stream().collect(Collectors.toMap(BOLL::getDate, Function.identity()));

            Collections.reverse(stockKLines);

            System.out.println(stock);
            List<String> dataList = Lists.newLinkedList();
            for (int i = 1; i < stockKLines.size() - 1; i++) {
                StockKLine kLine = stockKLines.get(i);
                String date = kLine.getDate();
                StockKLine nextKLine = stockKLines.get(i + 1);
                StockKLine prevKLine = stockKLines.get(i - 1);

                if (StringUtils.isNotBlank(TEST_DATE) && !date.equals(TEST_DATE)) {
                    continue;
                }

                double high = kLine.getHigh();
                double close = kLine.getClose();
                double open = kLine.getOpen();
                double low = kLine.getLow();
                BigDecimal volumeBig = kLine.getVolume();
                double nextClose = nextKLine.getClose();
                double prevClose = prevKLine.getClose();
                double prevOpen = prevKLine.getOpen();
                double prevLow = prevKLine.getLow();
                BigDecimal prevVolumeBig = prevKLine.getVolume();
                double prevHigh = prevKLine.getHigh();
                double closeLowDiff = close - low;
                double openLowDiff = open - low;
                double highLowDiff = high - low;
                double prevHighLowDiff = prevHigh - prevLow;
                double lossRatio = closeLowDiff / highLowDiff;
                double gainRatio = openLowDiff / highLowDiff;
                double upperShadowRatio = (high - Math.max(close, open)) / highLowDiff;
                double lowerShadowRatio = (Math.min(close, open) - low) / highLowDiff;

                boolean loss = close < open;
                boolean gain = close > open;
                boolean prevLoss = prevClose < prevOpen;
                boolean priceLimit = close > 6;
                boolean closeGtPrevClose = close > prevClose;
                boolean lowGtPrevLow = low > prevLow;
                boolean lowLtPrevLow = low < prevLow;
                double volume = volumeBig.doubleValue();
                double prevVolume = prevVolumeBig.doubleValue();
                boolean activity = volume > 100000;
                boolean highLtPrevOpen = high < prevOpen;
                boolean highLtPrevLow = high < prevLow;
                boolean highLtPrevClose = high < prevClose;

                if (priceLimit && activity) {
                    List<Object> list = Lists.newArrayList(
                      /*0*/date,
                      /*1*/open,
                      /*2*/close,
                      /*3*/high,
                      /*4*/low,
                      /*5 后一天收盘*/nextClose,
                      /*6 前一天开盘*/prevOpen,
                      /*7 前一天收盘*/prevClose,
                      /*8 前一天最低*/prevLow,
                      /*9 收盘和最低的差值*/closeLowDiff,
                      /*10 开盘和最低的差值*/openLowDiff,
                      /*11 最高和最低的差值*/highLowDiff,
                      /*12 上影线在全天的占比*/upperShadowRatio,
                      /*13 下影线在全天的占比*/lowerShadowRatio,
                      /*14 日内亏损*/loss,
                      /*15 日内盈利*/gain,
                      /*16 前一天日内亏损*/prevLoss,
                      /*17 当日收盘高于前日收盘*/closeGtPrevClose,
                      /*18 当日最低高于前日最低*/lowGtPrevLow,
                      /*19 当日最低低于前日最低*/lowLtPrevLow,
                      /*20 当日最高低于前日开盘*/highLtPrevOpen,
                      /*21 当日最高低于前日最低*/highLtPrevLow,
                      /*22 当日最高低于前日收盘*/highLtPrevClose,
                      /*23 当日成交量*/volume,
                      /*24 前日成交量*/prevVolume,
                      /*25 前日最高和最低的差值*/prevHighLowDiff
                    );
                    dataList.add(StringUtils.join(list, ","));
                }
                //                gain = true;
                //                priceLimit = true;
                closeGtPrevClose = true;
                lowGtPrevLow = true;
                //                lowLtPrevLow = true;
                highLtPrevOpen = true;

                //                    boolean gainRatioRes = gain && gainRatio > ratio;
                //                    boolean lossRatioRes = loss && lossRatio > ratio;
                //                    lossRatioRes = false;
                //                    if (priceLimit && activity &&
                //                      (gainRatioRes || lossRatioRes) &&
                //                      lowLtPrevLow && prevLoss && highLtPrevOpen) {
                //                        boolean success = nextClose > close;
                //                        if (success) {
                //                            successCount++;
                //                        } else {
                //                            failedCount++;
                //                        }
                //                        System.out.println(date + " " + success);

                //                    }
            }

            Collections.reverse(dataList);
            BaseUtils.writeFile(Constants.TEST_PATH + "strategy6/" + stock, dataList);
        }
        //            System.out.println("ratio=" + ratio + ", successCount=" + successCount + ", failedCount=" + failedCount + ", rate=" + successCount / (successCount + failedCount));
        //        }
    }
}
