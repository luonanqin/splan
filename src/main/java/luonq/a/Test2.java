package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import util.LoadData;

import java.util.List;
import java.util.Map;

/**
 * 测试场景：一次涨停板之后，第二天高开低走（不一定收假阴线），要多久才能涨回第二天的开盘
 * 测试结果：26.2%没回，15.4%5天内回（8.6%2天内回，6.8%2~5天内回），58.4%5天外回
 * 结论：只要高开低走就一定要卖出
 * 例子：002153 2025-06-09
 */
public class Test2 extends BaseFilter {

    public static void main(String[] args) {
        LoadData.init();
        cal();
        //        test(10);
        //        testOne(0, "002153");
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
            Map<Integer, Integer> indexMap = Maps.newHashMap();
            for (int i = 1; i < stockKLines.size() - 1; i++) {
                StockKLine topKline = stockKLines.get(i);
                if (topKline.getDiffRatio() <= 9.9) {
                    continue;
                }

                StockKLine lastKline = stockKLines.get(i - 1);
                if (lastKline.getDiffRatio() > 9.9) {
                    continue;
                }

                StockKLine nextKline = stockKLines.get(i + 1);
                double openDiff = (nextKline.getOpen() / nextKline.getLastClose() - 1) * 100;

                if (openDiff < 8) {
                    continue;
                }

                if (nextKline.getOpen() <= nextKline.getClose()) {
                    continue;
                }

                // 筛选高开低走但是没有低于前日涨停价
                //                if (nextKline.getClose()<nextKline.getLastClose()) {
                //                    continue;
                //                }

                indexMap.put(i + 1, 0);
            }

            for (Integer i : indexMap.keySet()) {
                int j = i + 1;
                StockKLine i_kline = stockKLines.get(i);
                for (; j < stockKLines.size() - 1; j++) {
                    StockKLine kLine = stockKLines.get(j);
                    if (kLine.getClose() > i_kline.getOpen()) {
                        int days = j - i;
                        indexMap.put(i, days);
                        break;
                    }
                }
            }

            for (Integer i : indexMap.keySet()) {
                StockKLine kLine = stockKLines.get(i);
                int days = indexMap.get(i) == 0 ? -1 : indexMap.get(i);
                System.out.println(code + " " + kLine.getDate() + " " + days);
            }
        }
        return res;
    }


}
