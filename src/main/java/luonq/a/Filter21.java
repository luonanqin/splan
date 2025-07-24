package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import util.LoadData;

import java.util.List;
import java.util.Map;

/**
 * 阴包阳洗盘，第一天涨停但非一字板，第二天开盘非大低开和高开，收盘要低于7个点但是非跌停，并且最高不超过开盘2个点，表示单边下跌无明显反弹
 * 1.不要高位出现偏离五日线，即涨了至少三个板，阴线收盘也偏离5日线。
 * 2.不要破20日线，即阴线收盘破20日线
 * 3.均线要多头排列，或者走过一小段上涨后均线多头排列后
 * 4.阴线实体最好不低于阳线实体，两个实体大小接近最好
 * 5.阴线最好是单边下跌，中途反弹力度弱
 * 6.尽量出现在横盘
 * 例如：601003 2025-07-15 ~ 2025-07-16
 */
public class Filter21 extends BaseFilter {

    public static void main(String[] args) {
        LoadData.init();
        Filter21 filter21 = new Filter21();
//        filter21.cal();
//        filter21.test(30);
        filter21.testOne(2, "603176");
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
            if (curClose > 10 || curClose < 4) {
                continue;
            }
            if (stockKLines.size() < 128) {
                //                System.out.println("x " + code);
                continue;
            }

            index -= 1; // 从前一天开始找
            StockKLine topKline = stockKLines.get(index);
            double openDiff = (topKline.getOpen() / topKline.getLastClose() - 1) * 100;
            // 第一天涨停但非一字板
            if (topKline.getDiffRatio() <= 9.9 || openDiff > 5) {
                continue;
            }

            StockKLine nextKline = stockKLines.get(index + 1);
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

            System.out.println(code);
            res.add(code);
        }
        return res;
    }


}
