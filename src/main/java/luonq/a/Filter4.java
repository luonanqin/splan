package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import util.LoadData;

import java.util.List;
import java.util.Map;

/**
 * 快跌横盘。找出近8天，波动小于15的股票
 * 例如：603949 2025-03-13之前
 */
public class Filter4 extends BaseFilter{

    public static void main(String[] args) {
        LoadData.init();
        cal();
    }

    public static List<String> cal() {
        List<String> res = Lists.newArrayList();
        Map<String, List<StockKLine>> kLineMap = LoadData.kLineMap;

        Map<String, Integer> map = Maps.newHashMap();
        for (String code : kLineMap.keySet()) {
            if (!code.equals("603949")) {
                //                continue;
            }
            List<StockKLine> stockKLines = kLineMap.get(code);
            int temp = fixTemp(stockKLines, 0); // 用于测试历史数据做日期调整
            StockKLine latest = stockKLines.get(stockKLines.size() - 1 - temp);
            if (latest.getClose() > 10) {
                                continue;
            }
            if (stockKLines.size() < temp + 20) {
//                System.out.println("x " + code);
                continue;
            }

            double highest = Double.MIN_VALUE, lowest = Double.MAX_VALUE;
            for (int i = stockKLines.size() - 1 - temp; i >= stockKLines.size() - 8 - temp; i--) {
                StockKLine kLine = stockKLines.get(i);

                double high = kLine.getHigh();
                double low = kLine.getLow();
                if (high > highest) {
                    highest = high;
                }
                if (low < lowest) {
                    lowest = low;
                }
            }
            StockKLine lastDay = stockKLines.get(stockKLines.size() - 9 - temp);
            double lowHighDiff = (highest - lowest) / lastDay.getClose() * 100;

            StockKLine last2Day = stockKLines.get(stockKLines.size() - 10 - temp);
            StockKLine firstDay = stockKLines.get(stockKLines.size() - 8 - temp);
            double lowHigh2Diff = (lastDay.getClose() - lowest) / last2Day.getClose() * 100;

            boolean upTop = false, downTop = false;
            for (int i = stockKLines.size() - 1 - temp; i >= stockKLines.size() - 20 - temp; i--) {
                StockKLine kLine = stockKLines.get(i);

                double close = kLine.getClose();
                double lastClose = kLine.getLastClose();
                double ratio = (close / lastClose - 1) * 100;
                if (ratio > 9.9) {
                    upTop = true;
                } else if (ratio < -9.9) {
                    downTop = true;
                }
            }

            //            boolean greater = lastDay.getClose() > firstDay.getClose();
            boolean greater = true;
            if (lowHighDiff < 15 && greater && upTop && downTop) {
                System.out.println(code);
                res.add(code);
            }
        }
        return res;
    }
}
