package test;

import bean.StockDaily;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

/**
 * Created by Luonanqin on 2023/2/1.
 */
public class StockDailyTest {

    public static List<StockDaily> getDailyData() {
        String code = "aapl";

        List<StockDaily> dailyList = Lists.newArrayList();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader("src/main/resources/testData/" + code));
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
                double volumn = Double.valueOf(split[7]);

                StockDaily daily = new StockDaily();
                daily.setDate(date);
                daily.setOpen(open);
                daily.setClose(close);
                daily.setHigh(high);
                daily.setLow(low);
                daily.setChange(change);
                daily.setChangePnt(Double.valueOf(changePnt.substring(0, changePnt.length() - 1)));
                daily.setVolumn(BigDecimal.valueOf(volumn));

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
        List<StockDaily> dailyData = getDailyData();
        System.out.println(System.currentTimeMillis());
    }
}
