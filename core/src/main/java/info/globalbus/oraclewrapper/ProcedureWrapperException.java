package info.globalbus.oraclewrapper;

class ProcedureWrapperException extends RuntimeException {
    ProcedureWrapperException(String message, Throwable innerException) {
        super(message, innerException);
    }

    ProcedureWrapperException(String message) {
        super(message);
    }
}
