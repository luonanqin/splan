package luonq.mapper;

import bean.StockRehab;

import java.util.List;

public interface RehabDataMapper {

    /**
     * 批量插入复权数据
     */
    void batchInsertRehab(List<StockRehab> list);

    /**
     * 查询某只股票最新的复权数据
     */
    StockRehab queryLatestRehab(String code);
}
