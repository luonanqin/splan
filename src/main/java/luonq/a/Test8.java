package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import util.LoadData;

import java.util.List;
import java.util.Map;

/**
 * 测试场景：前一天2个点以下涨停并且再之前不是涨停即不连板，第二天最高不超过涨停价的2%，但是跌幅超过5%，看看后面怎么走
 * 测试结果：30%没有回到原高点，25%5天内回到原高点
 * 结论：如果是非高位股，洗盘时间要长并且向上，如果是高位股，就要看第二天的下跌幅度
 * 例子：601606 2025-07-22 603767 2025-07-30
 */
public class Test8 extends BaseFilter {

    public static void main(String[] args) {
        LoadData.init();
        Test8 test8 = new Test8();
        //        cal();
        //                test(10);
//        test8.testOne(0, "603767");
        test8.testOne(0, null);
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
            for (int i = 1; i < stockKLines.size()-1; i++) {
                StockKLine kLine = stockKLines.get(i);
                if (!kLine.isTop()) {
                    continue;
                }
                if (kLine.getOpen()/kLine.getLastClose()>1.02) {
                    continue;
                }

                StockKLine prevKLine = stockKLines.get(i-1);
                if (prevKLine.isTop()) {
                    continue;
                }

                StockKLine nextKline = stockKLines.get(i + 1);
                if (nextKline.getDiffRatio()>-4) {
                    continue;
                }
                if (nextKline.getHigh()/nextKline.getLastClose()>1.01) {
                    continue;
                }
            indexMap.put(i, 0);
            }

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
