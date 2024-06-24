package luonq.mapper;

import bean.BOLL;
import bean.MA;
import bean.Page;
import bean.RealOpenVol;
import bean.SimpleTrade;
import bean.StockKLine;
import bean.Total;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface StockDataMapper {

    /**
     * 建表
     */
    void createTable(String dbYear);

    /**
     * 判断表是否存在
     */
    String showTables(String dbYear);

    /**
     * 初始化股票空行
     */
    @Deprecated
    void initStock(@Param("code") String code, @Param("dbYear") String dbYear);

    /**
     * 批量导入文件数据
     */
    void batchInsertFileData(@Param("list") List<Total> totalList, @Param("dbYear") String dbYear);

    /**
     * 分页批量返回全表数据
     */
    List<Total> queryForAllYear(@Param("dbYear") String dbYear, @Param("page") Page page);

    /**
     * 返回某只股票某年的数据
     */
    List<Total> queryByCode(@Param("dbYear") String dbYear, @Param("code") String code, @Param("dateOrderType") String dateOrderType);

    /**
     * 返回某天的股票列表
     */
    List<String> queryStockList(@Param("dbYear") String dbYear, @Param("date") String date);

    /**
     * 返回某天的股票数据
     */
    List<Total> queryStockDataList(@Param("dbYear") String dbYear, @Param("date") String date);

    /**
     * 批量返回指定日期指定股票的数据
     */
    List<Total> batchQueryStockData(@Param("dbYear") String dbYear, @Param("date") String date, @Param("stocks") List<String> stocks);

    /**
     * 返回某只股票某天的数据
     */
    Total selectByCodeDate(@Param("dbYear") String dbYear, @Param("code") String code, @Param("date") String date);

    /**
     * 查询股票是否已在库中存在
     */
    int queryStockExistCount(@Param("code") String code, @Param("dbYear") String dbYear);

    /**
     * 日K线
     */
    //    void updateStockKLine(@Param("kline") StockKLine stockKLine, @Param("dbYear") String dbYear);
    void updateStockKLine(StockKLine stockKLine);

    /**
     * 收盘价对应的均线
     */
    void updateMA(@Param("ma") MA ma, @Param("dbYear") String dbYear);

    /**
     * 收盘价对应的布林线
     */
    void updateBOLL(@Param("boll") BOLL boll, @Param("dbYear") String dbYear);

    /**
     * 开盘价对应的布林线
     */
    void updateOpenBOLL(@Param("boll") BOLL boll, @Param("dbYear") String dbYear);

    /**
     * 开盘第一分钟成交均价和总成交量
     */
    void updateF1minTrade(@Param("f1minTrade") RealOpenVol realOpenVol, @Param("dbYear") String dbYear);

    /**
     * 开盘第一笔交易价及交易时间
     */
    void updateOpenTrade(@Param("openTrade") SimpleTrade simpleTrade, @Param("dbYear") String dbYear);
}
