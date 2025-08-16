package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import util.LoadData;

import java.util.List;
import java.util.Map;

/**
 * 测试场景：10天内振幅超过+20%，但是十天内快速回到第一天的价格，看后续能否快速上涨
 * 测试结果：
 * 结论：
 * 例子：603881 2024-12-31 ~ 2025-02-10
 */
public class Test5 extends BaseFilter {

    public static void main(String[] args) {
        LoadData.init();
        Test5 test5 = new Test5();
        //        cal();
        //                test(10);
        //        test5.testOne(0, "600539");
        test5.testOne(0, null);
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
            Map<Integer, Integer> indexMap = Maps.newHashMap();
            for (int i = 1; i < stockKLines.size() - 10; i++) {
                StockKLine _1k = stockKLines.get(i);
                if (_1k.getDate().equals("2024-12-26")) {
                    //                    System.out.println();
                }

                double highestClose = Double.MIN_VALUE;
                int highestIndex = 0;
                for (int j = i; j < i + 11; j++) {
                    StockKLine kLine = stockKLines.get(j);
                    if (kLine.getClose() > highestClose) {
                        highestClose = kLine.getClose();
                        highestIndex = j;
                    }
                }
                if (highestClose / _1k.getClose() < 1.2) {
                    continue;
                }
                if (highestIndex < i + 3) {
                    continue;
                }

                double lowestClose = Double.MAX_VALUE;
                for (int j = highestIndex + 1; j < i + 11; j++) {
                    StockKLine kLine = stockKLines.get(j);
                    if (kLine.getClose() < lowestClose) {
                        lowestClose = kLine.getClose();
                    }
                }
                if (lowestClose > _1k.getClose()) {
                    continue;
                }

                if (i - 10 > 0) {
                    StockKLine last10K = stockKLines.get(i - 10);
                    if (_1k.getClose() / last10K.getClose() > 1.3) {
                        continue;
                    }
                }

                //                StockKLine _2k = stockKLines.get(i + 1);
                //                StockKLine _3k = stockKLines.get(i + 2);
                //                StockKLine _4k = stockKLines.get(i + 3);
                //                StockKLine _5k = stockKLines.get(i + 4);
                //                if (!_1k.isTop() && !_2k.isTop() && !_3k.isTop() && !_4k.isTop() && !_5k.isTop()) {
                //                    i += 4;
                //                    continue;
                //                }

                indexMap.put(i + 1, 0);
            }

            for (Integer i : indexMap.keySet()) {
                int j = i + 3;
                StockKLine i_kline = stockKLines.get(i);
                for (; j < stockKLines.size(); j++) {
                    StockKLine kLine = stockKLines.get(j);
                    if (kLine.getClose() > i_kline.getClose()) {
                        int days = j - i;
                        indexMap.put(i, days);
                        break;
                    }
                }
            }

            for (Integer i : indexMap.keySet()) {
                StockKLine kLine = stockKLines.get(i);
                int days = indexMap.get(i) == 0 ? -1 : indexMap.get(i);
                System.out.println(code + "\t" + kLine.getDate() + "\t" + days);
            }
        }
        return res;
    }


}
