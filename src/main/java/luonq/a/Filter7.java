package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import util.LoadData;

import java.util.List;
import java.util.Map;

/**
 * 寻找启动后横盘但是已接近前高的，即将拉升
 * 横盘超过10天，但是最高收盘和最低开盘相差不超过10%，且收盘均价超过前面10天的收盘均价。横盘价离前高不远，且前高没有明显的放量出货走势
 * 例如：002132 2025-03-04之后
 */
public class Filter7 extends BaseFilter {

    public static void main(String[] args) {
        cal();
    }

    public static List<String> cal() {
        LoadData.init();

        List<String> res = Lists.newArrayList();
        Map<String, List<StockKLine>> kLineMap = LoadData.kLineMap;

        Map<String, Integer> map = Maps.newHashMap();
        for (String code : kLineMap.keySet()) {
            if (!code.equals("002132")) {
//                continue;
            }
            List<StockKLine> stockKLines = kLineMap.get(code);
            int temp = 0;

            if (stockKLines.size() < temp + 22) {
                //                System.out.println("x " + code);
                continue;
            }
            StockKLine latest = stockKLines.get(stockKLines.size() - 1 - temp);
            if (latest.getClose() > 15d) {
                continue;
            }

            //            Map<String/* date */, BigDecimal> avgVolMap = cal50volMa(stockKLines);

            double sumClose = 0;
            double highest = Double.MIN_VALUE, lowest = Double.MAX_VALUE;
            for (int i = stockKLines.size() - 1 - temp; i >= stockKLines.size() - 11 - temp; i--) {
                StockKLine kLine = stockKLines.get(i);

                double close = kLine.getClose();
                double open = kLine.getOpen();
                if (close > highest) {
                    highest = close;
                }
                if (open > highest) {
                    highest = open;
                }
                if (open < lowest) {
                    lowest = open;
                }
                if (close < lowest) {
                    lowest = close;
                }
                sumClose += close;
            }
            double avgClose = sumClose / 10;

            double prevSumClose = 0;
            double prevHighest = Double.MIN_VALUE, prevLowest = Double.MAX_VALUE;
            for (int i = stockKLines.size() - 12 - temp; i >= stockKLines.size() - 22 - temp; i--) {
                StockKLine kLine = stockKLines.get(i);

                double close = kLine.getClose();
                double open = kLine.getOpen();
                if (close > prevHighest) {
                    prevHighest = close;
                }
                if (open > prevHighest) {
                    prevHighest = open;
                }
                if (open < prevLowest) {
                    prevLowest = open;
                }
                if (close < prevLowest) {
                    prevLowest = close;
                }
                prevSumClose += close;
            }
            double prevAvgClose = prevSumClose / 10;

            double hisHighest = Double.MIN_VALUE;
            for (int i = stockKLines.size() - 1 - temp; i >= 0; i--) {
                StockKLine kLine = stockKLines.get(i);
                if (kLine.getDate().equals("2024-09-24")) {
                    break;
                }

                double high = kLine.getHigh();
                if (hisHighest < high) {
                    hisHighest = high;
                }
            }
            double diffRatio = (highest / lowest - 1) * 100;
            //            double prevDiffRatio = (prevHighest / prevLowest - 1) * 100;
            double highCloseDiffRatio = (hisHighest / highest - 1) * 100;

            boolean greater = true;
            if (diffRatio < 5 && avgClose > prevAvgClose
              && highCloseDiffRatio < 11) {
                System.out.println(code);
                res.add(code);
            }
        }
        return res;
    }

}
