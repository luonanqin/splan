package luonq.mapper;

import bean.EarningDate;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Created by Luonanqin on 2023/12/3.
 */
public interface EarningDataMapper {

    /**
     * 批量插入财报日历数据
     */
    void batchInsertEarning(List<EarningDate> list);

    /**
     * 返回某天的财报日历
     */
    List<EarningDate> queryEarningByDate(String day);

    /**
     * 返回财报发生在某天的股票列表
     */
    List<String> queryEarningByActualDate(String day);

    /**
     * 删除指定日期的财报日历数据，这个日期对应的实际财报日可能是当日也可能是后一日
     */
    void deleteEarning(@Param("dateList") List<String> dateList);
}
