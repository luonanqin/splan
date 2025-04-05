package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Lists;
import util.LoadData;

import java.util.List;
import java.util.Map;

/**
 * 从前20天到前128天之间，找出这段时间里的最高点（当日最高价），然后计算这个高点和当天高点之间差值比例小于15%
 * 例如：002553 2025.3.13
 */
public class Filter1 {

    public static void main(String[] args) {
        LoadData.init();
        cal();
    }

    public static List<String> cal() {
        List<String> res = Lists.newArrayList();
        Map<String, List<StockKLine>> kLineMap = LoadData.kLineMap;

        for (String code : kLineMap.keySet()) {
            if (!code.equals("601212")) {
                //                continue;
            }
            List<StockKLine> stockKLines = kLineMap.get(code);
            if (stockKLines.size() < 100) {
                continue;
            }

            StockKLine latest = stockKLines.get(stockKLines.size() - 1);
            double curClose = latest.getClose();
            if (curClose > 15) {
                continue;
            }

            double spHigh = 0;
            for (int i = 0; i < stockKLines.size(); i++) {
                StockKLine kLine = stockKLines.get(i);
                if (kLine.getDate().equals("2024-10-08")) {
                    spHigh = kLine.getHigh();
                }
            }

            double high = Double.MIN_VALUE;
            for (int i = stockKLines.size() - 21; i >= 0; i--) {
                StockKLine kLine = stockKLines.get(i);
                if (kLine.getDate().equals("2024-11-19")) {
                    break;
                }
                double compareHigh = kLine.getHigh();
                if (high < compareHigh) {
                    high = compareHigh;
                }
            }
            if (high < spHigh) {
                continue;
            }

            double high2 = Double.MIN_VALUE;
            int high2Index = 0;
            for (int i = stockKLines.size() - 1; i >= stockKLines.size() - 20; i--) {
                double compareHigh = stockKLines.get(i).getHigh();
                if (high2 < compareHigh) {
                    high2 = compareHigh;
                    high2Index = stockKLines.size() - i;
                }
            }

            StockKLine _5kLine = stockKLines.get(stockKLines.size() - 6);
            double _5High = _5kLine.getHigh();
            double _5close = _5kLine.getClose();

            double highRatio = Math.abs(high / _5High - 1) * 100;
            double curHighRatio = Math.abs(high2 / _5High - 1) * 100;
            if (highRatio < 10 && _5close < 15 && curHighRatio < 15 && high2Index < 3) {
                System.out.println(code);
                res.add(code);
            }
        }

        return res;
    }
}
