package bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Created by Luonanqin on 2023/5/17.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimpleTrade {

    private String date;
    private String code;
    private double tradePrice;
    private double volume;
    private String tradeTime;
}
