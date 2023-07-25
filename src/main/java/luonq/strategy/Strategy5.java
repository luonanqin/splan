package luonq.strategy;

import bean.BOLL;
import bean.StockKLine;
import org.apache.commons.lang3.StringUtils;
import util.BaseUtils;
import util.Constants;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 测试
 * 1.价格大于6
 * 2.成交量大于10w
 * 3.最高价低于布林线下轨
 * 4.当前最低低于前日最低
 *
 * Created by Luonanqin on 2023/7/18.
 */
public class Strategy5 {

    public static String TEST_STOCK = "";
    public static String TEST_DATE = "";


    public static void main(String[] args) throws Exception {
        String mergePath = Constants.HIS_BASE_PATH + "merge/";
        Map<String, String> dailyFileMap = BaseUtils.getFileMap(mergePath);
        Set<String> stockSet = dailyFileMap.keySet();
        BaseUtils.filterStock(stockSet);

        for (String stock : stockSet) {
            String file = dailyFileMap.get(stock);
            if (StringUtils.isBlank(file)) {
                continue;
            }
            if (StringUtils.isNotBlank(TEST_STOCK) && !stock.equals(TEST_STOCK)) {
                continue;
            }

            List<StockKLine> stockKLines = BaseUtils.loadDataToKline(file, 2022, 0);
            String bollPath = Constants.INDICATOR_BOLL_PATH + "daily/" + stock;
            List<BOLL> bolls = BaseUtils.readBollFile(bollPath, 2022, 0);
            Map<String, BOLL> dateToMaMap = bolls.stream().collect(Collectors.toMap(BOLL::getDate, Function.identity()));

            Collections.reverse(stockKLines);

            System.out.println(stock);
            for (int i = 1; i < stockKLines.size() - 1; i++) {
                StockKLine kLine = stockKLines.get(i);
                String date = kLine.getDate();
                StockKLine nextKLine = stockKLines.get(i + 1);
                StockKLine prevKLine = stockKLines.get(i - 1);

                if (StringUtils.isNotBlank(TEST_DATE) && !date.equals(TEST_DATE)) {
                    continue;
                }

                BOLL boll = dateToMaMap.get(date);
                if (boll == null) {
                    continue;
                }

                double dn = boll.getDn();
                double high = kLine.getHigh();
                double close = kLine.getClose();
                double open = kLine.getOpen();
                double low = kLine.getLow();
                BigDecimal volume = kLine.getVolume();
                double nextClose = nextKLine.getClose();
                double prevClose = prevKLine.getClose();
                double prevLow = prevKLine.getLow();

                boolean gain = close > open;
                boolean priceLimit = close > 6;
                boolean closeGtPrevClose = close > prevClose;
                boolean lowGtPrevLow = low > prevLow;
                boolean lowLtPrevLow = low < prevLow;
                boolean activity = volume.doubleValue() > 100000;

                gain = true;
                //                priceLimit = true;
                closeGtPrevClose = true;
                lowGtPrevLow = true;
                //                lowLtPrevLow = true;

                if (priceLimit && high < dn && gain && closeGtPrevClose && lowGtPrevLow && lowLtPrevLow && activity) {
                    boolean success = nextClose > close;
                    System.out.println(date + " " + success);
                }
            }

        }
    }
}
