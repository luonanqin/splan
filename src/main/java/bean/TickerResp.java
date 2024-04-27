package bean;

import lombok.Data;

import java.util.List;

/**
 * Created by Luonanqin on 2023/1/11.
 */
@Data
public class TickerResp {

    private List<Ticker> results;
    private String status;
    private String request_id;
    private int count;
    private String next_url;
}
