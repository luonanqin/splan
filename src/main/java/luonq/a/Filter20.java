package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import util.LoadData;

import java.util.List;
import java.util.Map;

/**
 * 第一天涨停（之前不是涨停，第二天真阳线（涨幅小于涨停），第三天真阳线（涨幅小于第二天），
 * 预期第四天大涨
 * 例如：601208 2025-07-14 ~ 2025-07-17
 */
public class Filter20 extends BaseFilter {

    public static void main(String[] args) {
        LoadData.init();
        //        cal();
        test(10);
        //                                testOne(0, "601208");
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
                //                continue;
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

            double _4diffRatio = (_4Kline.getClose() / _4Kline.getLastClose() - 1) * 100;
            double _3diffRatio = (_3Kline.getClose() / _3Kline.getLastClose() - 1) * 100;
            double _2diffRatio = (_2Kline.getClose() / _2Kline.getLastClose() - 1) * 100;
            double _1diffRatio = (_1Kline.getClose() / _1Kline.getLastClose() - 1) * 100;

            boolean _4up = _4diffRatio < 9.9;
            boolean _3up = _3diffRatio > 9.92 && _3Kline.getOpen() != _3Kline.getClose();
            boolean _2up = _2diffRatio < 9.9 && _2diffRatio > 0 && _2diffRatio < _3diffRatio && _2Kline.getClose() > _2Kline.getOpen();
            boolean _1up = _1diffRatio < 9.9 && _1diffRatio > 0 && _1diffRatio < _2diffRatio && _1Kline.getClose() > _1Kline.getOpen();

            if (
              _4up
                && _3up
                && _2up
                && _1up
            ) {
                System.out.println(code);
                res.add(code);
            }
        }
        return res;
    }


}
