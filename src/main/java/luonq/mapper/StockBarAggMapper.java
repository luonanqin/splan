package luonq.mapper;

import bean.StockBarAgg;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface StockBarAggMapper {

    String selectMaxBarDate(@Param("code") String code, @Param("periodType") String periodType);

    void batchUpsertStockBarAgg(List<StockBarAgg> list);
}
