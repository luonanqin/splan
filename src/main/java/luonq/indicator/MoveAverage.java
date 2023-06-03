package luonq.indicator;

import bean.MA;
import bean.StockKLine;
import com.google.common.collect.Lists;
import util.BaseUtils;
import util.Constants;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by Luonanqin on 2023/2/3.
 */
public class MoveAverage {

    public static void main(String[] args) throws Exception {
        calculate("daily");
        calculate("weekly");
        calculate("monthly");
        calculate("quarterly");
        calculate("yearly");
    }

    public static void calculate(String period) throws Exception {
        Map<String, String> stockToKLineMap = BaseUtils.getFileMap(Constants.STD_BASE_PATH + period + "/");
        Map<String, String> hasCalcMap = BaseUtils.getFileMap(Constants.INDICATOR_MA_PATH + period);
        Set<String> hasCalcStock = hasCalcMap.keySet();

        for (String stock : stockToKLineMap.keySet()) {
            if (!stock.equals("AAPL")) {
                //                continue;
            }
            if (hasCalcStock.contains(stock)) {
                continue;
            }
            List<StockKLine> stockKLines = BaseUtils.loadDataToKline(stockToKLineMap.get(stock));

            BigDecimal m5close = BigDecimal.ZERO, m10close = BigDecimal.ZERO, m20close = BigDecimal.ZERO, m30close = BigDecimal.ZERO, m60close = BigDecimal.ZERO;
            int ma5count = 0, ma10count = 0, ma20count = 0, ma30count = 0, ma60count = 0;
            double ma5 = 0, ma10 = 0, ma20 = 0, ma30 = 0, ma60 = 0;
            List<String> maList = Lists.newArrayList();
            for (int i = stockKLines.size() - 1; i >= 0; i--) {
                StockKLine kLine = stockKLines.get(i);
                String date = kLine.getDate();

                BigDecimal close = BigDecimal.valueOf(kLine.getClose());
                m5close = m5close.add(close);
                m10close = m10close.add(close);
                m20close = m20close.add(close);
                m30close = m30close.add(close);
                m60close = m60close.add(close);

                ma5count++;
                ma10count++;
                ma20count++;
                ma30count++;
                ma60count++;

                if (ma5count == 5) {
                    ma5 = m5close.divide(BigDecimal.valueOf(5), 2, BigDecimal.ROUND_HALF_UP).doubleValue();
                    ma5count--;
                    m5close = m5close.subtract(BigDecimal.valueOf(stockKLines.get(i + 5 - 1).getClose()));
                }
                if (ma10count == 10) {
                    ma10 = m10close.divide(BigDecimal.valueOf(10), 2, BigDecimal.ROUND_HALF_UP).doubleValue();
                    ma10count--;
                    m10close = m10close.subtract(BigDecimal.valueOf(stockKLines.get(i + 10 - 1).getClose()));
                }
                if (ma20count == 20) {
                    ma20 = m20close.divide(BigDecimal.valueOf(20), 2, BigDecimal.ROUND_HALF_UP).doubleValue();
                    ma20count--;
                    m20close = m20close.subtract(BigDecimal.valueOf(stockKLines.get(i + 20 - 1).getClose()));
                }
                if (ma30count == 30) {
                    ma30 = m30close.divide(BigDecimal.valueOf(30), 2, BigDecimal.ROUND_HALF_UP).doubleValue();
                    ma30count--;
                    m30close = m30close.subtract(BigDecimal.valueOf(stockKLines.get(i + 30 - 1).getClose()));
                }
                if (ma60count == 60) {
                    ma60 = m60close.divide(BigDecimal.valueOf(60), 2, BigDecimal.ROUND_HALF_UP).doubleValue();
                    ma60count--;
                    m60close = m60close.subtract(BigDecimal.valueOf(stockKLines.get(i + 60 - 1).getClose()));
                }
                MA ma = MA.builder().date(date).ma5(ma5).ma10(ma10).ma20(ma20).ma30(ma30).ma60(ma60).build();
                maList.add(ma.toString());
            }

            BaseUtils.writeFile(Constants.INDICATOR_MA_PATH + period + "/" + stock, Lists.reverse(maList));
            System.out.println("finish " + period + " " + stock);
        }
    }
}
