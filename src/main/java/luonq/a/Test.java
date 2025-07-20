package luonq.a;

import com.google.common.math.Stats;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;

public class Test {

    public static void main(String[] args) {
        StandardDeviation stdev = new StandardDeviation();
        double[] a = {54097180,55739450,55739450,55739450,70338467,62780180,82004114,46657700,37386664,32757778,24854180};
//        double[] a = {10000000,10000000,10000000,10000000,10000000,10000000,10000000,10000000,10000000,10000000,10000000};
        // 每周前几天开盘价的标准差
        double openDiffStd = stdev.evaluate(a);
        System.out.println(openDiffStd);

        Stats stats = Stats.of(54097180, 55739450, 55739450, 55739450, 70338467, 62780180, 82004114, 46657700, 37386664, 32757778, 24854180);
        double avg = stats.mean();
        openDiffStd = stdev.evaluate(new double[]{avg, 51647450});
        System.out.println(openDiffStd);
    }
}
