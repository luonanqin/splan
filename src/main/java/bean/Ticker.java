package bean;

import lombok.Data;

/**
 * Created by Luonanqin on 2023/1/11.
 */
@Data
public class Ticker {

    private String ticker;
    private String name;
    private String market;
    private String locale;
    private String primary_exchange;
    private String type;
    private boolean active;
    private String currency_name;
    private String cik;
    private String composite_figi;
    private String share_class_figi;
    private String last_updated_utc;

    @Override
    public String toString() {
        return "ticker='" + ticker + '\'' +
          ", name='" + name + '\'' +
          //          ", market='" + market + '\'' +
          //          ", locale='" + locale + '\'' +
          //          ", primary_exchange='" + primary_exchange + '\'' +
          ", type=" + type +
          ", active='" + active + '\'' +
          //          ", currency_name='" + currency_name + '\'' +
          ", cik='" + cik + '\'' +
          ", composite_figi='" + composite_figi + '\'' +
          //          ", share_class_figi='" + share_class_figi + '\'' +
          //          ", last_updated_utc='" + last_updated_utc + '\''
          "";
    }
}
