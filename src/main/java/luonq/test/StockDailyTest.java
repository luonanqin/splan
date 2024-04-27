package luonq.test;

import bean.StockKLine;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import util.Constants;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

/**
 * Created by Luonanqin on 2023/2/1.
 */
public class StockDailyTest {

    public static List<StockKLine> getDailyData() {
        String code = "aapl_daily";

        List<StockKLine> dailyList = getStockKLine(code);

        return dailyList;
    }

    public static List<StockKLine> getMonthlyData() {
        List<StockKLine> monthly = Lists.newArrayList();

        monthly.add(StockKLine.builder().date("01/03/2023").open(133.88).close(133.41).high(134.26).low(131.44).volume(BigDecimal.valueOf(71379600)).build());
        monthly.add(StockKLine.builder().date("12/01/2022").open(131.25).close(133.49).high(133.51).low(130.46).volume(BigDecimal.valueOf(69458900)).build());
        monthly.add(StockKLine.builder().date("11/01/2022").open(130.26).close(130.73).high(131.2636).low(128.12).volume(BigDecimal.valueOf(63896100)).build());
        monthly.add(StockKLine.builder().date("10/03/2022").open(130.46).close(130.15).high(133.41).low(129.89).volume(BigDecimal.valueOf(70790800)).build());
        monthly.add(StockKLine.builder().date("09/01/2022").open(126.01).close(129.62).high(130.29).low(124.89).volume(BigDecimal.valueOf(87754700)).build());
        monthly.add(StockKLine.builder().date("08/01/2022").open(127.13).close(125.02).high(127.77).low(124.76).volume(BigDecimal.valueOf(80962700)).build());
        monthly.add(StockKLine.builder().date("07/01/2022").open(126.89).close(126.36).high(128.6557).low(125.08).volume(BigDecimal.valueOf(89113600)).build());
        monthly.add(StockKLine.builder().date("06/01/2022").open(130.28).close(125.07).high(130.9).low(124.17).volume(BigDecimal.valueOf(112117400)).build());
        monthly.add(StockKLine.builder().date("05/02/2022").open(128.41).close(129.93).high(129.95).low(127.43).volume(BigDecimal.valueOf(77034200)).build());
        monthly.add(StockKLine.builder().date("04/01/2022").open(127.99).close(129.61).high(130.4814).low(127.73).volume(BigDecimal.valueOf(75703700)).build());
        monthly.add(StockKLine.builder().date("03/01/2022").open(129.67).close(126.04).high(131.0275).low(125.87).volume(BigDecimal.valueOf(85438300)).build());
        monthly.add(StockKLine.builder().date("02/01/2022").open(131.38).close(130.03).high(131.41).low(128.72).volume(BigDecimal.valueOf(69007800)).build());
        monthly.add(StockKLine.builder().date("01/03/2022").open(130.92).close(131.86).high(132.415).low(129.64).volume(BigDecimal.valueOf(63814800)).build());
        monthly.add(StockKLine.builder().date("12/01/2021").open(134.35).close(132.23).high(134.56).low(130.3).volume(BigDecimal.valueOf(77852100)).build());

        monthly = getStockKLine("pep_monthly");

        return monthly;
    }

    private static List<StockKLine> getStockKLine(String code) {
        List<StockKLine> dailyList = Lists.newArrayList();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(Constants.TEST_PATH + code));
            String str;
            br.readLine();
            while (StringUtils.isNotBlank(str = br.readLine())) {
                String[] split = str.split(",");

                String date = split[0];
                double open = Double.valueOf(split[1]);
                double high = Double.valueOf(split[2]);
                double low = Double.valueOf(split[3]);
                double close = Double.valueOf(split[4]);
                double change = Double.valueOf(split[5]);
                String changePnt = split[6];
                double volume = Double.valueOf(split[7]);

                StockKLine daily = new StockKLine();
                daily.setDate(date);
                daily.setOpen(open);
                daily.setClose(close);
                daily.setHigh(high);
                daily.setLow(low);
                daily.setChange(change);
                daily.setChangePnt(Double.valueOf(changePnt.substring(0, changePnt.length() - 1)));
                daily.setVolume(BigDecimal.valueOf(volume));

                dailyList.add(daily);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return dailyList;
    }

    public static void main(String[] args) {
        System.out.println(System.currentTimeMillis());
        List<StockKLine> dailyData = getDailyData();
        System.out.println(System.currentTimeMillis());
    }
}
