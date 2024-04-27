package luonq.mapper;

import bean.TradeCalendar;

import java.util.List;

public interface TradeCalendarMapper {

    /**
     * 批量插入交易日历数据
     */
    void batchInsertTradeCalendar(List<TradeCalendar> list);

    /**
     * 查询某天的交易日历信息
     */
    TradeCalendar queryTradeCalendar(String tradeDate);
}
