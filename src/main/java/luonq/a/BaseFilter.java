package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Maps;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

public class BaseFilter {

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

            if (stockKLines.size() - i > 50) {
                continue;
            }
            avgVolMap.put(kLine.getDate(), avgVol);
        }

        return avgVolMap;
    }
}
