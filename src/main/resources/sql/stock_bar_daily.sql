create table `2025`
(
    id              int unsigned auto_increment
        primary key,
    code            varchar(10)     default ''                 not null comment '股票代码',
    date            varchar(10)     default ''                 not null comment '交易日',
    open            decimal(20, 5)  default 0.00000            not null comment '开盘',
    close           decimal(20, 5)  default 0.00000            not null comment '收盘',
    high            decimal(20, 5)  default 0.00000            not null comment '最高',
    low             decimal(20, 5)  default 0.00000            not null comment '最低',
    volume          bigint          default 0                  not null comment '成交量',
    open_trade      decimal(10, 3)  default 0.000              not null comment '真实开盘成交价',
    open_trade_time varchar(50)     default ''                 not null comment '真实开盘成交时间',
    f1min_avg_price decimal(10, 3)  default 0.000              not null comment '第一分钟成交均价',
    f1min_volume    bigint          default 0                  not null comment '第一分钟成交量',
    md              decimal(25, 16) default 0.0000000000000000 not null comment '布林线20日标准差',
    mb              decimal(20, 5)  default 0.00000            not null comment '布林线中轨',
    up              decimal(20, 5)  default 0.00000            not null comment '布林线上轨',
    dn              decimal(20, 5)  default 0.00000            not null comment '布林线下轨',
    open_md         decimal(25, 16) default 0.0000000000000000 not null comment '开盘布林线下轨',
    open_mb         decimal(20, 5)  default 0.00000            not null comment '开盘布林线下轨',
    open_up         decimal(20, 5)  default 0.00000            not null comment '开盘布林线下轨',
    open_dn         decimal(20, 5)  default 0.00000            not null comment '开盘布林线下轨',
    ma5             decimal(20, 5)  default 0.00000            not null comment '5日均线',
    ma10            decimal(20, 5)  default 0.00000            not null comment '10日均线',
    ma20            decimal(20, 5)  default 0.00000            not null comment '20日均线',
    ma30            decimal(20, 5)  default 0.00000            not null comment '30日均线',
    ma60            decimal(20, 5)  default 0.00000            not null comment '60日均线',
    create_time     datetime                                   not null comment '创建时间',
    update_time     datetime                                   not null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_code_date
        unique (code, date)
);

