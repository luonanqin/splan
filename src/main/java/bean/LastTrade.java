package bean;

import lombok.Data;

/**
 * Created by Luonanqin on 2023/5/5.
 */
@Data
public class LastTrade {

    private String T; // stock code
    private double p; // price
    private int s; // size
}
