package bean;

import lombok.Data;

import java.util.List;
import java.util.stream.Collectors;

@Data
public class ContinueRise {
    private String stock;
    private List<StockKLine> riseList;
    private StockKLine prev;
    private BOLL currBoll;

    public String getFirstDate() {
        return riseList.get(0).getDate();
    }

    public String getBuyDate() {
        return riseList.get(2).getDate();
    }

    public boolean getResult(int n) {
        if (n + 1 > riseList.size()) {
            return false;
        }

        StockKLine last = riseList.get(n);
        StockKLine prev = riseList.get(n - 1);
        return last.getClose() > prev.getClose();
    }

    public double getRatio() {
        StockKLine last = riseList.get(3);
        StockKLine prev = riseList.get(2 - 1);
        double lastClose = last.getClose();
        double prevClose = prev.getClose();
        return lastClose / prevClose;
    }

    public String toString() {
        String closeDetail = riseList.stream().map(k -> String.valueOf(k.getClose())).collect(Collectors.joining("\t"));
        String volDetail = riseList.stream().map(k -> String.valueOf(k.getVolume())).collect(Collectors.joining("\t"));
        return getBuyDate() + "\tstock=" + stock + "\t, close=\t" + closeDetail + "\tvolume=" + volDetail + ",\t" + getResult(riseList.size() - 1);
    }
}
