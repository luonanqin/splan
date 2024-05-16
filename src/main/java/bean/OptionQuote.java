package bean;

import lombok.Data;

@Data
public class OptionQuote {

    private String code;
    private String type;
    private int ask_exchange; // 302,
    private double ask_price; // 1.7,
    private int ask_size; // 716,
    private int bid_exchange; // 302,
    private double bid_price; // 0.45,
    private int bid_size; // 1066,
    private long sequence_number;// 32833180,
    private long sip_timestamp; // 1704465109914787840

    public String toString() {
        return type + "\t" + sip_timestamp + "\t" + ask_price + "\t" + ask_size + "\t" + bid_price + "\t" + bid_size;
    }
}
