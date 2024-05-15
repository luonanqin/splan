package bean;

import lombok.Data;

import java.util.List;

@Data
public class OptionQuoteResp {

    private String status;
    private String request_id;
    private String next_url;
    private List<OptionQuote> results;
}
