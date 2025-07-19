package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import util.LoadData;

import java.util.List;
import java.util.Map;

/**
 * 测试场景：一次涨停板之后，第二天平开冲高收阴（不一定收假阴线），要多久才能涨回第二天的最高点
 * 测试结果：34%没有回，26%5天内回（11.6%2天内回，14.4%2~5天内回）。40%5天外回
 * 结论：只要平开冲高收阴线就一定要卖出
 * 例子：002103 2025-07-01
 */
public class Test1 extends BaseFilter {

    public static void main(String[] args) {
        LoadData.init();
        new Test1().cal();
        //        test(10);
        //        testOne(0, "002103");
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
                double highDiff = (nextKline.getHigh() / nextKline.getLastClose() - 1) * 100;

                if (openDiff < 1 || openDiff > 3) {
                    continue;
                }

                if (highDiff < 6) {
                    continue;
                }

                if (nextKline.getOpen() < nextKline.getClose()) {
                    continue;
                }

                indexMap.put(i + 1, 0);
            }

            for (Integer i : indexMap.keySet()) {
                int j = i + 1;
                StockKLine i_kline = stockKLines.get(i);
                for (; j < stockKLines.size() - 1; j++) {
                    StockKLine kLine = stockKLines.get(j);
                    if (kLine.getClose() > i_kline.getHigh()) {
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
