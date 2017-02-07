package info.globalbus.oracleTest.OracleProcedureWrapper;

import lombok.Value;
import oracle.sql.STRUCT;
import org.dalesbred.internal.instantiation.Instantiator;
import org.dalesbred.internal.instantiation.InstantiatorArguments;
import org.dalesbred.internal.instantiation.InstantiatorProvider;
import org.dalesbred.internal.instantiation.NamedTypeList;
import org.dalesbred.internal.jdbc.ResultSetUtils;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by globalbus on 06.02.17.
 */
public class InstantiatorCache {
    private final InstantiatorProvider instantiatorProvider;

    public InstantiatorCache(InstantiatorProvider instantiatorProvider){
        this.instantiatorProvider=instantiatorProvider;
    }
    private final Map<Class<?>, InstatiatorEntry> instatiatorCache = new HashMap<>();
    @SuppressWarnings("unchecked")
    public <T> InstatiatorEntry<T> get(Class<T> clazz){
        return instatiatorCache.get(clazz);
    }
    public <T> InstatiatorEntry<T> add(Class<T> outputClass, STRUCT struct) throws SQLException {
        ResultSetMetaData meta = struct.getDescriptor().getMetaData();
        NamedTypeList types = ResultSetUtils.getTypes(meta);
        Instantiator<T> ctor = instantiatorProvider.findInstantiator(outputClass, types);
        InstatiatorEntry<T> entry = new InstatiatorEntry<>(ctor, types);
        instatiatorCache.put(outputClass, entry);
        return entry;
    }
    @Value
    static class InstatiatorEntry<T> {
        final Instantiator<T> ctor;
        final NamedTypeList types;

        T instantiate(Object[] arguments) {
            InstantiatorArguments instantiatorArguments = new InstantiatorArguments(types, arguments);
            return ctor.instantiate(instantiatorArguments);
        }
    }
}
