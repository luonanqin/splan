package stock;

import bean.StockKLine;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.math.BigDecimal;
import java.util.List;

/**
 * Created by Luonanqin on 2023/1/31.
 */
public class FilterStock {

    public static void main(String[] args) throws Exception {
        File stockFile = new File("src/main/resources/historicalData/stock");
        File[] files = stockFile.listFiles();

        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            String fileName = file.getName();

            int _index = fileName.indexOf("_");
            String code = fileName.substring(0, _index);

            //            System.out.println(code);

            List<StockKLine> dataList = getTwoMonthStockDaily(file);
            double avgChangePnt = calAvgChangePnt(dataList);
            double avgVolumn = calAvgVolumn(dataList);

            if (avgChangePnt < 0.6) {
                System.out.println("avgChangePnt: " + code);
            }
            if (avgVolumn < 100000) {
                System.out.println("avgVolumn: " + code);
            }
        }

    }

    public static List<StockKLine> getTwoMonthStockDaily(File file) throws Exception {
        //        System.out.println(file.getName());
        BufferedReader br = new BufferedReader(new FileReader(file));
        String data;
        br.readLine(); // table head

        List<StockKLine> dataList = Lists.newArrayList();
        int count = 0;
        while (StringUtils.isNotBlank(data = br.readLine())) {
            String[] split = data.split(",");
            String date = split[0];
            if ("12/30/2022".equals(date)) {
                count = 1;
            }

            if (count > 0 && split.length == 8) {
                String changePnt = split[6];
                String volumn = split[7];

                StockKLine stockDaily = new StockKLine();
                stockDaily.setChangePnt(Double.valueOf(changePnt.substring(0, changePnt.length() - 1)));
                stockDaily.setVolume(BigDecimal.valueOf(Double.valueOf(volumn)));

                dataList.add(stockDaily);

                count++;
            }

            if (count > 40) {
                break;
            }
        }
        br.close();

        return dataList;
    }

    public static double calAvgChangePnt(List<StockKLine> dataList) {
        double sum = 0;
        for (StockKLine data : dataList) {
            sum += Math.abs(data.getChangePnt());
        }

        return sum / dataList.size();
    }

    public static double calAvgVolumn(List<StockKLine> dataList) {
        BigDecimal sum = BigDecimal.ZERO;
        for (StockKLine data : dataList) {
            sum.add(data.getVolume());
        }

        return sum.divide(BigDecimal.valueOf(dataList.size())).doubleValue();
    }
}
