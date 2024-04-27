package luonq.indicator;

import bean.BOLL;
import bean.StockKLine;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import util.BaseUtils;
import util.Constants;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.math.BigDecimal.ROUND_DOWN;
import static java.math.BigDecimal.ROUND_HALF_UP;

/**
 * Created by Luonanqin on 2023/2/3.
 */
@Slf4j
public class Bollinger {

    public static void main(String[] args) throws Exception {
        calculate("daily");
        //        calculate("weekly");
        //        calculate("monthly");
        //        calculate("quarterly");
        //        calculate("yearly");
    }

    public static void calculate(String period) throws Exception {
        String mergePath = Constants.HIS_BASE_PATH + "merge/";
        Map<String, String> stockToKLineMap = BaseUtils.getFileMap(mergePath);
        Map<String, String> hasCalcMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "mergeBoll");
        Set<String> hasCalcStock = hasCalcMap.keySet();

        for (String stock : stockToKLineMap.keySet()) {
            if (!stock.equals("WINV")) {
//                continue;
            }
            if (hasCalcStock.contains(stock)) {
                                continue;
            }
            List<StockKLine> stockKLines = BaseUtils.loadDataToKline(stockToKLineMap.get(stock), 2023);

            BigDecimal m20close = BigDecimal.ZERO;
            int ma20count = 0;
            double md = 0, mb = 0, up = 0, dn = 0;
            List<String> maList = Lists.newArrayList();
            for (int i = stockKLines.size() - 1; i >= 0; i--) {
                StockKLine kLine = stockKLines.get(i);
                String date = kLine.getDate();

                BigDecimal close = BigDecimal.valueOf(kLine.getClose());
                m20close = m20close.add(close);
                ma20count++;

                if (ma20count == 20) {
                    double ma20 = m20close.divide(BigDecimal.valueOf(20), 2, ROUND_HALF_UP).doubleValue();
                    mb = ma20;
                    BigDecimal avgDiffSum = BigDecimal.ZERO;
                    int j = i, times = 20;
                    while (times > 0) {
                        double c = stockKLines.get(j++).getClose();
                        avgDiffSum = avgDiffSum.add(BigDecimal.valueOf(c - ma20).pow(2));
                        times--;
                    }

                    md = Math.sqrt(avgDiffSum.doubleValue() / 20);
                    BigDecimal mdPow2 = BigDecimal.valueOf(md).multiply(BigDecimal.valueOf(2));
                    up = BigDecimal.valueOf(mb).add(mdPow2).setScale(3, ROUND_DOWN).doubleValue();
                    dn = BigDecimal.valueOf(mb).subtract(mdPow2).setScale(3, ROUND_DOWN).doubleValue();

                    ma20count--;
                    m20close = m20close.subtract(BigDecimal.valueOf(stockKLines.get(i + 20 - 1).getClose()));
                }
                BOLL boll = BOLL.builder().date(date).md(md).mb(mb).up(up).dn(dn).build();
                maList.add(boll.toString());
            }

            BaseUtils.writeFile(Constants.HIS_BASE_PATH + "mergeBoll/" + stock, Lists.reverse(maList));
            log.info("finish " + period + " " + stock);
        }
    }
}
