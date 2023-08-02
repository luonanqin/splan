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
                BigDecimal volume = kLine.getVolume();
                double nextClose = nextKLine.getClose();
                double prevClose = prevKLine.getClose();
                double prevOpen = prevKLine.getOpen();
                double prevLow = prevKLine.getLow();
                double closeLowDiff = close - low;
                double openLowDiff = open - low;
                double highLowDiff = high - low;
                double lossRatio = closeLowDiff / highLowDiff;
                double gainRatio = openLowDiff / highLowDiff;

                boolean loss = close < open;
                boolean gain = close > open;
                boolean prevLoss = prevClose < prevOpen;
                boolean priceLimit = close > 6;
                boolean closeGtPrevClose = close > prevClose;
                boolean lowGtPrevLow = low > prevLow;
                boolean lowLtPrevLow = low < prevLow;
                boolean activity = volume.doubleValue() > 100000;
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
                      /*5*/nextClose,
                      /*6*/prevOpen,
                      /*7*/prevClose,
                      /*8*/prevLow,
                      /*9*/closeLowDiff,
                      /*10*/openLowDiff,
                      /*11*/highLowDiff,
                      /*12*/lossRatio,
                      /*13*/gainRatio,
                      /*14*/loss,
                      /*15*/gain,
                      /*16*/prevLoss,
                      /*17*/closeGtPrevClose,
                      /*18*/lowGtPrevLow,
                      /*19*/lowLtPrevLow,
                      /*20*/highLtPrevOpen,
                      /*21*/highLtPrevLow,
                      /*22*/highLtPrevClose
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
