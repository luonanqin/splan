package bean;

import lombok.Data;

import java.util.List;

@Data
public class OptionContractsResp {

    private String request_id;
    private List<OptionContracts> results;
    private String status;
}
