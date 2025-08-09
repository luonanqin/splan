package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import util.LoadData;

import java.util.List;
import java.util.Map;

/**
 * 测试场景：涨停后至少连续大跌两天，之后一个月回到高点，看看后续是否能继续上升
 * 测试结果：
 * 结论：
 * 例子：603881 2024-12-31 ~ 2025-02-10
 */
public class Test4 extends BaseFilter {

    public static void main(String[] args) {
        LoadData.init();
        Test4 test3 = new Test4();
        //        cal();
        //                test(10);
        test3.testOne(0, "603881");
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
                double openDiff = (topKline.getOpen() / topKline.getLastClose() - 1) * 100;
                // 第一天涨停
                if (topKline.getDiffRatio() <= 9.9) {
                    continue;
                }

                //                StockKLine lastKline = stockKLines.get(i - 1);
                //                if (lastKline.getDiffRatio() > 9.9) {
                //                    continue;
                //                }

                StockKLine _1nextKline = stockKLines.get(i + 1);
                // 第二天阴线不分真假
                if (_1nextKline.getClose() > _1nextKline.getOpen()) {
                    continue;
                }

                StockKLine _2nextKline = stockKLines.get(i + 1);
                // 第三天阴线近跌停
                if (_2nextKline.getDiffRatio() > -9) {
                    continue;
                }

                StockKLine _3nextKline = stockKLines.get(i + 1);
                // 第四天阴线近跌停
                if (_3nextKline.getDiffRatio() > -9) {
                    continue;
                }

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
                System.out.println(code + " " + kLine.getDate() + " " + days);
            }
        }
        return res;
    }


}
