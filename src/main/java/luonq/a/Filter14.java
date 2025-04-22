package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Lists;
import util.LoadData;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * 连续三根阴线，且逐渐放量，并且这三根的平均成交量大于前面五根的平均成交量
 * 例如：
 */
public class Filter14 extends BaseFilter {

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
            if (curClose > 10) {
                continue;
            }
            if (stockKLines.size() < 128) {
                //                System.out.println("x " + code);
                continue;
            }

            StockKLine _1Kline = stockKLines.get(stockKLines.size() - 1 - temp);
            StockKLine _2Kline = stockKLines.get(stockKLines.size() - 2 - temp);
            StockKLine _3Kline = stockKLines.get(stockKLines.size() - 3 - temp);

            StockKLine _4Kline = stockKLines.get(stockKLines.size() - 4 - temp);
            StockKLine _5Kline = stockKLines.get(stockKLines.size() - 5 - temp);
            StockKLine _6Kline = stockKLines.get(stockKLines.size() - 6 - temp);
            StockKLine _7Kline = stockKLines.get(stockKLines.size() - 7 - temp);
            StockKLine _8Kline = stockKLines.get(stockKLines.size() - 8 - temp);

            double _1diffRatio = (_1Kline.getClose() / _1Kline.getLastClose() - 1) * 100;
            double _2diffRatio = (_2Kline.getClose() / _2Kline.getLastClose() - 1) * 100;
            double _3diffRatio = (_3Kline.getClose() / _3Kline.getLastClose() - 1) * 100;
            double _4diffRatio = (_4Kline.getClose() / _4Kline.getLastClose() - 1) * 100;
            boolean _1down = _1diffRatio < 0 && _1Kline.getClose() < _1Kline.getOpen();
            boolean _2down = _2diffRatio < 0 && _2Kline.getClose() < _2Kline.getOpen();
            boolean _3down = _3diffRatio < 0 && _3Kline.getClose() < _3Kline.getOpen();
            boolean _4up = _4diffRatio > 0 && _4Kline.getClose() > _4Kline.getOpen();
            boolean _1great2 = _1Kline.getVolume().compareTo(_2Kline.getVolume()) > 0;
            boolean _2great3 = _2Kline.getVolume().compareTo(_3Kline.getVolume()) > 0;

            BigDecimal avgVol3days = _1Kline.getVolume().add(_2Kline.getVolume()).add(_3Kline.getVolume()).divide(BigDecimal.valueOf(3), 0, RoundingMode.HALF_UP);
            BigDecimal avgVol5days = _4Kline.getVolume()
              .add(_5Kline.getVolume())
              .add(_6Kline.getVolume())
              .add(_7Kline.getVolume())
              .add(_8Kline.getVolume())
              .divide(BigDecimal.valueOf(5), 0, RoundingMode.HALF_UP);

            if (_1down &&
              _2down &&
              _3down &&
              _4up &&
              _1great2 &&
              _2great3 &&
              avgVol5days.compareTo(avgVol3days) < 0) {
                System.out.println(code);
                res.add(code);
            }
        }
        return res;
    }


}
