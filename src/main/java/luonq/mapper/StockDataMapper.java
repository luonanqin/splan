package luonq.mapper;

import bean.BOLL;
import bean.MA;
import bean.RealOpenVol;
import bean.SimpleTrade;
import bean.StockKLine;
import bean.Total;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface StockDataMapper {

    /**
     * 批量导入文件数据
     */
    void batchInsertFileData(List<Total> totalList);

    /**
     * 查询股票是否已在库中存在
     */
    int queryStockExistCount(@Param("code") String code, @Param("date") String date);

    /**
     * 日K线
     */
    void updateStockKLine(@Param("kline") StockKLine stockKLine, @Param("date") String date);

    /**
     * 收盘价对应的均线
     */
    void updateMA(@Param("ma") MA ma, @Param("date") String date);

    /**
     * 收盘价对应的布林线
     */
    void updateBOLL(@Param("boll") BOLL boll, @Param("date") String date);

    /**
     * 开盘价对应的布林线
     */
    void updateOpenBOLL(@Param("boll") BOLL boll, @Param("date") String date);

    /**
     * 开盘第一分钟成交均价和总成交量
     */
    void updateF1minTrade(@Param("f1minTrade") RealOpenVol realOpenVol, @Param("date") String date);

    /**
     * 开盘第一笔交易价及交易时间
     */
    void updateOpenTrade(@Param("openTrade") SimpleTrade simpleTrade, @Param("date") String date);
}
