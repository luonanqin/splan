package luonq.stock;

import bean.StockKLine;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static util.Constants.FORMATTER;
import static util.Constants.GRAB_ONE_YEAR_PATH;

/**
 * Created by Luonanqin on 2023/1/31.
 */
public class FilterStock {

    public static void main(String[] args) throws Exception {
        List<String> list = tradeFlat(GRAB_ONE_YEAR_PATH);
        System.out.println(list);
    }

    public static boolean strongFilter = true;

    public static List<String> tradeFlat(String path) throws Exception {
        File stockFile = new File(path);
        File[] files = stockFile.listFiles();

        List<String> filted = Lists.newArrayList();
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            String fileName = file.getName();

            int _index = fileName.indexOf("_");
            String code = fileName;
            if (_index > -1) {
                code = fileName.substring(0, _index).toUpperCase();
            }

            //            System.out.println(code);
            //            if (code.equals("BABA")) {
            //                System.out.println();
            //            }

            List<StockKLine> dataList = getStockDailyForDays(file, 60);
            //            double avgChangePnt = calAvgChangePnt(dataList);
            double avgVolumn = calAvgVolumn(dataList);

            //            if (avgChangePnt < 0.6) {
            //                System.out.println("avgChangePnt: " + code);
            //            }
            if (avgVolumn < 1000000) {
                //                System.out.println("avgVolumn: " + code);
                filted.add(code.toUpperCase());
            }
        }
        return filted;
    }

    public static List<StockKLine> getStockDailyForDays(File file, int days) throws Exception {
        //        System.out.println(file.getName());
        BufferedReader br = new BufferedReader(new FileReader(file));
        String data;
        br.readLine(); // table head

        List<StockKLine> dataList = Lists.newArrayList();
        int count = 0;
        LocalDate init = LocalDate.parse("12/30/2022", FORMATTER);
        while (StringUtils.isNotBlank(data = br.readLine())) {
            String[] split = data.split(",");
            String date = split[0];
            if (count == 0) {
                LocalDate cur = LocalDate.parse(date, FORMATTER);
                if (cur.isBefore(init)) {
                    count = 1;
                }
            } else if (count > 0 && split.length >= 6) {
                //                String changePnt = split[6];
                //                String volumn = split[6];
                String volumn = "0";
                if (split.length == 7) {
                    volumn = split[6];
                } else if (split.length == 8) {
                    volumn = split[7];
                } else if (split.length == 9) {
                    volumn = split[7];
                }

                StockKLine stockDaily = new StockKLine();
                //                stockDaily.setChangePnt(Double.valueOf(changePnt.substring(0, changePnt.length() - 1)));
                stockDaily.setVolume(BigDecimal.valueOf(Double.valueOf(volumn)));

                dataList.add(stockDaily);

                count++;
            }

            if (count > days) {
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
        double max = Double.MIN_VALUE;
        for (StockKLine data : dataList) {
            sum = sum.add(data.getVolume());
            double v = data.getVolume().doubleValue();
            if (v > max) {
                max = v;
            }
        }
        if (sum.equals(BigDecimal.ZERO)) {
            return 0;
        }

        //        System.out.println(sum.toString()+" "+ dataList.size());
        int size = dataList.size();
        if (strongFilter) {
            sum = sum.subtract(BigDecimal.valueOf(max));
            size--;
            if (size == 0) {
                return 0;
            }
        }
        return sum.divide(BigDecimal.valueOf(size), BigDecimal.ROUND_HALF_UP).doubleValue();
    }
}
