package luonq.test;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import util.BaseUtils;
import util.Constants;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by Luonanqin on 2023/3/26.
 */
public class Test2 {
    public static void calCallWithProtect() throws Exception {
        List<String> lines = BaseUtils.readFile(Constants.USER_PATH + "optionData/裸买和带保护");
        Map<String, List<Double>> dateToCall = Maps.newTreeMap(Comparator.comparing(BaseUtils::formatDateToInt));
        for (String line : lines) {
            String[] split = line.split("\t");
            String date = split[0];
            double call = Double.parseDouble(split[1]);

            if (!dateToCall.containsKey(date)) {
                dateToCall.put(date, Lists.newArrayList());
            }
            dateToCall.get(date).add(call);
        }

        double init = 10000;
        for (String date : dateToCall.keySet()) {
            List<Double> doubles = dateToCall.get(date);
            if (doubles.size() == 1) {
                //                continue;
            }
            Double avgRatio = doubles.stream().collect(Collectors.averagingDouble(d -> d));
            init = init * (1 + avgRatio / 100);
            System.out.println(date + "\t" + (int) init);
        }
    }

    public static void calGainWithHandleFee() throws Exception {
        List<String> lines = BaseUtils.readFile(Constants.USER_PATH + "optionData/双开带手续费");
        Map<String, Map<String, Integer>> dateToVolume = Maps.newHashMap();
        for (String line : lines) {
            String[] split = line.split("\t");
            String date = split[0];
            int lastVolume = Integer.valueOf(split[5]);

            if (!dateToVolume.containsKey(date)) {
                dateToVolume.put(date, Maps.newHashMap());
            }
            dateToVolume.get(date).put(line, lastVolume);
        }

        Map<String, List<String>> dateToLine = Maps.newTreeMap(Comparator.comparing(BaseUtils::formatDateToInt));
        dateToVolume.forEach((k, v) -> {
            //            if (v.size() > 5) {
            //                List<String> sorted = v.entrySet().stream().sorted((o1, o2) -> o2.getValue().compareTo(o1.getValue())).map(s -> s.getKey()).collect(Collectors.toList());
            //                dateToLine.put(k, sorted.subList(0, 5));
            //            } else {
            dateToLine.put(k, Lists.newArrayList(v.keySet()));
            //            }
        });

        Map<Integer, Integer> openSumToCount = Maps.newHashMap();
        Map<Integer, Integer> openSumAllToCount = Maps.newHashMap();
        double init = 10000;
        for (String date : dateToLine.keySet()) {
            List<String> l = dateToLine.get(date);
            if (l.size() == 1) {
//                continue;
            }
            double avgFund = init / l.size();
            init = 0;
            for (String line : l) {
                String[] split = line.split("\t");
                double callBuy = Double.parseDouble(split[1]);
                double callSell = Double.parseDouble(split[2]);
                double putBuy = Double.parseDouble(split[3]);
                double putSell = Double.parseDouble(split[4]);
                int sumOpen = (int) (callBuy + putBuy);
                if (!openSumToCount.containsKey(sumOpen)) {
                    openSumToCount.put(sumOpen, 0);
                }
                if (!openSumAllToCount.containsKey(sumOpen)) {
                    openSumAllToCount.put(sumOpen, 0);
                }
                //                if (callBuy + putBuy < 0.5) {
                //                    init += avgFund;
                //                    continue;
                //                }
                int count = (int) (avgFund / 100 / (callBuy + putBuy));
                //                if (callBuy + putBuy < 1) {
                //                    count = count / 2;
                //                }

                //                double fee = futuFee(callBuy, callSell, putBuy, putSell, count);
                double fee = ibkrFee(callBuy, callSell, putBuy, putSell, count);
                double gainOrLoss = count * (callSell + putSell - callBuy - putBuy) * 100;

                double actualGainOrLoss = gainOrLoss - fee;

                init += (avgFund + actualGainOrLoss);

                if (gainOrLoss < -20) {
                    Integer openCount = openSumToCount.get(sumOpen) + 1;
                    openSumToCount.put(sumOpen, openCount);
                }

                int openAllCount = openSumAllToCount.get(sumOpen) + 1;
                openSumAllToCount.put(sumOpen, openAllCount);
            }
            System.out.println(date + "\t" + (int) init);
        }

//        for (Integer open : openSumAllToCount.keySet()) {
//            Integer allCount = openSumAllToCount.get(open);
//            Integer count = openSumToCount.get(open);
//            if (count != null) {
//                System.out.println("open=" + open + ", -20 count=" + count + " allCount=" + allCount + " ratio=" + (double) count / (double) allCount);
//            }
//        }
//        System.out.println(openSumToCount);
    }

