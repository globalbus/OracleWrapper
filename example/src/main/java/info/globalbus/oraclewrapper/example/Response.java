package info.globalbus.oraclewrapper.example;

import info.globalbus.oraclewrapper.OracleStruct;
import lombok.AllArgsConstructor;
import lombok.Data;

@OracleStruct("Response")
@Data
@AllArgsConstructor
public class Response {
    Message message;
}
