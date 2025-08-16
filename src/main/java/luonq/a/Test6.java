package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import util.LoadData;

import java.util.List;
import java.util.Map;

/**
 * 测试场景：寻找某一天阴线振幅超过前面十天平均值1.5倍，第二天阳线收盘不低于前日开盘的1%，看看后续走势
 * 测试结果：
 * 结论：前面没有明显出货的情况下，第二天阳线不要超出阴线太多，后续基本都向上走势
 * 例子：000813 2025-07-15 ~ 2025-07-16
 */
public class Test6 extends BaseFilter {

    public static void main(String[] args) {
        LoadData.init();
        Test6 test6 = new Test6();
        //        cal();
        //                test(10);
        //        test6.testOne(0, "000813");
        test6.testOne(0, null);
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

            Map<Integer, Integer> indexMap = Maps.newHashMap();
            int latestI = 0;
            for (int i = 10; i < stockKLines.size() - 1; i++) {
                StockKLine _1k = stockKLines.get(i);
                if (_1k.getDate().equals("2025-07-15")) {
                    //                    System.out.println();
                }
                if (_1k.getDiffRatio() > 0) {
                    continue;
                }
                if (_1k.getHigh() / _1k.getOpen() > 1.01) {
                    continue;
                }

                double avgSwing = 0;
                boolean hasTop = false;
                for (int j = i - 1; j >= i - 9; j--) {
                    StockKLine kLine = stockKLines.get(j);
                    double swing = (kLine.getHigh() - kLine.getLow()) / kLine.getLastClose() * 100;
                    avgSwing += swing;
                    if (kLine.isTop()) {
                        hasTop = true;
                    }
                }
                if (hasTop) {
                    continue;
                }
                avgSwing = avgSwing / 10;

                double swing = (_1k.getHigh() - _1k.getLow()) / _1k.getLastClose() * 100;
                if (swing < avgSwing * 1.5) {
                    continue;
                }

                StockKLine _2k = stockKLines.get(i + 1);
                if (_2k.getClose() < _1k.getOpen() * 0.99) {
                    continue;
                }

                latestI = i;
            }
            if (latestI < stockKLines.size() - 50) {
                continue;
            }
            indexMap.put(latestI, 0);

            for (Integer i : indexMap.keySet()) {
                int j = i + 2;
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
