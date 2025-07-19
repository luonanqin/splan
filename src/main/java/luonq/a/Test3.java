package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import util.LoadData;

import java.util.List;
import java.util.Map;

/**
 * 测试场景：在连续一段时间上涨后，在某一次涨停板之后，第二天低开低走接近前一天的开盘价，即一个实体大阳和实体大阴挨着（可以低于10日线但是不能低于20日线），要多久才能回到第二天的收盘
 * 测试结果：
 * 结论：
 * 例子：601003 2025-07-15
 */
public class Test3 extends BaseFilter {

    public static void main(String[] args) {
        LoadData.init();
        Test3 test3 = new Test3();
        //        cal();
        //                test(10);
        test3.testOne(0, "001316");
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
                // 第一天涨停但非一字板
                if (topKline.getDiffRatio() <= 9.9 || openDiff > 5) {
                    continue;
                }

                //                StockKLine lastKline = stockKLines.get(i - 1);
                //                if (lastKline.getDiffRatio() > 9.9) {
                //                    continue;
                //                }

                StockKLine nextKline = stockKLines.get(i + 1);
                double nextOpenDiff = (nextKline.getOpen() / nextKline.getLastClose() - 1) * 100;
                double nextHighDiff = (nextKline.getHigh() / nextKline.getLastClose() - 1) * 100;
                // 第二天开盘非大低开和高开
                if (nextOpenDiff < -4 || nextOpenDiff > 2) {
                    continue;
                }

                // 第二天收盘要低于7个点但是非跌停
                double nextDiffRatio = nextKline.getDiffRatio();
                if (nextDiffRatio > -7 || nextDiffRatio < -9.9) {
                    continue;
                }

                // 第二天最高不超过开盘2个点
                if (nextHighDiff - nextOpenDiff > 2) {
                    continue;
                }

                indexMap.put(i + 1, 0);
            }

            for (Integer i : indexMap.keySet()) {
                int j = i + 1;
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
