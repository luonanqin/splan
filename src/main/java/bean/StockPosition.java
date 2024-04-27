package bean;

import lombok.Data;
import lombok.ToString;

@ToString
@Data
public class StockPosition {

    private String stock;
    private double canSellQty; // 可卖数量
    private double costPrice; // 成本价
    private double currPrice; // 当前价
}
