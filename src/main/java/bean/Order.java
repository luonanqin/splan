package bean;

import lombok.Data;
import lombok.ToString;

/**
 * Created by Luonanqin on 2023/5/23.
 */
@Data
@ToString
public class Order {

    private long orderID; //订单号
    private int orderType; //订单类型
    private int orderStatus; //订单状态, 参见 OrderStatus 的枚举定义
    private String code; //代码
    private String name; //名称
    private double count; //订单数量，2位精度，期权单位是"张"
    private double tradeCount; //成交数量，2位精度，期权单位是"张"
    private double price; //成交价格，3位精度
    private double avgPrice; //成交均价格，无精度限制
    private String createTime; //创建时间，严格按 YYYY-MM-DD HH:MM:SS 或 YYYY-MM-DD HH:MM:SS.MS 格式传
    private String updateTime; //最后更新时间，严格按 YYYY-MM-DD HH:MM:SS 或 YYYY-MM-DD HH:MM:SS.MS 格式传
    private int tradeSide; //交易方向, 参见 TrdSide 的枚举定义
    private String orderIDEx; //扩展订单号(仅查问题时备用)
}
