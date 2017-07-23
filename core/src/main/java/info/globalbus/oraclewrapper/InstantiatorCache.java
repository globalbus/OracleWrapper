package info.globalbus.oraclewrapper;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.Value;
import oracle.sql.SQLName;
import oracle.sql.StructDescriptor;
import org.dalesbred.internal.instantiation.Instantiator;
import org.dalesbred.internal.instantiation.InstantiatorArguments;
import org.dalesbred.internal.instantiation.InstantiatorProvider;
import org.dalesbred.internal.instantiation.NamedTypeList;
import org.dalesbred.internal.jdbc.ResultSetUtils;

/**
 * Cache for resolved instantiators of Java Objects. Internal usage.
 */
class InstantiatorCache {
    private final InstantiatorProvider instantiatorProvider;
    private final Map<Class<?>, InstantiatorEntry> instatiatorCache = new HashMap<>();
    private final Map<String, Class<?>> instatiatorNameCache = new HashMap<>();
    private final Set<Class<?>> knownOutputTypes = new HashSet<>();
    private final Set<Class<?>> knownInputTypes = new HashSet<>();

    InstantiatorCache(InstantiatorProvider instantiatorProvider) {
        this.instantiatorProvider = instantiatorProvider;
    }

    @SuppressWarnings("unchecked")
    <T> InstantiatorEntry<T> get(Class<T> clazz) {
        return instatiatorCache.get(clazz);
    }

    <T> InstantiatorEntry<T> add(Class<T> outputClass, StructDescriptor desc) throws SQLException {
        ResultSetMetaData meta = desc.getMetaData();
        NamedTypeList types = ResultSetUtils.getTypes(meta);
        Instantiator<T> ctor = instantiatorProvider.findInstantiator(outputClass, types);
        InstantiatorEntry<T> entry = new InstantiatorEntry<>(ctor, types);
        instatiatorCache.put(outputClass, entry);
        knownOutputTypes.add(outputClass);
        return entry;
    }

    boolean isKnown(Class<?> type) {
        return knownOutputTypes.contains(type);
    }

    void setKnown(Class<?> type) {
        knownOutputTypes.add(type);
        addNameCache(type, SqlStructParameter.getTypeName(type));
    }

    boolean isKnownInput(Class<?> type) {
        return knownInputTypes.contains(type);
    }

    void setKnownInput(Class<?> type) {
        knownInputTypes.add(type);
    }

    private void addNameCache(Class<?> type, String sqlName) {
        instatiatorNameCache.put(sqlName, type);
    }

    Class<?> getClassByName(SQLName sqlName) throws SQLException {
        return instatiatorNameCache.get(sqlName.getSimpleName());
    }

    @Value
    static class InstantiatorEntry<T> {
        final Instantiator<T> ctor;
        final NamedTypeList types;

        T instantiate(Object[] arguments) {
            InstantiatorArguments instantiatorArguments = new InstantiatorArguments(types, arguments);
            return ctor.instantiate(instantiatorArguments);
        }
    }
}
