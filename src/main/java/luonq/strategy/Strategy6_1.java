package luonq.strategy;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import util.BaseUtils;
import util.Constants;

import java.util.List;
import java.util.Map;

/**
 * 测试
 * 1.价格大于6
 * 2.成交量大于10w
 * 3.当日有长下影线
 * 4.前日下跌
 * 5.当日下跌
 * 6*当日上涨
 * <p>
 * Created by Luonanqin on 2023/7/18.
 */
public class Strategy6_1 {

    public static String TEST_STOCK = "";
    public static String TEST_DATE = "";


    public static void main(String[] args) throws Exception {
        Map<String, String> fileMap = BaseUtils.getFileMap(Constants.TEST_PATH + "strategy6");
        List<Double> ratioRange = Lists.newArrayList(0.5, 0.6, 0.7, 0.8, 0.9);
        for (Double ratio : ratioRange) {
            double successCount = 0, failedCount = 0;

            for (String stock : fileMap.keySet()) {
                String file = fileMap.get(stock);
                if (StringUtils.isBlank(file)) {
                    continue;
                }
                if (StringUtils.isNotBlank(TEST_STOCK) && !stock.equals(TEST_STOCK)) {
                    continue;
                }

//                System.out.println(stock);
                List<String> dataList = BaseUtils.readFile(file);
                for (int i = 1; i < dataList.size() - 1; i++) {
                    String[] split = dataList.get(i).split(",");
                    String date = split[0];
                    double close = Double.parseDouble(split[2]);
                    double nextClose = Double.parseDouble(split[5]);
                    double lossRatio = Double.parseDouble(split[12]);
                    double gainRatio = Double.parseDouble(split[13]);

                    boolean loss = Boolean.parseBoolean(split[14]);
                    boolean gain = Boolean.parseBoolean(split[15]);
                    boolean prevLoss = Boolean.parseBoolean(split[16]);
                    boolean lowLtPrevLow = Boolean.parseBoolean(split[19]);
                    boolean highLtPrevOpen = Boolean.parseBoolean(split[20]);
                    boolean gainRatioRes = gain && gainRatio > ratio;
                    boolean lossRatioRes = loss && lossRatio > ratio;

                    lossRatioRes = true;
                    gainRatioRes = true;
                    //                gain = true;
                    //                lowLtPrevLow = true;
                    //                highLtPrevOpen = true;

                    if ((gainRatioRes || lossRatioRes) &&
                      lowLtPrevLow && prevLoss && highLtPrevOpen) {
                        boolean success = nextClose > close;
                        if (success) {
                            successCount++;
                        } else {
                            failedCount++;
                        }
                        //                        System.out.println(date + " " + success);
                    }
                }
            }
            System.out.println("ratio=" + ratio + ", successCount=" + successCount + ", failedCount=" + failedCount + ", rate=" + successCount / (successCount + failedCount));
        }
    }
}
