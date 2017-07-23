package info.globalbus.oraclewrapper.internal.util;

import java.lang.reflect.Method;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.dalesbred.annotation.DalesbredIgnore;
import org.dalesbred.internal.instantiation.InstantiationFailureException;

import static java.lang.reflect.Modifier.isPublic;
import static org.dalesbred.internal.utils.StringUtils.isEqualIgnoringCaseAndUnderscores;

@Slf4j
public class ReflectionUtils {

    public static Optional<Method> findGetterOrSetter(Class<?> cl, String propertyName, boolean getter) {
        String methodName = (getter ? "get" : "set") + propertyName;
        int parameterCount = getter ? 0 : 1;
        Method result = null;

        for (Method method : cl.getMethods()) {
            if (isPublic(method.getModifiers())
                && isEqualIgnoringCaseAndUnderscores(methodName, method.getName())
                && method.getParameterCount() == parameterCount
                && !method.isAnnotationPresent(DalesbredIgnore.class)) {
                if (result != null) {
                    throw new InstantiationFailureException("Conflicting accessors for property: "
                        + result + " - " + propertyName);
                }
                result = method;
            }
        }

        return Optional.ofNullable(result);
    }
}
