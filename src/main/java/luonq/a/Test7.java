package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import util.LoadData;

import java.util.List;
import java.util.Map;

/**
 * 测试场景：寻找十天前的收盘价，距离30天之前到10.8之间的最高点不超过10%的股票，分析这十天的走势
 * 测试结果：
 * 结论：前面没有明显出货的情况下，第二天阳线不要超出阴线太多，后续基本都向上走势
 * 例子：000813 2025-07-15 ~ 2025-07-16
 */
public class Test7 extends BaseFilter {

    public static void main(String[] args) {
        LoadData.init();
        Test7 test7 = new Test7();
        //        cal();
        //                test(10);
//        test7.testOne(0, "601199");
        test7.testOne(0, null);
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
            double highest = Double.MIN_VALUE;
            for (int i = 0; i < stockKLines.size() - 40; i++) {
                StockKLine kLine = stockKLines.get(i);
                double high = kLine.getHigh();
                if (high > highest) {
                    highest = high;
                }
            }
            StockKLine _10k = stockKLines.get(stockKLines.size() - 10);
            if (Math.abs(_10k.getClose() / highest - 1) > 0.1) {
                continue;
            }
            indexMap.put(stockKLines.size() - 10, 0);

            for (Integer i : indexMap.keySet()) {
                int j = i + 10;
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
