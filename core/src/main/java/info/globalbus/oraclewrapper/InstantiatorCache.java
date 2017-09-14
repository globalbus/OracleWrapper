package info.globalbus.oraclewrapper;

import info.globalbus.oraclewrapper.internal.util.ReflectionUtils;
import java.lang.reflect.Constructor;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.Value;
import oracle.sql.SQLName;
import oracle.sql.StructDescriptor;
import org.dalesbred.internal.instantiation.InstantiationFailureException;
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
    private final ReflectionUtils.PrivateMethod candidateConstructors;
    private final ReflectionUtils.PrivateMethod implictInstantiator;

    InstantiatorCache(InstantiatorProvider instantiatorProvider) {
        this.instantiatorProvider = instantiatorProvider;
        candidateConstructors = ReflectionUtils.callPrivate(instantiatorProvider,
            "candidateConstructorsSortedByDescendingParameterCount", Class.class);
        implictInstantiator = ReflectionUtils.callPrivate(instantiatorProvider,
            "implicitInstantiatorFrom", Constructor.class, NamedTypeList.class);

    }

    @SuppressWarnings("unchecked")
    <T> InstantiatorEntry<T> get(Class<T> clazz) {
        return instatiatorCache.get(clazz);
    }

    <T> InstantiatorEntry<T> add(Class<T> outputClass, StructDescriptor desc) throws SQLException {
        ResultSetMetaData meta = desc.getMetaData();
        NamedTypeList types = ResultSetUtils.getTypes(meta);
        Instantiator<T> ctor;
        if (types.size() == 1) {
            ctor = findSingleInstantiator(outputClass, types);
        } else {
            ctor = instantiatorProvider.findInstantiator(outputClass, types);
        }
        InstantiatorEntry<T> entry = new InstantiatorEntry<>(ctor, types);
        instatiatorCache.put(outputClass, entry);
        knownOutputTypes.add(outputClass);
        return entry;
    }

    @SuppressWarnings("unchecked")
    private <T> Instantiator<T> findSingleInstantiator(Class<T> cl, NamedTypeList types) {
        return ((Stream<? extends Constructor<?>>) candidateConstructors.call(cl))
            .map(ctor -> ((Optional<Instantiator<T>>) implictInstantiator.call(ctor, types)).orElse(null))
            .filter(Objects::nonNull)
            .findFirst()
            .orElseThrow(() -> new InstantiationFailureException("could not find a way to instantiate " + cl + " with parameters " + types));
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
        return instatiatorNameCache.get(sqlName.getName());
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
