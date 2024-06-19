package bean;

import lombok.Data;

@Data
public class OptionSnapshot {

    private double implied_volatility;
    private OptionSnapshotUnderlying underlying_asset;
}
