package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import util.LoadData;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 测试场景：寻找6天涨幅超过60%的直到收盘最高点后，到目前为止已经十天的股票，观察走势
 * 测试结果：
 * 结论：
 * 例子：
 */
public class Test11 extends BaseFilter {

    public static void main(String[] args) {
        LoadData.init();
        Test11 test8 = new Test11();
        //        cal();
        //                test(10);
        //        test8.testOne(0, "603767");
        test8.testOne(0, null);
    }

    public List<String> cal(int prevDays, String testCode) {
        List<String> res = Lists.newArrayList();
        Map<String, List<StockKLine>> kLineMap = LoadData.kLineMap;

        Set<String> result = Sets.newHashSet();
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

            int last = stockKLines.size();
            for (int i = 10; i <= 30; i++) {
                StockKLine _10k = stockKLines.get(last - i);
                StockKLine _16k = stockKLines.get(last - i - 4);
                if (_10k.getClose() / _16k.getClose() > 1.4) {
                    result.add(code);
                }
            }
        }
        result.forEach(System.out::println);
        return res;
    }


}
