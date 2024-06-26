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

import java.util.List;

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
}
