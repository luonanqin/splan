package luonq.service;

import bean.Total;
import luonq.mapper.StockDataMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import util.Constants;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 年分表日线 OHLC 读取（{@link StockDataMapper}），供 MA/BOLL/聚合等非「均线计算」模块复用。
 */
@Service
public class StockDailyDataService {

    private static final int MIN_YEAR = 2022;
    private static final LocalDate MIN_DATE = LocalDate.of(MIN_YEAR, 1, 1);

    @Autowired
    private StockDataMapper stockDataMapper;

    /**
     * 某标的日线（跨年份分表），按日期升序；{@code startDate} 早于库内下界（与 MA/BOLL 任务一致，自 2022-01-01）时会被抬升到该下界。
     */
    public List<Total> fetchDailiesAsc(String code, LocalDate startDate, LocalDate endDate) {
        if (StringUtils.isBlank(code) || startDate == null || endDate == null) {
            return Collections.emptyList();
        }
        LocalDate effStart = startDate.isBefore(MIN_DATE) ? MIN_DATE : startDate;
        List<String> years = stockDataMapper.listStockDataYears();
        if (years == null || years.isEmpty()) {
            return Collections.emptyList();
        }
        int yStart = effStart.getYear();
        int yEnd = endDate.getYear();
        List<Total> merged = new ArrayList<>();
        for (String y : years) {
            int yi = Integer.parseInt(y);
            if (yi < yStart || yi > yEnd) {
                continue;
            }
            List<Total> chunk = stockDataMapper.queryByCode(y, code.trim(), "asc");
            if (chunk == null || chunk.isEmpty()) {
                continue;
            }
            for (Total t : chunk) {
                if (StringUtils.isBlank(t.getDate())) {
                    continue;
                }
                LocalDate d = LocalDate.parse(t.getDate(), Constants.DB_DATE_FORMATTER);
                if (d.isBefore(effStart) || d.isAfter(endDate)) {
                    continue;
                }
                merged.add(t);
            }
        }
        merged.sort(Comparator.comparing(Total::getDate));
        return merged;
    }
}
