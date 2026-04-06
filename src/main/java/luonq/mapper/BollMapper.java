package luonq.mapper;

import bean.StockBoll;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface BollMapper {

    void batchUpsertBoll(List<StockBoll> list);

    /** yyyy-MM-dd 字典序即时间序 */
    String selectMaxDate(@Param("code") String code, @Param("type") String type);

    /**
     * 图表用：某标的、某周期类型、闭区间日期内的布林行，按 date 升序。
     */
    List<StockBoll> selectByCodeTypeBetween(
            @Param("code") String code,
            @Param("type") String type,
            @Param("fromInclusive") String fromInclusive,
            @Param("toInclusive") String toInclusive);
}
