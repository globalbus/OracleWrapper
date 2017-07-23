package info.globalbus.oraclewrapper.example;

import info.globalbus.oraclewrapper.OracleStruct;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Created by globalbus on 06.02.17.
 */
@Data
@NoArgsConstructor
@OracleStruct
public class Complex {
    Double rPart;
    Double iPart;
}
