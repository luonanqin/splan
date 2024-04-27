package luonq.strategy;

import util.BaseUtils;
import util.Constants;

import java.io.File;
import java.util.List;

/**
 * 测试SQ 2023年数据
 * 看开盘后半小时的价格（卖出价），是否是当前的最低价。
 * 如果是则看收盘（买入价）是否小于这个价格，如果小于则说明这个点的价格确定了日内下跌的趋势
 * PS：可以计算趋势概率，应用到所有股票上作为筛选条件
 * <p>
 * 几个止损策略：
 * 1.半小时后的价格之后如果回撤到半小时前的最高点，则说明日内下跌概率低，可止损
 * 2.半小时后的价格之后如果回撤到一定比例，则直接止损（可能价格未到1中说的前最高点，因为前半小时可能跌幅很大，回撤到半小时前最高损失较大）
 * <p>
 * 可调参数：开盘后的时间。目前半小时是大概估计的时间，按照经验开盘后半小时比较能确定日内趋势
 */
public class Strategy23 {

    public static void main(String[] args) throws Exception {
        String stock = "CMRE";
        String path = Constants.HIS_BASE_PATH + "minAggregate/" + stock;
        File dir = new File(path);
        File[] files = dir.listFiles();

        for (File file : files) {
            String date = file.getName();
            List<String> lines = BaseUtils.readFile(file);
            double temp = Double.MAX_VALUE;
            boolean flag = false;
            // 2023-01-03 22:31:00	67.1001
            int count = 30;
            for (int i = 0; i < count; i++) {
                String line = lines.get(i);
                String[] split = line.split("\t");
                Double price = Double.valueOf(split[1]);
                if (price < temp) {
                    temp = price;
                    if (i == count - 1) {
                        flag = true;
                    }
                }
            }

            if (!flag) {
                continue;
            }

            String lastLine = lines.get(lines.size() - 1);
            Double close = Double.valueOf(lastLine.split("\t")[1]);
            System.out.println(date + "\t" + temp + "\t" + close + "\t" + (close < temp));
        }
    }
}
