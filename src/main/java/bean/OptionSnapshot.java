package bean;

import lombok.Data;

@Data
public class OptionSnapshot {

    private String ticker;
    private double implied_volatility;
    private OptionGreek greeks;
    private OptionSnapshotUnderlying underlying_asset;
    private OptionLastTrade last_trade;
    private OptionLastQuote last_quote;
}