    public static double ibkrFee(double callBuy, double callSell, double putBuy, double putSell, int count) {
        // 买入call佣金
        double buyCallCommission;
        if (callBuy < 0.05) {
            buyCallCommission = 0.25 * count;
        } else if (callBuy >= 0.05 && callBuy < 0.1) {
            buyCallCommission = 0.5;
        } else {
            buyCallCommission = 0.65;
        }
        // 买入put佣金
        double buyPutCommission;
        if (putBuy < 0.05) {
            buyPutCommission = 0.25 * count;
        } else if (putBuy >= 0.05 && putBuy < 0.1) {
            buyPutCommission = 0.5;
        } else {
            buyPutCommission = 0.65;
        }
        // 买入期权监管费
        double buyMonitor = count * 0.02685 * 2;
        // 买入期权清算费
        double buyClear = (count * 0.02 > 55 ? 55 : count * 0.02) * 2;

        // 卖出call佣金
        double sellCallCommission;
        if (callSell < 0.05) {
            sellCallCommission = 0.25 * count;
        } else if (callSell >= 0.05 && callSell < 0.1) {
            sellCallCommission = 0.5;
        } else {
            sellCallCommission = 0.65;
        }
        // 卖出put佣金
        double sellPutCommission;
        if (putSell < 0.05) {
            sellPutCommission = 0.25 * count;
        } else if (putSell >= 0.05 && putSell < 0.1) {
            sellPutCommission = 0.5;
        } else {
            sellPutCommission = 0.65;
        }
        // 卖出期权监管费
        double sellMonitor = count * 0.02685 * 2;
        // 卖出期权清算费
        double sellClear = (count * 0.02 > 55 ? 55 : count * 0.02) * 2;
        // 证监会规费
        double sellSRC = 0.0000278 * count * callSell * 100 + 0.0000278 * count * putSell * 100;
        // 交易活动费
        double sellActivity = 0.00279 * count * 2;

        double fee = buyCallCommission + buyPutCommission + buyMonitor + buyClear + sellCallCommission + sellPutCommission + sellMonitor + sellClear + sellSRC + sellActivity;
        return fee;
    }

    public static double futuFee(double callBuy, double callSell, double putBuy, double putSell, int count) {
        // 买入call佣金
        double buyCallCommission = callBuy <= 0.1 ? 0.15 * count : 0.65 * count;
        // 买入put佣金
        double buyPutCommission = putBuy <= 0.1 ? 0.15 * count : 0.65 * count;
        // 买入平台使用费
        double buyPlatform = count * 0.3 * 2;
        // 买入期权监管费
        double buyMonitor = count * 0.012 * 2;
        // 买入期权清算费
        double buyClear = (count * 0.02 > 55 ? 55 : count * 0.02) * 2;
        // 买入期权交收费
        double buyDelivery = count * 0.18 * 2;

        // 卖出call佣金
        double sellCallCommission = callSell <= 0.1 ? 0.15 * count : 0.65 * count;
        // 卖出put佣金
        double sellPutCommission = putSell <= 0.1 ? 0.15 * count : 0.65 * count;
        // 卖出平台使用费
        double sellPlatform = count * 0.3 * 2;
        // 卖出期权监管费
        double sellMonitor = count * 0.012 * 2;
        // 卖出期权清算费
        double sellClear = (count * 0.02 > 55 ? 55 : count * 0.02) * 2;
        // 卖出期权交收费
        double sellDelivery = count * 0.18 * 2;
        // 证监会规费
        double sellSRC = 0.0000278 * count * callSell * 100 < 0.01 ? 0.01 : 0.0000278 * count * callSell * 100 + 0.0000278 * count * putSell * 100 < 0.01 ? 0.01 : 0.0000278 * count * putSell * 100;
        // 交易活动费
        double sellActivity = (0.00279 * count < 0.01 ? 0.01 : 0.00279 * count) * 2;

        double fee = buyCallCommission + buyPutCommission + buyPlatform + buyMonitor + buyClear + buyDelivery + sellCallCommission + sellPutCommission + sellPlatform + sellMonitor + sellClear + sellDelivery + sellSRC + sellActivity;
        return fee;
    }

    public static void main(String[] args) throws Exception {
        //        deleteFile();
//        calCallWithProtect();
//        System.out.println();
        calGainWithHandleFee();

        int week = 12;
        double iit = 10000;
        for (int i = 0; i < week; i++) {
            iit = iit * 1.1;
            //            System.out.println(i + 1 + "\t" + iit);
        }

        List<Double> list = Lists.newArrayList();
        list.add(16.06557377);
        list.add(9.016393443);
        list.add(54.85714286);
        list.add(-3.276955603);
        list.add(1.111111111);
        list.add(-18.71921182);
        list.add(-12.69035533);
        list.add(7.5);
        list.add(181.4285714);
        list.add(12.24489796);
        list.add(-22.33009709);
        double init = 10000;
        int i = 1;
        for (Double l : list) {
            l = l / 100;
            //            double lossRatio = -0.01;
            //            if (l < lossRatio) {
            //                l = lossRatio;
            //            }
            //            init = init * 0.1 * (1 + l) + init * 0.9;
            init = init * (1 + l);
            //            System.out.println(i++ + "\t" + init);
        }
        //        System.out.println(init);
    }

}
