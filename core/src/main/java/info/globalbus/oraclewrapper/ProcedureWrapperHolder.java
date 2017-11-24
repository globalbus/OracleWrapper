package info.globalbus.oraclewrapper;

@FunctionalInterface
public interface ProcedureWrapperHolder {
    ProcedureWrapperBuilder.LazyInitializer getWrapper();
}
