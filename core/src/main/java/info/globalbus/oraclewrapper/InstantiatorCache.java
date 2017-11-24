package info.globalbus.oraclewrapper;

import info.globalbus.oraclewrapper.internal.util.ReflectionUtils;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import oracle.jdbc.internal.OracleConnection;
import oracle.sql.ArrayDescriptor;
import oracle.sql.SQLName;
import oracle.sql.StructDescriptor;
import oracle.sql.TypeDescriptor;
import org.dalesbred.internal.instantiation.InstantiationFailureException;
import org.dalesbred.internal.instantiation.Instantiator;
import org.dalesbred.internal.instantiation.InstantiatorArguments;
import org.dalesbred.internal.instantiation.InstantiatorProvider;
import org.dalesbred.internal.instantiation.NamedTypeList;
import org.dalesbred.internal.jdbc.ResultSetUtils;

/**
 * Cache for resolved instantiators of Java Objects. Internal usage.
 */
@Slf4j
@SuppressWarnings("deprecated")
class InstantiatorCache {
    private final InstantiatorProvider instantiatorProvider;
    private final Map<Class<?>, InstantiatorEntry> instatiatorCache = new ConcurrentHashMap<>();
    private final Map<String, Class<?>> instatiatorNameCache = new ConcurrentHashMap<>();
    private final Set<Class<?>> knownInputTypes = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<String, StructDescriptor> structDescriptorMap = new ConcurrentHashMap<>();
    private final Map<String, ArrayDescriptor> arrayDescriptorMap = new ConcurrentHashMap<>();
    private final ReflectionUtils.PrivateMethod<Stream<? extends Constructor<?>>> candidateConstructors;
    private final ReflectionUtils.PrivateMethod implicitInstantiator;
    private final Field typeDescriptorConnection;
    @Getter
    private final OracleConnection dummyConnection;

    InstantiatorCache(InstantiatorProvider instantiatorProvider) {
        this.instantiatorProvider = instantiatorProvider;
        candidateConstructors = ReflectionUtils.callPrivate(instantiatorProvider,
            "candidateConstructorsSortedByDescendingParameterCount", Class.class);
        implicitInstantiator = ReflectionUtils.callPrivate(instantiatorProvider,
            "implicitInstantiatorFrom", Constructor.class, NamedTypeList.class);
        typeDescriptorConnection = Arrays.stream(TypeDescriptor.class.getDeclaredFields()).filter(v ->
            "connection".equals(v.getName())).findFirst().orElseThrow(IllegalArgumentException::new);
        typeDescriptorConnection.setAccessible(true);
        dummyConnection = (OracleConnection) Proxy.newProxyInstance(InstantiatorCache.class.getClassLoader(),
            new Class[] {OracleConnection.class}, (proxy, method, args) -> {
                if ("isDescriptorSharable".equals(method.getName())) {
                    return true;
                } else if ("physicalConnectionWithin".equals(method.getName())) {
                    return proxy;
                } else {
                    throw new UnsupportedOperationException("This proxy cannot be called");
                }
            });
    }

    @SuppressWarnings("unchecked")
    <T> InstantiatorEntry<T> get(Class<T> clazz) {
        return instatiatorCache.get(clazz);
    }

    <T> InstantiatorEntry<T> add(Class<T> outputClass, StructDescriptor desc) throws SQLException {
        ResultSetMetaData meta = desc.getMetaData();
        NamedTypeList types = ResultSetUtils.getTypes(meta);
        Instantiator<T> ctor;
        if (types.size() == 1 && types.getType(0).equals(oracle.jdbc.OracleStruct.class)) {
            ctor = findSingleInstantiator(outputClass, types);
        } else {
            ctor = instantiatorProvider.findInstantiator(outputClass, types);
        }
        InstantiatorEntry<T> entry = new InstantiatorEntry<>(ctor, types);
        instatiatorCache.put(outputClass, entry);
        return entry;
    }

    @SuppressWarnings("unchecked")
    private <T> Instantiator<T> findSingleInstantiator(Class<T> cl, NamedTypeList types) {
        return candidateConstructors.call(cl).orElse(Stream.empty())
            .map(ctor -> ((Optional<Instantiator<T>>) implicitInstantiator.call(ctor, types).orElse(Optional.empty()))
                .orElse(null))
            .filter(Objects::nonNull)
            .findFirst()
            .orElseThrow(() -> new InstantiationFailureException("could not find a way to instantiate " + cl + " with"
                + " parameters " + types));
    }

    boolean isKnown(Class<?> type) {
        return instatiatorNameCache.values().contains(type);
    }

    void setKnown(Class<?> type) {
        instatiatorNameCache.put(SqlStructParameter.getTypeName(type), type);
    }

    boolean isKnownInput(Class<?> type) {
        return knownInputTypes.contains(type);
    }

    void setKnownInput(Class<?> type) {
        knownInputTypes.add(type);
    }

    Class<?> getClassByName(SQLName sqlName) throws SQLException {
        return instatiatorNameCache.get(sqlName.getName());
    }

    StructDescriptor getStructFromCache(String typeName, Connection connection) throws SQLException {
        StructDescriptor structDescriptor = structDescriptorMap.get(typeName);
        if (structDescriptor == null) {
            structDescriptor = new StructDescriptor(typeName, connection);
            structDescriptor.isInstantiable();
            clearConnection(structDescriptor);
            structDescriptorMap.put(typeName, structDescriptor);
        }
        return structDescriptor;
    }

    ArrayDescriptor getArrayFromCache(String typeName, Connection connection) throws SQLException {
        ArrayDescriptor arrayDescriptor = arrayDescriptorMap.get(typeName);
        if (arrayDescriptor == null) {
            arrayDescriptor = new ArrayDescriptor(typeName, connection);
            arrayDescriptor.getArrayType();
            clearConnection(arrayDescriptor);
            arrayDescriptorMap.put(typeName, arrayDescriptor);
        }
        return arrayDescriptor;
    }

    synchronized <T> T inConnection(TypeDescriptor descriptor, Connection connection, Callable<T> callee) throws
        SQLException {
        try {
            descriptor.setConnection(connection);
            return callee.call();
        } catch (SQLException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SQLException(ex);
        } finally {
            clearConnection(descriptor);
        }
    }

    private void clearConnection(TypeDescriptor descriptor) {
        try {
            typeDescriptorConnection.set(descriptor, dummyConnection);
        } catch (IllegalAccessException ex) {
            log.error("Cannot clear connection on TypeDescriptor", ex);
        }
    }

    @Value
    @FieldDefaults(level = AccessLevel.PRIVATE)
    static class InstantiatorEntry<T> {
        final Instantiator<T> ctor;
        final NamedTypeList types;

        T instantiate(Object[] arguments) {
            InstantiatorArguments instantiatorArguments = new InstantiatorArguments(types, arguments);
            return ctor.instantiate(instantiatorArguments);
        }
    }
}
