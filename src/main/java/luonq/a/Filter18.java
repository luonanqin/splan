package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import util.LoadData;

import java.util.List;
import java.util.Map;

/**
 * 连续三天下跌，且后面两天都是跳空低开低走，且成交量越来越少（非必须）
 * 需要观察是否触及长期均线（60日均线以上）
 * 例如：001236 2025-06-18 ~ 2025-06-20 600513 2025-06-17 ~ 2025-06-19
 */
public class Filter18 extends BaseFilter {

    public static void main(String[] args) {
        LoadData.init();
        cal();
        //        test(10);
        //                        testOne(14, null);
    }

    public static List<String> cal() {
        return cal(0, null);
    }

    public static void test(int days) {
        for (int i = 0; i < days; i++) {
            System.out.println("days: " + i);
            cal(i, null);
        }
    }

    public static void testOne(int day, String testCode) {
        cal(day, testCode);
    }

    public static List<String> cal(int prevDays, String testCode) {
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
            double curLastClose = latest.getLastClose();
            //            double curRatio = (curClose / curLastClose - 1) * 100;
            if (curClose > 10) {
                continue;
            }
            if (stockKLines.size() < 128) {
                //                System.out.println("x " + code);
                continue;
            }

            //            Map<String, BigDecimal> avgVolMap = cal50volMa(stockKLines);

            StockKLine _1Kline = stockKLines.get(stockKLines.size() - 1 - temp);
            StockKLine _2Kline = stockKLines.get(stockKLines.size() - 2 - temp);
            StockKLine _3Kline = stockKLines.get(stockKLines.size() - 3 - temp);
            StockKLine _4Kline = stockKLines.get(stockKLines.size() - 4 - temp);
            StockKLine _5Kline = stockKLines.get(stockKLines.size() - 5 - temp);

            double _4diffRatio = (_4Kline.getClose() / _4Kline.getLastClose() - 1) * 100;
            double _3diffRatio = (_3Kline.getClose() / _3Kline.getLastClose() - 1) * 100;
            double _2diffRatio = (_2Kline.getClose() / _2Kline.getLastClose() - 1) * 100;
            double _1diffRatio = (_1Kline.getClose() / _1Kline.getLastClose() - 1) * 100;

            boolean _4down = _4diffRatio < -1 && _4Kline.getClose() < _4Kline.getOpen();
            boolean _3down = _3diffRatio < -1 && _3Kline.getClose() < _3Kline.getOpen();
            boolean _2down = _2diffRatio < -1 && _2Kline.getClose() < _2Kline.getOpen();
            boolean _3skipDownOpen = _3Kline.getOpen() < _3Kline.getLastClose();
            boolean _2skipDownOpen = _2Kline.getOpen() < _2Kline.getLastClose();
            boolean _3volLower_4 = _3Kline.getVolume().compareTo(_4Kline.getVolume()) < 0;
            boolean _2volLower_3 = _2Kline.getVolume().compareTo(_3Kline.getVolume()) < 0;

            if (_4down
              && _3down
              && _2down
              && _3skipDownOpen
              && _2skipDownOpen
                //              && _3volLower_4
                //              && _2volLower_3
            ) {
                System.out.println(code);
                res.add(code);
            }
        }
        return res;
    }


}
