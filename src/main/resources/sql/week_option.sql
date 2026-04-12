-- 与 classpath `option/weekOption` 同步的跟踪标的，供 SQL 中 `JOIN week_option` / `IN (SELECT code FROM week_option)` 使用。
-- 更新列表：修改 resources/option/weekOption 后重新生成 week_option_inserts.sql 并执行（或 INSERT IGNORE 增量）。

CREATE TABLE IF NOT EXISTS `week_option` (
    `code` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '股票代码',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='weekOption 标的列表（与 resources/option/weekOption 一致）';
