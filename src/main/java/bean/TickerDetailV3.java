package bean;

import lombok.Data;

/**
 * Created by Luonanqin on 2023/1/17.
 */
@Data
public class TickerDetailV3 {

    private String date;
    private String ticker;
    private String list_date;
    private long share_class_shares_outstanding;
    private long market_cap;
}
