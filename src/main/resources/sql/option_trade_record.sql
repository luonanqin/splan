-- 期权交易记录（单笔：买入后可补卖出）
-- 到期日、买入日、卖出日均为 DATE（yyyy-MM-dd），不含时分秒

CREATE TABLE IF NOT EXISTS `option_trade_record` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `underlying_code` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '正股代码',
    `strike_price` decimal(20, 2) NOT NULL DEFAULT 0.00 COMMENT '行权价，两位小数',
    `option_right` varchar(4) NOT NULL DEFAULT '' COMMENT 'call 或 put',
    `expiration_date` date NOT NULL COMMENT '到期日',
    `buy_date` date NOT NULL COMMENT '买入日',
    `sell_date` date DEFAULT NULL COMMENT '卖出日，未平仓为空',
    `buy_price` decimal(20, 2) NOT NULL DEFAULT 0.00 COMMENT '买入价（单价），两位小数',
    `sell_price` decimal(20, 2) DEFAULT NULL COMMENT '卖出价（单价），两位小数',
    `quantity` int NOT NULL DEFAULT 0 COMMENT '数量（张/合约数）',
    `create_time` datetime NOT NULL COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_underlying_right` (`underlying_code`, `option_right`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='期权交易记录';

