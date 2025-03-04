package luonq.strategy;

import bean.Total;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import luonq.data.ReadFromDB;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import util.BaseUtils;
import util.Constants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Component
public class Strategy41 {

    @Autowired
    private ReadFromDB readFromDB;

    private static List<String> years = Lists.newArrayList("2022", "2023", "2024");

    // 过滤出每天流动性适合的股票（总市值大于20亿，三十天成交量大于9亿，前日收盘价大于10）
    public void filter1() throws Exception {
        BigDecimal standardVol = BigDecimal.valueOf(900000000);
        double standardMarketCap = 2000000000;
        double standardPrice = 10;

        for (String year : years) {
            Map<String, String> fileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + year + "/sharing");

            String path = Constants.HIS_BASE_PATH + "/strategy41/liquidity/" + year + "/";
            Map<String/* date */, List<String>> filterMap = Maps.newHashMap();

            for (String stock : fileMap.keySet()) {
                List<String> sharings = BaseUtils.readFile(fileMap.get(stock));
                Map<String/* date */, String> sharingMap = Maps.newHashMap();
                for (String sharing : sharings) {
                    String[] split = sharing.split("\t");
                    sharingMap.put(split[0], sharing);
                }

                List<Total> totals = readFromDB.getCodeDate(year, stock, "asc");

                BigDecimal vol = BigDecimal.ZERO;
                for (int i = 0; i < totals.size(); i++) {
                    Total total = totals.get(i);
                    vol = vol.add(total.getVolume());

                    if (i < 29) {
                        continue;
                    }

                    if (vol.compareTo(standardVol) > 0) {
                        String date = total.getDate();
                        String sharing = sharingMap.get(date);
                        if (StringUtils.isNotBlank(sharing)) {
                            String[] split = sharing.split("\t");
                            double stockCount = Double.valueOf(split[1]);
                            double marketCap = Double.valueOf(split[2]);
                            double price = marketCap / stockCount;

                            if (marketCap > standardMarketCap && price > standardPrice) {
                                if (!filterMap.containsKey(date)) {
                                    filterMap.put(date, Lists.newArrayList());
                                }
                                filterMap.get(date).add(stock);
                            }
                        }
                    }
                    vol = vol.subtract(totals.get(i - 29).getVolume());
                }
            }

            for (String date : filterMap.keySet()) {
                List<String> stocks = filterMap.get(date);
                BaseUtils.writeFile(path + date, stocks);
            }
        }
    }

    // 在filter1的基础上，以开盘价计算5日均线，过滤出开盘大于5日均线的股票
    public void filter2() throws Exception {
        for (String year : years) {
            String filterPath = Constants.HIS_BASE_PATH + "/strategy41/openUpMa5/" + year + "/";
            Map<String/* date */, List<String>> filterMap = Maps.newHashMap();
            Map<String, String> fileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "/strategy41/liquidity/" + year + "/");
            for (String date : fileMap.keySet()) {
                String path = fileMap.get(date);
                List<String> stocks = BaseUtils.readFile(path);

                Map<String/* stock */, List<Total>> stockDataMap = Maps.newHashMap();
                for (String stock : stocks) {
                    List<Total> totals;
                    if (stockDataMap.containsKey(stock)) {
                        totals = stockDataMap.get(stock);
                    } else {
                        totals = readFromDB.getCodeDate(String.valueOf(Integer.valueOf(year) - 1), stock, "asc");
                        totals.addAll(readFromDB.getCodeDate(year, stock, "asc"));
                        stockDataMap.put(stock, totals);
                    }

                    BigDecimal m5close = BigDecimal.ZERO;
                    double ma5 = 0, open = 0;
                    for (int i = 0; i < totals.size(); i++) {
                        if (date.equals(totals.get(i).getDate())) {
                            i = i - 4;
                            if (i < 0) {
                                break;
                            }

                            for (int j = i; j < i + 5; j++) {
                                Total total = totals.get(j);
                                double close = total.getClose();
                                open = total.getOpen();

                                if (j < i + 4) {
                                    m5close = m5close.add(BigDecimal.valueOf(close));
                                } else {
                                    m5close = m5close.add(BigDecimal.valueOf(open));
                                    ma5 = m5close.divide(BigDecimal.valueOf(5), 2, BigDecimal.ROUND_HALF_UP).doubleValue();
                                }
                            }
                            break;
                        }
                    }

                    if (open > ma5) {
                        if (!filterMap.containsKey(date)) {
                            filterMap.put(date, Lists.newArrayList());
                        }
                        filterMap.get(date).add(stock);
                    }
                }
            }

            for (String date : filterMap.keySet()) {
                List<String> stocks = filterMap.get(date);
                BaseUtils.writeFile(filterPath + date, stocks);
            }
        }
    }

    // 在filter2的基础上，以120个交易日为滑动窗口，计算开盘到最高波动大于2%的比例
    public void filter3() throws Exception {
        double standardRatio = 0.02;
        for (String year : years) {
            String filterPath = Constants.HIS_BASE_PATH + "/strategy41/gt2/" + year + "/";
            Map<String, String> fileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "/strategy41/openUpMa5/" + year + "/");
            Map<String/* date */, List<String>> filterMap = Maps.newHashMap();

            for (String date : fileMap.keySet()) {
                String path = fileMap.get(date);
                List<String> stocks = BaseUtils.readFile(path);

                Map<String/* stock */, List<Total>> stockDataMap = Maps.newHashMap();
                for (String stock : stocks) {
                    List<Total> totals;
                    if (stockDataMap.containsKey(stock)) {
                        totals = stockDataMap.get(stock);
                    } else {
                        totals = readFromDB.getCodeDate(String.valueOf(Integer.valueOf(year) - 1), stock, "asc");
                        totals.addAll(readFromDB.getCodeDate(year, stock, "asc"));
                        stockDataMap.put(stock, totals);
                    }

                    double count = 0;
                    for (int i = 0; i < totals.size(); i++) {
                        if (date.equals(totals.get(i).getDate())) {
                            i = i - 120;
                            if (i < 0) {
                                break;
                            }
                            for (int j = i; j < i + 120; j++) {
                                Total total = totals.get(j);
                                double open = total.getOpen();
                                double high = total.getHigh();

                                double ratio = (high - open) / open;
                                if (ratio > standardRatio) {
                                    count++;
                                }
                            }
                            break;
                        }
                    }
                    double res = BigDecimal.valueOf(count / 120 * 100).setScale(2, RoundingMode.HALF_UP).doubleValue();
                    if (!filterMap.containsKey(date)) {
                        filterMap.put(date, Lists.newArrayList());
                    }
                    filterMap.get(date).add(stock + "\t" + res);
                }
            }

            for (String date : filterMap.keySet()) {
                List<String> stocks = filterMap.get(date);
                BaseUtils.writeFile(filterPath + date, stocks);
            }
        }
    }
}
