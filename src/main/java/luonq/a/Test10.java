package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import util.LoadData;

import java.util.List;
import java.util.Map;

/**
 * 测试场景：找10天内的最高点，然后从20天前开始往前找，直到10.8日，找到最高，计算两者最高点差距绝对值小于10%（也就是超过或者低于前高），计算多久盈利超过20%
 * 测试结果：
 * 结论：
 * 例子：
 */
public class Test10 extends BaseFilter {

    public static void main(String[] args) {
        LoadData.init();
        Test10 test3 = new Test10();
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

            Map<Integer, Integer> indexMap = Maps.newHashMap();
            for (int i = 0; i < stockKLines.size() - 4; i++) {
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

                StockKLine _1k = stockKLines.get(i + 1);
                // 第二天长上影
                double cOrO = _1k.getClose() > _1k.getOpen() ? _1k.getClose() : _1k.getOpen();
                double _1kHigh = _1k.getHigh();
                if ((_1kHigh - cOrO) / (_1kHigh - _1k.getLow()) > 0.3) {
                    continue;
                }

                StockKLine _2k = stockKLines.get(i + 2);
                StockKLine _3k = stockKLines.get(i + 3);
                StockKLine _4k = stockKLines.get(i + 4);
                // 后面三天某一天高点超过长上影那天的高点
                if (_2k.getHigh() < _1kHigh && _3k.getHigh() < _1kHigh && _4k.getHigh() < _1kHigh) {
                    continue;
                }

                indexMap.put(i, 0);
            }

            for (Integer i : indexMap.keySet()) {
                int j = i + 5;
                StockKLine i_kline = stockKLines.get(i);
                for (; j < stockKLines.size(); j++) {
                    StockKLine kLine = stockKLines.get(j);
                    if (kLine.getClose() / i_kline.getClose() > 1.3) {
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
