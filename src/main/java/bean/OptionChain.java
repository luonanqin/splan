package bean;

import lombok.Data;

@Data
public class OptionChain {

    private Detail detail;

    @Data
    public class Detail {
        private String contract_type;
        private String exercise_style;
        private String expiration_date;
        private int shares_per_contract;
        private double strike_price;
        private String ticker;
    }
}
