-- 布林线（BOLL），与 `ma` 表维度一致：按 code + 锚点交易日 + 周期类型
-- type: day | week | month | quarter（与 ma 表 type 语义一致）
-- md/mb/up/dn 对应 bean.BOLL；中轨 mb 一般为 N 周期均线，md 为同周期收盘价标准差
-- 锚点 date：日线为当日；周/月/季为「该周期内最后一个交易日」，与 stock_bar_agg.bar_date 一致，表示「到该日为止的这一段周期」。
--   图表若要把柱子/点画在周期首日，横轴请用 stock_bar_agg.first_trade_date（或等价字段）关联展示，与存库锚点无关。

CREATE TABLE IF NOT EXISTS `boll` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `code` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '股票代码',
    `date` varchar(10) NOT NULL DEFAULT '' COMMENT '锚点交易日 yyyy-MM-dd：day=该日；week/month/quarter=该周期最后交易日（同 stock_bar_agg.bar_date）；展示用周期首日请关联 first_trade_date',
    `type` varchar(10) NOT NULL DEFAULT '' COMMENT 'day=日 week=周 month=月 quarter=季',
    `md` decimal(25,16) NOT NULL DEFAULT 0.0000000000000000 COMMENT '标准差（如 20 周期）',
    `mb` decimal(20,5) NOT NULL DEFAULT 0.00000 COMMENT '中轨',
    `up` decimal(20,5) NOT NULL DEFAULT 0.00000 COMMENT '上轨',
    `dn` decimal(20,5) NOT NULL DEFAULT 0.00000 COMMENT '下轨',
    `create_time` datetime NOT NULL COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code_date_type` (`code`, `date`, `type`),
    KEY `idx_code_type_date` (`code`, `type`, `date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='布林线（按周期类型）';
