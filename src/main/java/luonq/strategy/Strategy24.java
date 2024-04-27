package luonq.strategy;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import util.BaseUtils;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 1.读取每个股票的分钟线数据
 * 2.计算在开盘后30分钟时买入且收盘卖出的成功率，即按照Strategy22计算
 * 3.验证2024年同样也是满足交易条件的股票，按照之前计算的成功率倒排，选第一个进行交易，计算收益
 * <p>
 * 策略改进备选方案：
 * 1.可选开盘一小时内，每一分钟都计算下成功率，取成功率最高的时间
 * 2.同一时刻的股票可以作为这个时刻成功率最高的一组股票
 * 3.当实际交易时，有股票满足交易条件并且在这一组股票中出现时，取这些股票中成功率最高的进行交易
 * 4.有个问题：满足3.的股票可能在不同时刻分别出现，比如在5min/10min/15min分别各出现3只/1只/5只，
 * 可能需要考虑每个时刻的股票数量， 股票数>=3时才能进行交易，当然这得结合实际测试的情况做决定，暂定3
 */
public class Strategy24 {

    @Data
    public static class StockMin {
        private String stock;
        private double buyPrice;
        private double closePrice;
        private double afterBuyHigh;
        private double afterBuyLow;

        public String toString() {
            return String.format("%s\tbuy=%.2f\tclose=%.2f\thigh=%.2f\tlow=%.2f", stock, buyPrice, closePrice, afterBuyHigh, afterBuyLow);
        }
    }

    public static void main(String[] args) throws Exception {
        double init = 10000;
        Map<String, List<StockMin>> _2024DataMap = get2024Data();
        Map<String, Double> stockRatioMap = getStockRatioMap();

        for (String date : _2024DataMap.keySet()) {
            List<StockMin> stockMins = _2024DataMap.get(date);
            stockMins = stockMins.stream().filter(s -> stockRatioMap.containsKey(s.getStock())).collect(Collectors.toList());
            stockMins.sort((o1, o2) -> {
                Double ratio1 = stockRatioMap.get(o1.getStock());
                Double ratio2 = stockRatioMap.get(o2.getStock());
                return ratio2.compareTo(ratio1);
            });

            StockMin stockMin = stockMins.get(0);
            double buyPrice = stockMin.getBuyPrice();
            double closePrice = stockMin.getClosePrice();
            int count = (int) (init / buyPrice);
            init = init + (closePrice - buyPrice) * count;

            System.out.println(date + " " + stockMin.toString() + "\t" + init);
        }
    }

    public static Map<String/*date*/, List<StockMin>/*stock*/> get2024Data() throws Exception {
        String path = "/Users/Luonanqin/study/intellij_idea_workspaces/temp/2024";
        File stockDir = new File(path);
        File[] stockFiles = stockDir.listFiles();

        Map<String/*date*/, List<StockMin>/*stock*/> stockRatioMap = Maps.newTreeMap(Comparator.comparingInt(BaseUtils::formatDateToInt));
        for (File stockFile : stockFiles) {
            String stock = stockFile.getName();
            if (!stock.equals("TCBP")) {
                //                continue;
            }

            File[] files = stockFile.listFiles();
            int count = 30;
            if (files == null) {
                System.out.println("empty file: " + stock);
                continue;
            }
            for (File file : files) {
                String date = file.getName();
                List<String> lines = BaseUtils.readFile(file);
                if (CollectionUtils.isEmpty(lines)) {
                    continue;
                }

                double buy = Double.MIN_VALUE;
                boolean flag = false;
                // 2023-01-03 22:31:00	67.1001
                for (int i = 0; i < count; i++) {
                    if (count > lines.size()) {
                        flag = false;
                        break;
                    }
                    String line = lines.get(i);
                    String[] split = line.split("\t");
                    Double price = Double.valueOf(split[1]);
                    if (price > buy) {
                        buy = price;
                        if (i == count - 1 && StringUtils.endsWithAny(split[0], "22:00:00", "23:00:00")) {
                            flag = true;
                        }
                    }
                }

                if (!flag) {
                    continue;
                }
                double high = Double.MIN_VALUE;
                double low = Double.MAX_VALUE;
                for (int i = count; i < lines.size(); i++) {
                    String line = lines.get(i);
                    String[] split = line.split("\t");
                    Double price = Double.valueOf(split[1]);
                    if (price > high) {
                        high = price;
                    }
                    if (price < low) {
                        low = price;
                    }
                }

                String lastLine = lines.get(lines.size() - 1);
                Double close = null;
                try {
                    close = Double.valueOf(lastLine.split("\t")[1]);
                } catch (Exception e) {
                    System.out.println("error: " + stock + " " + date);
                    continue;
                }
                StockMin stockMin = new StockMin();
                stockMin.setBuyPrice(buy);
                stockMin.setClosePrice(close);
                stockMin.setStock(stock);
                stockMin.setAfterBuyHigh(high);
                stockMin.setAfterBuyLow(low);

                if (!stockRatioMap.containsKey(date)) {
                    stockRatioMap.put(date, Lists.newArrayList());
                }
                stockRatioMap.get(date).add(stockMin);
            }
        }

        return stockRatioMap;
    }

    public static Map<String, Double> getStockRatioMap() throws Exception {
        String path = "/Users/Luonanqin/study/intellij_idea_workspaces/temp/2023";
        File stockDir = new File(path);
        File[] stockFiles = stockDir.listFiles();

        Map<String/*stock*/, Double/*ratio*/> stockRatioMap = Maps.newHashMap();
        for (File stockFile : stockFiles) {
            String stock = stockFile.getName();
            if (!stock.equals("TCBP")) {
                //                continue;
            }

            File[] files = stockFile.listFiles();
            int count = 30;
            double success = 0, fail = 0;
            if (files == null) {
                System.out.println("empty file: " + stock);
                continue;
            }
            for (File file : files) {
                List<String> lines = BaseUtils.readFile(file);
                if (CollectionUtils.isEmpty(lines)) {
                    continue;
                }

                double temp = Double.MIN_VALUE;
                boolean flag = false;
                // ex: 2023-01-03 22:31:00	67.1001
                for (int i = 0; i < count; i++) {
                    if (count > lines.size()) {
                        flag = false;
                        break;
                    }
                    String line = lines.get(i);
                    String[] split = line.split("\t");
                    Double price = Double.valueOf(split[1]);
                    if (price > temp) {
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
                Double close = null;
                try {
                    close = Double.valueOf(lastLine.split("\t")[1]);
                } catch (Exception e) {
                    System.out.println("error: " + stock + " " + file.getName());
                    continue;
                }
                if (close > temp) {
                    success++;
                } else {
                    fail++;
                }
                //                System.out.println(date + "\t" + temp + "\t" + close + "\t" + (close > temp));
            }
            if (success == 0 && fail == 0) {
                continue;
            }
            double sum = success + fail;
            double successRatio = success / sum;
            //            System.out.println(stock + ": " + successRatio + " " + sum);
            stockRatioMap.put(stock, successRatio);
        }

        return stockRatioMap;
    }
}
