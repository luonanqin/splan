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

    /**
     * 全部交易日（yyyy-MM-dd），与 Python massive_day_aggregates 脚本一致。
     */
    List<String> listAllTradeDatesOrderByDate();

    /**
     * 闭区间内最早一条交易日（yyyy-MM-dd）；区间内无记录时返回 null。
     */
    String selectMinTradingDateBetween(
            @Param("fromInclusive") String fromInclusive, @Param("toInclusive") String toInclusive);
}
