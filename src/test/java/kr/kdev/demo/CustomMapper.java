package kr.kdev.demo;

import com.google.gson.Gson;
import lombok.SneakyThrows;
import org.postgresql.util.PGobject;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.beans.FeatureDescriptor;
import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.*;
import java.util.stream.Collectors;

public class CustomMapper<T> extends DataClassRowMapper<T> {
    private static final Gson gson = new Gson();

    public CustomMapper() {
    }

    public CustomMapper(Class<T> clazz) {
        super(clazz);
    }

    @SneakyThrows
    @Override
    protected Object getColumnValue(ResultSet rs, int index, PropertyDescriptor pd) {
        Class<?> propertyType = pd.getPropertyType();
        String propertyName = pd.getName();

        if (propertyType.isArray() || Collection.class.isAssignableFrom(propertyType)) {
            Array arr = rs.getArray(index);
            String value = arr == null ? "[]" : arr.toString();
            Method writeMethod = pd.getWriteMethod();
            if (writeMethod != null) {
                Class<?> declaringClass = writeMethod.getDeclaringClass();
                Field declaredField = declaringClass.getDeclaredField(propertyName);
                Type genericType = declaredField.getGenericType();
                return gson.fromJson(value, genericType);
            }
        }

        Object object = rs.getObject(index);
        if (object == null) {
            return null;
        } else if (PGobject.class.isAssignableFrom(object.getClass())) {
            PGobject pGobject = (PGobject) rs.getObject(index);
            return gson.fromJson(pGobject == null ? "" : pGobject.getValue(), propertyType);
        }
        return JdbcUtils.getResultSetValue(rs, index, propertyType);
    }

    @SneakyThrows
    @Override
    public T mapRow(ResultSet rs, int rowNumber) {
        Class<T> mappedClass = getMappedClass();
        T mappedObject = BeanUtils.instantiateClass(mappedClass);
        BeanWrapper beanWrapper = PropertyAccessorFactory.forBeanPropertyAccess(mappedObject);
        initBeanWrapper(beanWrapper);

        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        Collection<? extends PropertyDescriptor> propertyDescriptors = determineBasicProperties(mappedClass);
        Map<String, ? extends PropertyDescriptor> propertyDescriptorMap = propertyDescriptors.stream()
                .collect(Collectors.toMap(FeatureDescriptor::getName, pd -> pd));

        for (int index = 1; index <= columnCount; index++) {
            String columnName = JdbcUtils.lookupColumnName(metaData, index);
            String fieldName = lowerCaseName(columnName);
            PropertyDescriptor pd = propertyDescriptorMap.get(fieldName);
            Object value = getColumnValue(rs, index, pd);
            beanWrapper.setPropertyValue(pd.getName(), value);
        }

        return mappedObject;
    }

    public static <T> CustomMapper<T> newInstance(Class<T> mappedClass) {
        return new CustomMapper<>(mappedClass);
    }

    public static Collection<? extends PropertyDescriptor> determineBasicProperties(Class<?> beanClass) throws IntrospectionException {
        Map<String, BasicPropertyDescriptor> pdMap = new TreeMap<>();

        for (Method method : beanClass.getMethods()) {
            String methodName = method.getName();
            boolean setter;
            int nameIndex;
            if (methodName.startsWith("set") && method.getParameterCount() == 1) {
                setter = true;
                nameIndex = 3;
            } else if (methodName.startsWith("get") && method.getParameterCount() == 0 && method.getReturnType() != Void.TYPE) {
                setter = false;
                nameIndex = 3;
            } else {
                if (!methodName.startsWith("is") || method.getParameterCount() != 0 || method.getReturnType() != Boolean.TYPE) {
                    continue;
                }

                setter = false;
                nameIndex = 2;
            }

            String propertyName = StringUtils.uncapitalizeAsProperty(methodName.substring(nameIndex));
            if (!propertyName.isEmpty()) {
                BasicPropertyDescriptor pd = pdMap.get(propertyName);
                if (pd != null) {
                    if (setter) {
                        Method writeMethod = pd.getWriteMethod();
                        if (writeMethod != null && !writeMethod.getParameterTypes()[0].isAssignableFrom(method.getParameterTypes()[0])) {
                            pd.addWriteMethod(method);
                        } else {
                            pd.setWriteMethod(method);
                        }
                    } else {
                        Method readMethod = pd.getReadMethod();
                        if (readMethod == null || readMethod.getReturnType() == method.getReturnType() && method.getName().startsWith("is")) {
                            pd.setReadMethod(method);
                        }
                    }
                } else {
                    pd = new BasicPropertyDescriptor(propertyName, !setter ? method : null, setter ? method : null);
                    pdMap.put(propertyName, pd);
                }
            }
        }

        return pdMap.values();
    }

    private static class BasicPropertyDescriptor extends PropertyDescriptor {
        @Nullable
        private Method readMethod;
        @Nullable
        private Method writeMethod;
        private final List<Method> alternativeWriteMethods = new ArrayList();

        public BasicPropertyDescriptor(String propertyName, @Nullable Method readMethod, @Nullable Method writeMethod) throws IntrospectionException {
            super(propertyName, readMethod, writeMethod);
        }

        public void setReadMethod(@Nullable Method readMethod) {
            this.readMethod = readMethod;
        }

        @Nullable
        public Method getReadMethod() {
            return this.readMethod;
        }

        public void setWriteMethod(@Nullable Method writeMethod) {
            this.writeMethod = writeMethod;
        }

        public void addWriteMethod(Method writeMethod) {
            if (this.writeMethod != null) {
                this.alternativeWriteMethods.add(this.writeMethod);
                this.writeMethod = null;
            }

            this.alternativeWriteMethods.add(writeMethod);
        }

        @Nullable
        public Method getWriteMethod() {
            if (this.writeMethod == null && !this.alternativeWriteMethods.isEmpty()) {
                if (this.readMethod == null) {
                    return this.alternativeWriteMethods.get(0);
                }

                for (Method method : this.alternativeWriteMethods) {
                    if (this.readMethod.getReturnType().isAssignableFrom(method.getParameterTypes()[0])) {
                        this.writeMethod = method;
                        break;
                    }
                }
            }

            return this.writeMethod;
        }
    }
}
