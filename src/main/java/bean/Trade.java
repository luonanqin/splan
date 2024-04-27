package bean;

import lombok.Data;

import java.util.List;

/**
 * Created by Luonanqin on 2023/4/28.
 */
@Data
public class Trade {

    private List<Integer> conditions;
    private int exchange;
    private String id;
    private long participant_timestamp;
    private double price;
    private int sequence_number;
    private long sip_timestamp;
    private int size;
    private int tape;
}
