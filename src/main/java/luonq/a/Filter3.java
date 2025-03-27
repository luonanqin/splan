package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Maps;
import util.LoadData;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * 找出近60天，成交量有大于50天均量线的k线，记录总数，取至少要大于2天的股票
 * 例如：002628 2025.1.2至2025.3.21
 */
public class Filter3 {

    public static void main(String[] args) {
        LoadData.init();

        Map<String, List<StockKLine>> kLineMap = LoadData.kLineMap;

        Map<String, Integer> map = Maps.newHashMap();
        for (String code : kLineMap.keySet()) {
            if (!code.equals("002628")) {
                //                continue;
            }
            List<StockKLine> stockKLines = kLineMap.get(code);
            StockKLine latest = stockKLines.get(stockKLines.size() - 1);
            if (latest.getClose() > 15d) {
                continue;
            }
            if (stockKLines.size() < 128) {
                System.out.println("x " + code);
                continue;
            }

            BigDecimal vol = BigDecimal.ZERO;
            double highest = Double.MIN_VALUE, lowest = Double.MAX_VALUE;
            int count = 0;
            int lastIndex = Integer.MAX_VALUE;
            for (int i = 0; i < stockKLines.size(); i++) {
                StockKLine kLine = stockKLines.get(i);
                BigDecimal volume = kLine.getVolume();
                vol = vol.add(volume);

                if (i < 50) {
                    continue;
                }

                BigDecimal _2avgVol = vol.multiply(BigDecimal.valueOf(2)).divide(BigDecimal.valueOf(50), 0, RoundingMode.HALF_UP);
                vol = vol.subtract(stockKLines.get(i - 50).getVolume());

                if (stockKLines.size() - i > 50) {
                    continue;
                }
                if (volume.compareTo(_2avgVol) > 0) {
                    //                    System.out.println(kLine.getDate());
                    count++;
                    lastIndex = i;
                }
                double high = kLine.getHigh();
                double low = kLine.getLow();
                if (high > highest) {
                    highest = high;
                }
                if (low < lowest) {
                    lowest = low;
                }
            }
            double lowHighDiff = (highest - lowest) / lowest * 100;

            if (count > 2 && count < 6 && lowHighDiff < 30 && lastIndex < stockKLines.size() - 5) {
                map.put(code, count);
            }
        }

        for (String code : map.keySet()) {
            System.out.println(code + "\t" + map.get(code));
        }
    }
}
