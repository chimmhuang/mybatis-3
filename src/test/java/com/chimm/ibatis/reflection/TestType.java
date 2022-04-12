package com.chimm.ibatis.reflection;

import org.apache.ibatis.reflection.TypeParameterResolver;
import org.junit.Test;
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;

/**
 * @author Chimm Huang
 * @date 2022/4/11
 */
public class TestType {

    public class ClassA<K, V> {
        protected Map<K, V> map;

        public Map<K, V> getMap() {
            return map;
        }

        public void setMap(Map<K, V> map) {
            this.map = map;
        }
    }

    public class SubClassA<T> extends ClassA<T, T> {
    }

    SubClassA<Long> sa = new SubClassA<Long>();

    @Test
    public void mainTest() throws Exception {
        Field f = ClassA.class.getDeclaredField("map");

        System.out.println(f.getGenericType()); // java.util.Map<K, V>
        System.out.println(f.getGenericType() instanceof ParameterizedType); // true

        /*
            解析 SubClassA<Long>(ParameterizedType 类型) 中的 map 字段，注意：ParameterizedTypeImpl 是
            在 sun.reflect.generics.reflectiveObjects 包下的 ParameterizedType 接口实现
         */
        Type type = TypeParameterResolver.resolveFieldType(f, ParameterizedTypeImpl
                .make(SubClassA.class, new Type[]{Long.class}, TestType.class));

        /*
            也可以使用下面的方式生成上述 ParameterizedType 对象，
            并调用 TypeParameterResolver.resolveFieldType() 方法：
         */
        // TypeParameterResolver.resolveFieldType(f, TestType.class.getDeclaredField("sa").getGenericType());

        // 注意：TypeParameterResolver$ParameterizedTypeImpl 是 ParameterizedType 接口的实现
        System.out.println(type.getClass()); // class org.apache.ibatis.reflection.TypeParameterResolver$ParameterizedTypeImpl

        ParameterizedType p = (ParameterizedType) type;
        System.out.println(p.getRawType()); // interface java.util.Map
        System.out.println(p.getOwnerType()); // null

        for (Type t : p.getActualTypeArguments()) {
            System.out.println(t);
            //class java.lang.Long
            //class java.lang.Long
        }
    }
}
