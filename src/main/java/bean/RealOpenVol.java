package bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealOpenVol implements Serializable {

    private String date;
    private String code;
    private double volume;
    private double avgPrice;

}
