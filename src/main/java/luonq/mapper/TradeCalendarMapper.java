package luonq.mapper;

import bean.TradeCalendar;
import org.apache.ibatis.annotations.Param;

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

    /**
     * 查询上一交易日历信息
     */
    TradeCalendar queryLastTradeCalendar(String tradeDate);

    /**
     * 查询前面N天的交易日历信息
     */
    List<TradeCalendar> queryLastNTradeCalendar(@Param("tradeDate") String tradeDate, @Param("N") int N);

    /**
     * 查询下一交易日历信息
     */
    TradeCalendar queryNextTradeCalendar(String tradeDate);
}
