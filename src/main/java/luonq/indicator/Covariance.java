package luonq.indicator;

import com.google.common.collect.Lists;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by Luonanqin on 2023/3/24.
 */
public class Covariance {

    public static void main(String[] args) {
        List<Double> closeList = Lists.newArrayList(176.15, 173.71, 173.39, 172.17, 170.99, 169.34, 165.82, 167.81, 165.85, 167.19, 166.59, 162.41, 163.59, 163.63, 157.64, 157.33, 153.085, 155.04, 156.28, 155.57, 153.72, 151.23, 151.57, 150.86, 148.95, 146.45, 148.45, 146.64, 147.55);
        List<Double> upList = Lists.newArrayList(177.389, 176.057, 174.908, 173.801, 172.158, 171.138, 169.931, 169.226, 168.345, 166.917, 165.102, 163.067, 162.301, 161.176, 159.619, 158.602, 157.589, 157.022, 156.134, 155.004, 153.531, 152.234, 151.669, 151.754, 150.406, 149.375, 148.811, 147.846, 147.476);

        Double closeAvg = closeList.stream().collect(Collectors.averagingDouble(a -> a));
        Double upAvg = upList.stream().collect(Collectors.averagingDouble(a -> a));

        double sum = 0;
        int size = closeList.size();
        for (int i = 0; i < size; i++) {
            Double close = closeList.get(i);
            Double up = upList.get(i);

            sum = sum + (close - closeAvg) * (up - upAvg);
        }

        double closeDiffSum = 0;
        for (Double close : closeList) {
            closeDiffSum = closeDiffSum + Math.pow(close - closeAvg, 2);
        }
        double varClose = closeDiffSum / size;

        double upDiffSum = 0;
        for (Double up : upList) {
            upDiffSum = upDiffSum + Math.pow(up - upAvg, 2);
        }
        double varUp = upDiffSum / size;

        double cov = sum / size;
        double rou = cov / Math.sqrt(varClose * varUp);
        System.out.println(cov);
        System.out.println(rou);
    }
}
