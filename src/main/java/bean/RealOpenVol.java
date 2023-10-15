package bean;

import lombok.Data;

import java.io.Serializable;

@Data
public class RealOpenVol implements Serializable {

    private String date;
    private double volumn;
    private double avgPrice;

}
