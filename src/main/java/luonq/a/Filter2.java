package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import util.LoadData;

import java.util.List;
import java.util.Map;

/**
 * 找出近十天，涨跌幅超过7%的股票，并记录次数，排除2次以下，股价高于15的股票，并且十天前和当天差距不大于20%，最高点和最低点差距不大于25%
 * 例如：000882 2025.1.2至2025.1.14
 */
public class Filter2 extends BaseFilter{
    public static void main(String[] args) {
        LoadData.init();
        cal();
    }

    public static List<String> cal() {
        List<String> res = Lists.newArrayList();
        Map<String, List<StockKLine>> kLineMap = LoadData.kLineMap;

        Map<String, Integer> map = Maps.newHashMap();
        for (String code : kLineMap.keySet()) {
            if (!code.equals("003010")) {
                //                continue;
            }
            List<StockKLine> stockKLines = kLineMap.get(code);
            if (stockKLines.size() < 11) {
                //                System.out.println("x " + code);
                continue;
            }
            StockKLine latest = stockKLines.get(stockKLines.size() - 1);
            double curClose = latest.getClose();
            if (curClose > 15) {
                continue;
            }

            //            for (int j = 0; j < 11; j++) {
            int count = 0;
            double highest = Double.MIN_VALUE, lowest = Double.MAX_VALUE;
            for (int i = stockKLines.size() - 1; i >= stockKLines.size() - 1 - 10; i--) {
                StockKLine stockKLine = stockKLines.get(i);
                double lastClose = stockKLine.getLastClose();
                double close = stockKLine.getClose();
                double diffRatio = Math.abs((close - lastClose) / lastClose) * 100;
                if (diffRatio > 7) {
                    count++;
                }
                double high = stockKLine.getHigh();
                double low = stockKLine.getLow();
                if (high > highest) {
                    highest = high;
                }
                if (low < lowest) {
                    lowest = low;
                }
            }

            double close = stockKLines.get(stockKLines.size() - 1).getClose();
            double firstClose = stockKLines.get(stockKLines.size() - 11).getClose();
            double diffRatio = Math.abs((close - firstClose) / firstClose) * 100;
            double lowHighDiff = (highest - lowest) / lowest * 100;

            if (count >= 2 && diffRatio < 20 && lowHighDiff < 25 && close < 15) {
                Integer c = map.get(code);
                if (c != null && c < count) {
                    map.put(code, count);
                } else if (c == null) {
                    map.put(code, count);
                }
                //                System.out.println(code + " " + close);
            }
        }
        //        }

        for (String code : map.keySet()) {
            System.out.println(code + "\t" + map.get(code));
            res.add(code);
        }
        return res;
    }
}
