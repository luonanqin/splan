package bean;

import lombok.Data;

@Data
public class StockPosition {

    private String stock;
    private double canSellQty; // 可卖数量
    private double costPrice; // 成本价
}
