package bean;

import lombok.Data;
import lombok.ToString;

/**
 * Created by Luonanqin on 2023/8/14.
 */
@Data
@ToString
public class StockOptionEvent {

    private String stock;
    private Double lastClose;
    private Double price;
    private long time;

    public StockOptionEvent(String stock, Double price, long time) {
        this.stock = stock;
        this.price = price;
        this.time = time;
    }

    public Double getRatio() {
        return (lastClose - price) / lastClose;
    }
}
