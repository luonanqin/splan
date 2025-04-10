package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Lists;
import util.LoadData;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * 近十天的平均成交量是再前面十天的1.5倍，且这20天没有连续跌停和连续涨停
 * 例如： 2025-03-20至2025-03-26
 */
public class Filter13 extends BaseFilter {

    public static void main(String[] args) {
        LoadData.init();
        cal();
    }

    public static List<String> cal() {
        List<String> res = Lists.newArrayList();
        Map<String, List<StockKLine>> kLineMap = LoadData.kLineMap;

        for (String code : kLineMap.keySet()) {
            if (!code.equals("600081")) {
                //                                continue;
            }
            List<StockKLine> stockKLines = kLineMap.get(code);
            int temp = fixTemp(stockKLines, 0); // 用于测试历史数据做日期调整
            int index = stockKLines.size() - 1 - temp;
            if (index < 0) {
                continue;
            }
            StockKLine latest = stockKLines.get(index);
            double curClose = latest.getClose();
            double curLastClose = latest.getLastClose();
            double curRatio = (curClose / curLastClose - 1) * 100;
            if (curClose > 15d) {
                continue;
            }
            if (stockKLines.size() < 128) {
                //                System.out.println("x " + code);
                continue;
            }

            boolean continueBoard = false;
            BigDecimal last10AvgVol = BigDecimal.ZERO;
            double last10AvgClose = 0;
            for (int i = stockKLines.size() - 1 - temp; i > stockKLines.size() - 11 - temp; i--) {
                StockKLine kLine = stockKLines.get(i);
                last10AvgVol = last10AvgVol.add(kLine.getVolume());
                last10AvgClose = last10AvgClose + kLine.getClose();

                StockKLine lastKLine = stockKLines.get(i - 1);
                double ratio = Math.abs((kLine.getClose() / kLine.getLastClose() - 1) * 100);
                double lastRatio = Math.abs((lastKLine.getClose() / lastKLine.getLastClose() - 1) * 100);
                if (ratio > 9.9 && lastRatio > 9.9) {
                    continueBoard = true;
                    break;
                }
            }
            last10AvgVol = last10AvgVol.divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP);

            BigDecimal lastlast10AvgVol = BigDecimal.ZERO;
            double lastlast10AvgClose = 0;
            for (int i = stockKLines.size() - 11 - temp; i > stockKLines.size() - 21 - temp; i--) {
                StockKLine kLine = stockKLines.get(i);
                lastlast10AvgVol = lastlast10AvgVol.add(kLine.getVolume());
                lastlast10AvgClose = lastlast10AvgClose + kLine.getClose();

                StockKLine lastKLine = stockKLines.get(i - 1);
                double ratio = Math.abs((kLine.getClose() / kLine.getLastClose() - 1) * 100);
                double lastRatio = Math.abs((lastKLine.getClose() / lastKLine.getLastClose() - 1) * 100);
                if (ratio > 9.9 && lastRatio > 9.9) {
                    continueBoard = true;
                    break;
                }
            }
            lastlast10AvgVol = lastlast10AvgVol.divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP);

            double avgVolTimes = last10AvgVol.divide(lastlast10AvgVol, 2, RoundingMode.HALF_UP).doubleValue();
            boolean closeCompare = last10AvgClose > lastlast10AvgClose;

            if (avgVolTimes < 2 && avgVolTimes > 1.5 && !continueBoard && closeCompare) {
                System.out.println(code);
                res.add(code);
            }
        }
        return res;
    }


}
