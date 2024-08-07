package bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Created by Luonanqin on 2023/8/14.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class StockEvent {

    private String stock;
    private double price;
    private long time;
}
