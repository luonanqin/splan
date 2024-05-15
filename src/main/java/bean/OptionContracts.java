package bean;

import lombok.Data;

@Data
public class OptionContracts {

    private String cfi;
    private String contract_type; // call
    private String exercise_style;
    private String expiration_date; //"2024-01-19",
    private String primary_exchange; // "BATO",
    private String shares_per_contract; //100,
    private String strike_price; //10,
    private String ticker; //O:AGL240119C00010000",
    private String underlying_ticker; //"AGL"
}
