package luonq.data;

import bean.Page;
import bean.StockRehab;
import bean.Total;
import bean.TradeCalendar;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import luonq.mapper.EarningDataMapper;
import luonq.mapper.RehabDataMapper;
import luonq.mapper.StockDataMapper;
import luonq.mapper.TradeCalendarMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@Slf4j
public class ReadFromDB {

    @Autowired
    private StockDataMapper stockDataMapper;

    @Autowired
    private EarningDataMapper earningDataMapper;

    @Autowired
    private RehabDataMapper rehabDataMapper;

    @Autowired
    private TradeCalendarMapper tradeCalendarMapper;

    public List<Total> getAllYearDate(String dbYear) {
        Page page = new Page();
        List<Total> allTotals = Lists.newLinkedList();
        while (true) {
            List<Total> totals = stockDataMapper.queryForAllYear(dbYear, page);
            int size = totals.size();
            if (size == 0) {
                break;
            }
            page.setId(totals.get(size - 1).getId());
            allTotals.addAll(totals);
        }
        return allTotals;
    }

    public List<Total> getCodeDate(String dbYear, String code, String dateOrderType) {
        if (StringUtils.isBlank(dateOrderType)) {
            dateOrderType = "asc";
        }
        return stockDataMapper.queryByCode(dbYear, code, dateOrderType);
    }

    public List<String> getStockForEarning(String date) {
        return earningDataMapper.queryEarningByActualDate(date);
    }

    public List<String> getAllStock(int year, String date) {
        return stockDataMapper.queryStockList(String.valueOf(year), date);
    }

    public List<Total> getAllStockData(int year, String date) {
        return stockDataMapper.queryStockDataList(String.valueOf(year), date);
    }

    public List<Total> batchGetStockData(int year, String date, List<String> stocks) {
        return stockDataMapper.batchQueryStockData(String.valueOf(year), date, stocks);
    }

    public StockRehab getLatestRehab(String code) {
        return rehabDataMapper.queryLatestRehab(code);
    }

    public TradeCalendar getTradeCalendar(String tradeDay) {
        return tradeCalendarMapper.queryTradeCalendar(tradeDay);
    }

    public TradeCalendar getLastTradeCalendar(String tradeDay) {
        return tradeCalendarMapper.queryLastTradeCalendar(tradeDay);
    }

    public List<TradeCalendar> getLastNTradeCalendar(String tradeDay, int N) {
        return tradeCalendarMapper.queryLastNTradeCalendar(tradeDay, N);
    }

    public TradeCalendar getNextTradeCalendar(String tradeDay) {
        return tradeCalendarMapper.queryNextTradeCalendar(tradeDay);
    }

    /**
     * 所有在库中有日线记录的股票代码（按年表汇总，有序）。
     */
    public List<String> listAllStockCodes() {
        List<String> years = stockDataMapper.listStockDataYears();
        if (years == null || years.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> codes = new LinkedHashSet<>();
        for (String year : years) {
            List<String> part = stockDataMapper.listDistinctCodesForYear(year);
            if (part != null) {
                codes.addAll(part);
            }
        }
        List<String> sorted = new ArrayList<>(codes);
        sorted.sort(Comparator.naturalOrder());
        return sorted;
    }

    /**
     * 某只股票日 K（跨年份分表），按日期升序。{@code from}、{@code to} 为 yyyy-MM-dd，闭区间，可空。
     */
    public List<Total> queryDailyByCode(String code, String from, String to) {
        List<String> years = stockDataMapper.listStockDataYears();
        if (years == null || years.isEmpty()) {
            return Collections.emptyList();
        }
        int yMin = Integer.parseInt(years.get(0));
        int yMax = Integer.parseInt(years.get(years.size() - 1));
        int yFrom = yMin;
        int yTo = yMax;
        if (from != null && from.length() >= 4) {
            yFrom = Math.max(yFrom, Integer.parseInt(from.substring(0, 4)));
        }
        if (to != null && to.length() >= 4) {
            yTo = Math.min(yTo, Integer.parseInt(to.substring(0, 4)));
        }
        if (yFrom > yTo) {
            return Collections.emptyList();
        }
        List<Total> merged = new ArrayList<>();
        for (String year : years) {
            int y = Integer.parseInt(year);
            if (y < yFrom || y > yTo) {
                continue;
            }
            List<Total> chunk = stockDataMapper.queryByCode(year, code, "asc");
            if (chunk == null || chunk.isEmpty()) {
                continue;
            }
            for (Total t : chunk) {
                String d = t.getDate();
                if (from != null && d.compareTo(from) < 0) {
                    continue;
                }
                if (to != null && d.compareTo(to) > 0) {
                    continue;
                }
                merged.add(t);
            }
        }
        merged.sort(Comparator.comparing(Total::getDate));
        return merged;
    }

    /**
     * 最新 {@code limit} 个交易日（按 date 升序）。从最近年份倒序扫表，性能优于全表合并再截断。
     *
     * @param toInclusive 若非空，只包含 date ≤ toInclusive 的行
     */
    public List<Total> queryLatestDaily(String code, int limit, String toInclusive) {
        if (limit <= 0 || StringUtils.isBlank(code)) {
            return Collections.emptyList();
        }
        List<String> years = stockDataMapper.listStockDataYears();
        if (years == null || years.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> yrev = new ArrayList<>(years);
        Collections.reverse(yrev);
        List<Total> got = new ArrayList<>();
        for (String year : yrev) {
            List<Total> chunk = stockDataMapper.queryByCode(year, code, "desc");
            if (chunk == null || chunk.isEmpty()) {
                continue;
            }
            for (Total t : chunk) {
                String d = t.getDate();
                if (StringUtils.isBlank(d)) {
                    continue;
                }
                if (toInclusive != null && !toInclusive.isEmpty() && d.compareTo(toInclusive) > 0) {
                    continue;
                }
                got.add(t);
                if (got.size() >= limit) {
                    break;
                }
            }
            if (got.size() >= limit) {
                break;
            }
        }
        Collections.reverse(got);
        return got;
    }

    /**
     * 严格早于 {@code beforeExclusive}（yyyy-MM-dd）的最近 {@code limit} 个交易日，按 date 升序。
     */
    public List<Total> queryDailyBeforeExclusive(String code, String beforeExclusive, int limit) {
        if (limit <= 0 || StringUtils.isBlank(code) || StringUtils.isBlank(beforeExclusive)) {
            return Collections.emptyList();
        }
        List<String> years = stockDataMapper.listStockDataYears();
        if (years == null || years.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> yrev = new ArrayList<>(years);
        Collections.reverse(yrev);
        List<Total> got = new ArrayList<>();
        for (String year : yrev) {
            List<Total> chunk = stockDataMapper.queryByCode(year, code, "desc");
            if (chunk == null || chunk.isEmpty()) {
                continue;
            }
            for (Total t : chunk) {
                String d = t.getDate();
                if (StringUtils.isBlank(d)) {
                    continue;
                }
                if (d.compareTo(beforeExclusive) >= 0) {
                    continue;
                }
                got.add(t);
                if (got.size() >= limit) {
                    break;
                }
            }
            if (got.size() >= limit) {
                break;
            }
        }
        Collections.reverse(got);
        return got;
    }
}
