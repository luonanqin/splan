package luonq.mapper;

import bean.OptionTradeRecord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OptionTradeRecordMapper {

    int insert(OptionTradeRecord row);

    OptionTradeRecord selectById(@Param("id") long id);

    List<OptionTradeRecord> listByUnderlyingCode(@Param("underlyingCode") String underlyingCode);

    int updateById(OptionTradeRecord row);

    int deleteById(@Param("id") long id);
}
