package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import util.LoadData;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * 近20天的平均成交量是再前面20天的2倍，且这20天没有连续跌停和连续涨停
 * 例如：
 */
public class Filter13_1 extends BaseFilter {

    public static void main(String[] args) {
        LoadData.init();
        new Filter13_1().cal();
    }

    public List<String> cal(int prevDays, String testCode) {
        List<String> res = Lists.newArrayList();
        Map<String, List<StockKLine>> kLineMap = LoadData.kLineMap;

        for (String code : kLineMap.keySet()) {
            if (StringUtils.isNotBlank(testCode) && !code.equals(testCode)) {
                                                continue;
            }
            List<StockKLine> stockKLines = kLineMap.get(code);
            int temp = fixTemp(stockKLines, prevDays); // 用于测试历史数据做日期调整
            int index = stockKLines.size() - 1 - temp;
            if (index < 0) {
                continue;
            }
            StockKLine latest = stockKLines.get(index);
            double curClose = latest.getClose();
            if (curClose > 10 || curClose < 4) {
                continue;
            }
            if (stockKLines.size() < 128) {
                //                System.out.println("x " + code);
                continue;
            }

            boolean continueBoard = false;
            BigDecimal last20AvgVol = BigDecimal.ZERO;
            double last20AvgClose = 0;
            for (int i = stockKLines.size() - 1 - temp; i > stockKLines.size() - 21 - temp; i--) {
                StockKLine kLine = stockKLines.get(i);
                last20AvgVol = last20AvgVol.add(kLine.getVolume());
                last20AvgClose = last20AvgClose + kLine.getClose();

                StockKLine lastKLine = stockKLines.get(i - 1);
                double ratio = Math.abs((kLine.getClose() / kLine.getLastClose() - 1) * 100);
                double lastRatio = Math.abs((lastKLine.getClose() / lastKLine.getLastClose() - 1) * 100);
                if (ratio > 9.9 && lastRatio > 9.9) {
                    continueBoard = true;
                    break;
                }
            }
            last20AvgVol = last20AvgVol.divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP);

            BigDecimal lastlast20AvgVol = BigDecimal.ZERO;
            double lastlast20AvgClose = 0;
            for (int i = stockKLines.size() - 21 - temp; i > stockKLines.size() - 41 - temp; i--) {
                StockKLine kLine = stockKLines.get(i);
                lastlast20AvgVol = lastlast20AvgVol.add(kLine.getVolume());
                lastlast20AvgClose = lastlast20AvgClose + kLine.getClose();

                StockKLine lastKLine = stockKLines.get(i - 1);
                double ratio = Math.abs((kLine.getClose() / kLine.getLastClose() - 1) * 100);
                double lastRatio = Math.abs((lastKLine.getClose() / lastKLine.getLastClose() - 1) * 100);
                if (ratio > 9.9 && lastRatio > 9.9) {
                    continueBoard = true;
                    break;
                }
            }
            lastlast20AvgVol = lastlast20AvgVol.divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP);

            double avgVolTimes = last20AvgVol.divide(lastlast20AvgVol, 2, RoundingMode.HALF_UP).doubleValue();
            boolean closeCompare = last20AvgClose > lastlast20AvgClose;

            if (avgVolTimes < 3 && avgVolTimes > 2 && !continueBoard && closeCompare) {
                System.out.println(code);
                res.add(code);
            }
        }
        return res;
    }


}
