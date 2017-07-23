package info.globalbus.oraclewrapper;

@FunctionalInterface
public interface Lazy<T> {
    T get();
}
