-- 连续三周：每周均线满足 ma5 > ma10 > ma20。
-- 相邻两周（第1→2 周、第2→3 周）的增量满足：Δma5 > Δma10 > Δma20（均为后一周减前一周）。
-- 仅统计 week_option 表中的标的（与 classpath option/weekOption 同步）。
-- 连续三周 = 同一 code 下按 date 排序的相邻三根周线（数据序列连续，无中间缺周）。
-- 依赖：ma.type = 'week'；可选 stock_bar_agg 展示周期首尾交易日。

WITH w AS (
    SELECT
        m.code,
        m.`date`,
        m.ma5,
        m.ma10,
        m.ma20,
        LEAD(m.ma5, 1) OVER (PARTITION BY m.code ORDER BY m.`date`)  AS ma5_w2,
        LEAD(m.ma10, 1) OVER (PARTITION BY m.code ORDER BY m.`date`) AS ma10_w2,
        LEAD(m.ma20, 1) OVER (PARTITION BY m.code ORDER BY m.`date`) AS ma20_w2,
        LEAD(m.ma5, 2) OVER (PARTITION BY m.code ORDER BY m.`date`)  AS ma5_w3,
        LEAD(m.ma10, 2) OVER (PARTITION BY m.code ORDER BY m.`date`) AS ma10_w3,
        LEAD(m.ma20, 2) OVER (PARTITION BY m.code ORDER BY m.`date`) AS ma20_w3,
        LEAD(m.`date`, 2) OVER (PARTITION BY m.code ORDER BY m.`date`) AS date_w3
    FROM ma m
    INNER JOIN week_option wo ON wo.code = m.code
    WHERE m.`type` = 'week'
      AND m.ma5 > 0
      AND m.ma10 > 0
      AND m.ma20 > 0
)
SELECT
    w.code,
    w.`date`              AS start_week_bar_date,
    w.date_w3             AS end_week_bar_date,
    a1.first_trade_date   AS start_week_cycle_start,
    a3.last_trade_date    AS end_week_last_trade_date
FROM w
LEFT JOIN stock_bar_agg a1
       ON a1.code = w.code AND a1.period_type = 'week' AND a1.bar_date = w.`date`
LEFT JOIN stock_bar_agg a3
       ON a3.code = w.code AND a3.period_type = 'week' AND a3.bar_date = w.date_w3
WHERE w.ma5 > w.ma10
  AND w.ma10 > w.ma20
  AND w.ma5_w2 > w.ma10_w2
  AND w.ma10_w2 > w.ma20_w2
  AND w.ma5_w3 > w.ma10_w3
  AND w.ma10_w3 > w.ma20_w3
  -- 第1周→第2周：Δma5 > Δma10 > Δma20
  AND (w.ma5_w2 - w.ma5) > (w.ma10_w2 - w.ma10)
  AND (w.ma10_w2 - w.ma10) > (w.ma20_w2 - w.ma20)
  -- 第2周→第3周：Δma5 > Δma10 > Δma20
  AND (w.ma5_w3 - w.ma5_w2) > (w.ma10_w3 - w.ma10_w2)
  AND (w.ma10_w3 - w.ma10_w2) > (w.ma20_w3 - w.ma20_w2)
  AND w.date_w3 IS NOT NULL
ORDER BY w.code, w.`date`;
