package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Maps;
import util.Constants;
import util.LoadData;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public abstract class BaseFilter {

    public List<String> cal() {
        return cal(0, null);
    }

    public void testOne(int day, String testCode) {
        cal(day, testCode);
    }

    public void test(int days) {
        for (int i = 0; i < days; i++) {
            System.out.println("days: " + i);
            cal(i, null);
        }
    }

    public abstract List<String> cal(int prevDays, String testCode);

    public static Map<String/* date */, BigDecimal> cal50volMa(List<StockKLine> stockKLines) {
        BigDecimal vol = BigDecimal.ZERO;
        Map<String/* date */, BigDecimal> avgVolMap = Maps.newHashMap();
        for (int i = 0; i < stockKLines.size(); i++) {
            StockKLine kLine = stockKLines.get(i);
            BigDecimal volume = kLine.getVolume();
            vol = vol.add(volume);

            if (i < 50) {
                continue;
            }

            BigDecimal avgVol = vol.divide(BigDecimal.valueOf(50), 0, RoundingMode.HALF_UP);
            vol = vol.subtract(stockKLines.get(i - 50).getVolume());

            //            if (stockKLines.size() - i > 50) {
            //                continue;
            //            }
            avgVolMap.put(kLine.getDate(), avgVol);
        }

        return avgVolMap;
    }

    public static int fixTemp(List<StockKLine> stockKLines, int temp) {
        int index = stockKLines.size() - 1 - temp;
        if (index < 0) {
            temp = 0;
            return temp;
        }
        StockKLine tempKLine = stockKLines.get(index);
        String tempDate = tempKLine.getDate();
        LocalDate tempLocalDate = LocalDate.parse(tempDate, Constants.DB_DATE_FORMATTER);
        for (String date : LoadData.excludeDate) {
            if (!tempLocalDate.isAfter(LocalDate.parse(date, Constants.DB_DATE_FORMATTER))) {
                temp--;
            }
        }
        if (temp < 0) {
            temp = 0;
        }
        return temp;
    }
}
