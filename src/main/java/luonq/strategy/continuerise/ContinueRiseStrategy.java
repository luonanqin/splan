package luonq.strategy.continuerise;

import bean.BOLL;
import bean.ContinueRise;
import bean.StockKLine;
import com.google.common.collect.Sets;
import org.apache.commons.collections4.SetUtils;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Created by Luonanqin on 2023/3/14.
 */
public class ContinueRiseStrategy {

    // 成交量递减
    public static boolean filter1(ContinueRise continueRise) {
        List<StockKLine> riseList = continueRise.getRiseList();
        StockKLine kLine1 = riseList.get(0);
        StockKLine kLine2 = riseList.get(1);
        StockKLine kLine3 = riseList.get(2);

        double vol1 = kLine1.getVolume().doubleValue();
        double vol2 = kLine2.getVolume().doubleValue();
        double vol3 = kLine3.getVolume().doubleValue();

        return vol1 > vol2 && vol2 > vol3;
    }

    // 成交量先减后增 且 第三天比第一天少
    public static boolean filter2(ContinueRise continueRise) {
        List<StockKLine> riseList = continueRise.getRiseList();
        StockKLine kLine1 = riseList.get(0);
        StockKLine kLine2 = riseList.get(1);
        StockKLine kLine3 = riseList.get(2);

        double vol1 = kLine1.getVolume().doubleValue();
        double vol2 = kLine2.getVolume().doubleValue();
        double vol3 = kLine3.getVolume().doubleValue();

        return vol1 > vol2 && vol2 < vol3;
    }

    // 成交量先增后减
    public static boolean filter3(ContinueRise continueRise) {
        List<StockKLine> riseList = continueRise.getRiseList();
        StockKLine kLine1 = riseList.get(0);
        StockKLine kLine2 = riseList.get(1);
        StockKLine kLine3 = riseList.get(2);

        double vol1 = kLine1.getVolume().doubleValue();
        double vol2 = kLine2.getVolume().doubleValue();
        double vol3 = kLine3.getVolume().doubleValue();

        return vol1 < vol2 && vol2 > vol3;
    }

    // 第三天成交量超过第二天成交量两倍以上
    public static boolean filter4(ContinueRise continueRise) {
        List<StockKLine> riseList = continueRise.getRiseList();
        StockKLine kLine1 = riseList.get(0);
        StockKLine kLine2 = riseList.get(1);
        StockKLine kLine3 = riseList.get(2);

        double vol1 = kLine1.getVolume().doubleValue();
        double vol2 = kLine2.getVolume().doubleValue();
        double vol3 = kLine3.getVolume().doubleValue();

        return vol3 > (vol2 * 2);
    }

    // 三天的平均涨幅小于2
    public static boolean filter5(ContinueRise continueRise) {
        List<StockKLine> riseList = continueRise.getRiseList();
        StockKLine kLine1 = riseList.get(0);
        StockKLine kLine2 = riseList.get(1);
        StockKLine kLine3 = riseList.get(2);
        StockKLine prev = continueRise.getPrev();

        double ratio1 = kLine1.getClose() / prev.getClose() - 1;
        double ratio2 = kLine2.getClose() / kLine1.getClose() - 1;
        double ratio3 = kLine3.getClose() / kLine2.getClose() - 1;

        return (ratio1 + ratio2 + ratio3) / 3 < 0.02d;
    }

    // 第三天收盘超上轨
    public static boolean filter6(ContinueRise continueRise) {
        List<StockKLine> riseList = continueRise.getRiseList();
        StockKLine kLine3 = riseList.get(2);
        StockKLine prev = continueRise.getPrev();
        BOLL currBoll = continueRise.getCurrBoll();

        return kLine3.getClose() > currBoll.getUp();
    }

    // 第三天长上影线
    public static boolean filter7(ContinueRise continueRise) {
        List<StockKLine> riseList = continueRise.getRiseList();
        StockKLine kLine3 = riseList.get(2);
        double high = kLine3.getHigh();
        double close = kLine3.getClose();
        double low = kLine3.getLow();
        double ratio = (high - close) / (high - low);

        return ratio > 0.3;
    }

    // 第三天最高低于第二天最高
    public static boolean filter8(ContinueRise continueRise) {
        List<StockKLine> riseList = continueRise.getRiseList();
        StockKLine kLine2 = riseList.get(1);
        StockKLine kLine3 = riseList.get(2);
        double high_2 = kLine2.getHigh();
        double high_3 = kLine3.getHigh();

        return high_2 > high_3;
    }

    // 第三天的最高和收盘boll的中轨差值比例小于0.005
    public static boolean filter9(ContinueRise continueRise) {
        BOLL currBoll = continueRise.getCurrBoll();
        List<StockKLine> riseList = continueRise.getRiseList();
        StockKLine currKLine = riseList.get(2);
        return Math.abs((currKLine.getHigh() - currBoll.getMb()) / currBoll.getMb()) < 0.005d;
    }

    // 第三天的最高和收盘boll的上轨差值比例小于0.01
    public static boolean filter10(ContinueRise continueRise) {
        BOLL currBoll = continueRise.getCurrBoll();
        List<StockKLine> riseList = continueRise.getRiseList();
        StockKLine currKLine = riseList.get(2);
        return Math.abs((currKLine.getHigh() - currBoll.getUp()) / currBoll.getUp()) < 0.01d;
    }
    
    public static Set<String> computerIntersection(Collection... coll) {
        Set<String> result = Sets.newHashSet();

        for (int i = 0; i < coll.length; i++) {
            if (i == 0) {
                result = Sets.newHashSet(coll[i]);
            } else {
                result = SetUtils.intersection(result, Sets.newHashSet(coll[i]));
            }
        }
        return result;
    }
}
