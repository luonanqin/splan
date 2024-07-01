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
        Map<String, List<Double>> dateToCallP = Maps.newTreeMap(Comparator.comparing(BaseUtils::formatDateToInt));
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
            Double avgRatio = doubles.stream().collect(Collectors.averagingDouble(d -> d));
            init = init * (1 + avgRatio / 100);
            System.out.println(date + "\t" + init);
        }
    }

    public static void main(String[] args) throws Exception {
        //        deleteFile();
        calCallWithProtect();

        int week = 12;
        double iit = 10000;
        for (int i = 0; i < week; i++) {
            iit = iit * 1.1;
            //                        System.out.println(i + 1 + "\t" + iit);
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
