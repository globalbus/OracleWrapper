package info.globalbus.oraclewrapper;

import java.util.List;

public interface ProcedureCaller<T> {
    String OUTPUT_PARAM = "output";
    String INPUT_PARAM = "input";

    List<T> mapList(Object... input);

    T mapObject(Object... input);
}
