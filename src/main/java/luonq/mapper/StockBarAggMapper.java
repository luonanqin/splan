package luonq.mapper;

import bean.StockBarAgg;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface StockBarAggMapper {

    String selectMaxBarDate(@Param("code") String code, @Param("periodType") String periodType);

    StockBarAgg selectByCodePeriodBarDate(
            @Param("code") String code,
            @Param("periodType") String periodType,
            @Param("barDate") String barDate);

    void batchUpsertStockBarAgg(List<StockBarAgg> list);

    List<StockBarAgg> selectByCodePeriodBetween(
            @Param("code") String code,
            @Param("periodType") String periodType,
            @Param("fromInclusive") String fromInclusive,
            @Param("toInclusive") String toInclusive);

    List<StockBarAgg> selectLatestByCodePeriod(
            @Param("code") String code,
            @Param("periodType") String periodType,
            @Param("limit") int limit,
            @Param("toInclusive") String toInclusive);

    List<StockBarAgg> selectBeforeExclusive(
            @Param("code") String code,
            @Param("periodType") String periodType,
            @Param("beforeExclusive") String beforeExclusive,
            @Param("limit") int limit);
}
