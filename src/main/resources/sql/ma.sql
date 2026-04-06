-- 均线（MA），按 code + 锚点交易日 + 周期类型
-- 锚点 date：日线为当日；周/月/季为「该周期内最后一个交易日」，与 stock_bar_agg.bar_date 一致。
--   图表若要把点画在周期首日，横轴请用 stock_bar_agg.first_trade_date（或等价字段）关联展示，与存库锚点无关。

create table ma
(
    id int unsigned auto_increment
        primary key,
    code varchar(10) default '' not null comment '股票代码',
    date varchar(10) default '' not null comment '锚点交易日 yyyy-MM-dd：day=该日；week/month/quarter=该周期最后交易日（同 stock_bar_agg.bar_date）；展示用周期首日请关联 first_trade_date',
    type varchar(10) default '' not null comment '日ma=day、周ma=week、月ma=month等',
    ma5 decimal(20,5) default 0.00000 not null comment '5周期均线',
    ma10 decimal(20,5) default 0.00000 not null comment '10周期均线',
    ma20 decimal(20,5) default 0.00000 not null comment '20周期均线',
    ma30 decimal(20,5) default 0.00000 not null comment '30周期均线',
    ma60 decimal(20,5) default 0.00000 not null comment '60周期均线',
    create_time datetime not null comment '创建时间',
    update_time datetime not null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_code_date_type
        unique (code, date, type)
);

