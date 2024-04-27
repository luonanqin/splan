package bean;

import lombok.Data;

import java.util.List;

/**
 * Created by Luonanqin on 2023/4/28.
 */
@Data
public class AggregateTradeResp {

    private List<AggregateTrade> results;
    private String status;
    private String request_id;
    private String next_url;
    private int queryCount;
    private int resultCount;
    private String ticker;
}
