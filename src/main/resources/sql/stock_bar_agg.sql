-- 周/月/季聚合 K 线（由日线聚合）
-- period_type: week | month | quarter
-- bar_date: 周期锚点（周线见 trade_calendar 当周首个交易日；月/季为自然月或季首日）
-- first_trade_date: 该周期第一个交易日，便于 K 线图展示周期起点
-- last_trade_date: 本周期已聚合到的最后交易日，增量时可仅从该日之后合并日线

CREATE TABLE IF NOT EXISTS `stock_bar_agg` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `code` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '股票代码',
    `period_type` varchar(10) NOT NULL DEFAULT '' COMMENT 'week=周线 month=月线 quarter=季线',
    `bar_date` varchar(10) NOT NULL DEFAULT '' COMMENT '周期锚点 yyyy-MM-dd',
    `first_trade_date` varchar(10) NOT NULL DEFAULT '' COMMENT '该周期第一个交易日 yyyy-MM-dd，K线图上标示周期起点',
    `last_trade_date` varchar(10) NOT NULL DEFAULT '' COMMENT '本周期已聚合到的最后交易日 yyyy-MM-dd',
    `open` decimal(20,5) NOT NULL DEFAULT 0.00000 COMMENT '周期内首个交易日开盘价',
    `high` decimal(20,5) NOT NULL DEFAULT 0.00000 COMMENT '周期内最高价',
    `low` decimal(20,5) NOT NULL DEFAULT 0.00000 COMMENT '周期内最低价',
    `close` decimal(20,5) NOT NULL DEFAULT 0.00000 COMMENT '周期内最后交易日收盘价',
    `volume` bigint NOT NULL DEFAULT 0 COMMENT '周期内成交量合计',
    `create_time` datetime NOT NULL COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code_period_bar_date` (`code`, `period_type`, `bar_date`),
    KEY `idx_code_period_bar_date` (`code`, `period_type`, `bar_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='周/月/季聚合K线（由日线聚合）';

-- 若表已先建好、仅补字段，可执行：
-- ALTER TABLE `stock_bar_agg`
--     ADD COLUMN `first_trade_date` varchar(10) NOT NULL DEFAULT '' COMMENT '该周期第一个交易日 yyyy-MM-dd，K线图上标示周期起点' AFTER `bar_date`;
-- ALTER TABLE `stock_bar_agg`
--     ADD COLUMN `last_trade_date` varchar(10) NOT NULL DEFAULT '' COMMENT '本周期已聚合到的最后交易日 yyyy-MM-dd' AFTER `first_trade_date`;
