package bean;

import lombok.Data;

@Data
public class StopLoss {

    private String stock;
    private double canSellQty; // 可卖数量
    private double lossPrice; // 止损价
}
