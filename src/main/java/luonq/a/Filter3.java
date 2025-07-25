package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import util.LoadData;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * 找出5天前近60天，成交量有大于50天均量线的k线，记录总数，取至少要大于2天的股票，最高和最低相差小于30%
 * 近15个交易日内要至少一次4%以上的涨幅
 * 例如：002628 2025.1.2至2025.3.21
 */
public class Filter3 extends BaseFilter {

    public static void main(String[] args) {
        LoadData.init();
        new Filter3().cal();
    }

    public List<String> cal(int prevDays, String testCode) {
        List<String> res = Lists.newArrayList();
        Map<String, List<StockKLine>> kLineMap = LoadData.kLineMap;

        Map<String, Integer> map = Maps.newHashMap();
        for (String code : kLineMap.keySet()) {
            if (StringUtils.isNotBlank(testCode) && !code.equals(testCode)) {
                continue;
            }
            List<StockKLine> stockKLines = kLineMap.get(code);
            int temp = fixTemp(stockKLines, prevDays); // 用于测试历史数据做日期调整
            StockKLine latest = stockKLines.get(stockKLines.size() - 1 - temp);
            double curClose = latest.getClose();
            if (curClose > 10 || latest.getDiffRatio() > 1 || curClose < 4) {
                continue;
            }
            if (stockKLines.size() < 128) {
                //                System.out.println("x " + code);
                continue;
            }

            BigDecimal vol = BigDecimal.ZERO;
            double highest = Double.MIN_VALUE, lowest = Double.MAX_VALUE;
            int count = 0;
            int lastIndex = Integer.MAX_VALUE;
            for (int i = 0; i < stockKLines.size() - 5 - temp; i++) {
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
                if (volume.compareTo(_2avgVol) > 0 && i > lastIndex + 5) {
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

            // 15天内有涨幅超过4%的
            boolean up4 = false;
            for (int i = stockKLines.size() - 1 - temp; i >= stockKLines.size() - 16 - temp; i--) {
                StockKLine kLine = stockKLines.get(i);
                double close = kLine.getClose();
                double lastClose = kLine.getLastClose();
                double ratio = (close / lastClose - 1) * 100;
                if (ratio > 4) {
                    up4 = true;
                    break;
                }
            }

            if (count > 2 && count < 6 && lowHighDiff < 30 && up4) {
                map.put(code, count);
            }
        }

        for (String code : map.keySet()) {
            System.out.println(code + "\t" + map.get(code));
            res.add(code);
        }

        return res;
    }
}
