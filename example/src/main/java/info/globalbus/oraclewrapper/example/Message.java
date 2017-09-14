package info.globalbus.oraclewrapper.example;

import info.globalbus.oraclewrapper.OracleStruct;
import lombok.AllArgsConstructor;
import lombok.Data;

@OracleStruct("Message")
@Data
@AllArgsConstructor
public class Message {
    String code;
    String message;
    Void unrelated;
}
