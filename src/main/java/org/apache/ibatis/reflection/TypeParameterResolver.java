/**
 *    Copyright 2009-2016 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.reflection;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;

/**
 * 一个工具类，提供了一系列静态方法来解析指定类中的字段
 *
 * 自定义测试类：{@link com.chimm.ibatis.reflection.TestType#mainTest()}
 * 官方测试类：{@link org.apache.ibatis.reflection.TypeParameterResolverTest}
 *
 * @author Iwao AVE!
 */
public class TypeParameterResolver {

  /**
   * @return The field type as {@link Type}. If it has type parameters in the declaration,<br>
   *         they will be resolved to the actual runtime {@link Type}s.
   */
  public static Type resolveFieldType(Field field, Type srcType) {
    // 获取字段的声明类型
    Type fieldType = field.getGenericType();
    // 获取字段定义所在的类的 Class 对象
    Class<?> declaringClass = field.getDeclaringClass();
    // 调用 resolveType() 方法进行后续处理
    return resolveType(fieldType, srcType, declaringClass);
  }

  /**
   * @return The return type of the method as {@link Type}. If it has type parameters in the declaration,<br>
   *         they will be resolved to the actual runtime {@link Type}s.
   */
  public static Type resolveReturnType(Method method, Type srcType) {
    Type returnType = method.getGenericReturnType();
    Class<?> declaringClass = method.getDeclaringClass();
    return resolveType(returnType, srcType, declaringClass);
  }

  /**
   * @return The parameter types of the method as an array of {@link Type}s. If they have type parameters in the declaration,<br>
   *         they will be resolved to the actual runtime {@link Type}s.
   */
  public static Type[] resolveParamTypes(Method method, Type srcType) {
    Type[] paramTypes = method.getGenericParameterTypes();
    Class<?> declaringClass = method.getDeclaringClass();
    Type[] result = new Type[paramTypes.length];
    for (int i = 0; i < paramTypes.length; i++) {
      result[i] = resolveType(paramTypes[i], srcType, declaringClass);
    }
    return result;
  }

  private static Type resolveType(Type type, Type srcType, Class<?> declaringClass) {
    // 解析 TypeVariable 类型
    if (type instanceof TypeVariable) {
      return resolveTypeVar((TypeVariable<?>) type, srcType, declaringClass);
    } else if (type instanceof ParameterizedType) {
      // 解析 ParameterizedType 类型
      return resolveParameterizedType((ParameterizedType) type, srcType, declaringClass);
    } else if (type instanceof GenericArrayType) {
      // 解析 GenericArrayType 类型
      return resolveGenericArrayType((GenericArrayType) type, srcType, declaringClass);
    } else {
      return type;
    }
    // 字段、返回值、参数不可能直接定义成 WildcardType 类型，但可以嵌套在别的类型中。
  }

  /**
   * 该方法负责解析 GenericArrayType 类型的变量，它会根据数组元素的类型选择合适的 resolve*() 方法进行解析
   */
  private static Type resolveGenericArrayType(GenericArrayType genericArrayType, Type srcType, Class<?> declaringClass) {
    Type componentType = genericArrayType.getGenericComponentType();
    Type resolvedComponentType = null;
    // 根据数组元素类型选择合适的方法进行解析
    if (componentType instanceof TypeVariable) {
      resolvedComponentType = resolveTypeVar((TypeVariable<?>) componentType, srcType, declaringClass);
    } else if (componentType instanceof GenericArrayType) {
      resolvedComponentType = resolveGenericArrayType((GenericArrayType) componentType, srcType, declaringClass);
    } else if (componentType instanceof ParameterizedType) {
      resolvedComponentType = resolveParameterizedType((ParameterizedType) componentType, srcType, declaringClass);
    }
    // 根据解析后的数组项类型构造返回类型
    if (resolvedComponentType instanceof Class) {
      return Array.newInstance((Class<?>) resolvedComponentType, 0).getClass();
    } else {
      return new GenericArrayTypeImpl(resolvedComponentType);
    }
  }

  /**
   * @see com.chimm.ibatis.reflection.TestType#mainTest()
   *
   * @param parameterizedType （Map<K,V> 对应的 ParameterizedType 类型）
   * @param srcType （TestType.SubClassA<Long> 对应的 ParameterizedType 对象）
   * @param declaringClass （ClassA (声明 map 字段的类)）
   */
  private static ParameterizedType resolveParameterizedType(ParameterizedType parameterizedType, Type srcType, Class<?> declaringClass) {
    // 在该实例中，得到原始类型 map 对应的 class 对象 （interface java.util.Map）
    Class<?> rawType = (Class<?>) parameterizedType.getRawType();
    // 类型变量为 K 和 V
    Type[] typeArgs = parameterizedType.getActualTypeArguments();
    // 用于保存解析后的结果
    Type[] args = new Type[typeArgs.length];
    // 解析 K 和 V
    for (int i = 0; i < typeArgs.length; i++) {
      if (typeArgs[i] instanceof TypeVariable) {
        // 解析类型变量
        args[i] = resolveTypeVar((TypeVariable<?>) typeArgs[i], srcType, declaringClass);
      } else if (typeArgs[i] instanceof ParameterizedType) {
        // 如果嵌套了 ParameterizedType，则调用 resolveParameterizedType() 方法进行处理
        args[i] = resolveParameterizedType((ParameterizedType) typeArgs[i], srcType, declaringClass);
      } else if (typeArgs[i] instanceof WildcardType) {
        // 如果嵌套了 WildcardType，则调用 resolveWildcardType() 方法进行处理
        args[i] = resolveWildcardType((WildcardType) typeArgs[i], srcType, declaringClass);
      } else {
        args[i] = typeArgs[i];
      }
    }
    /*
        将解析结果封装成 TypeParameterResolver 中定义的 ParameterizedTypeImpl 实现并返回，本例中 args
        数组中的元素都是 Long.class
     */
    return new ParameterizedTypeImpl(rawType, null, args);
  }

  /**
   * 该方法负责解析 WildcardType 类型的变量。
   * 它首先解析 WildcardType 中记录的上下界，然后通过解析后的结果构造 WildcardTypeImpl 对象返回。
   */
  private static Type resolveWildcardType(WildcardType wildcardType, Type srcType, Class<?> declaringClass) {
    Type[] lowerBounds = resolveWildcardTypeBounds(wildcardType.getLowerBounds(), srcType, declaringClass);
    Type[] upperBounds = resolveWildcardTypeBounds(wildcardType.getUpperBounds(), srcType, declaringClass);
    return new WildcardTypeImpl(lowerBounds, upperBounds);
  }

  private static Type[] resolveWildcardTypeBounds(Type[] bounds, Type srcType, Class<?> declaringClass) {
    Type[] result = new Type[bounds.length];
    for (int i = 0; i < bounds.length; i++) {
      if (bounds[i] instanceof TypeVariable) {
        result[i] = resolveTypeVar((TypeVariable<?>) bounds[i], srcType, declaringClass);
      } else if (bounds[i] instanceof ParameterizedType) {
        result[i] = resolveParameterizedType((ParameterizedType) bounds[i], srcType, declaringClass);
      } else if (bounds[i] instanceof WildcardType) {
        result[i] = resolveWildcardType((WildcardType) bounds[i], srcType, declaringClass);
      } else {
        result[i] = bounds[i];
      }
    }
    return result;
  }

  /**
   * 负责解析 TypeVariable
   * @see com.chimm.ibatis.reflection.TestType#mainTest()
   *
   * @param typeVar （类型变量 K 对应的 TypeVariable 对象）
   * @param srcType （TestType.SubClassA<Long> 对应的 ParameterizedType 对象）
   * @param declaringClass （ClassA (声明 map 字段的类)）
   */
  private static Type resolveTypeVar(TypeVariable<?> typeVar, Type srcType, Class<?> declaringClass) {
    Type result = null;
    Class<?> clazz = null;
    if (srcType instanceof Class) {
      clazz = (Class<?>) srcType;
    } else if (srcType instanceof ParameterizedType) {
      // 本例中 SubClassA<Long> 是 ParameterizedType 类型，clazz 为 SubClassA 对应的 Class 对象
      ParameterizedType parameterizedType = (ParameterizedType) srcType;
      clazz = (Class<?>) parameterizedType.getRawType();
    } else {
      throw new IllegalArgumentException("The 2nd arg must be Class or ParameterizedType, but was: " + srcType.getClass());
    }

    /*
        因为 SubClassA 继承了 ClassA 且 map 字段定义在 ClassA 中，故这里的 srcType 与 declaringClass
        并不相等。如果 map 字段定义在 SubClassA 中，则可以直接结束对 K 的解析
     */
    if (clazz == declaringClass) {
      // 获取上界
      Type[] bounds = typeVar.getBounds();
      if(bounds.length > 0) {
        return bounds[0];
      }
      return Object.class;
    }

    // 获取声明的父类类型，即 ClassA<T,T> 对应的 ParameterizedType 对象
    Type superclass = clazz.getGenericSuperclass();
    // 通过扫描父类进行后续解析，这是递归的入口
    result = scanSuperTypes(typeVar, srcType, declaringClass, clazz, superclass);
    if (result != null) {
      return result;
    }

    // 获取接口
    Type[] superInterfaces = clazz.getGenericInterfaces();
    // 通过扫描接口进行后续解析，逻辑同扫描父类
    for (Type superInterface : superInterfaces) {
      result = scanSuperTypes(typeVar, srcType, declaringClass, clazz, superInterface);
      if (result != null) {
        return result;
      }
    }
    // 若在整个继承结构中都没有解析成功，则返回 Object.class
    return Object.class;
  }

  /**
   * 该方法会递归整个继承结构并完成类型变量的解析。
   * @see com.chimm.ibatis.reflection.TestType#mainTest()
   *
   * @param typeVar （类型变量 K 对应的 TypeVariable 对象）
   * @param srcType （TestType.SubClassA<Long> 对应的 ParameterizedType 对象）
   * @param declaringClass （ClassA (声明 map 字段的类)）
   * @param clazz （SubClassA 对应的 Class 对象）
   * @param superclass （Class<T,t> 对应的 ParameterizedType 对象）
   */
  private static Type scanSuperTypes(TypeVariable<?> typeVar, Type srcType, Class<?> declaringClass, Class<?> clazz, Type superclass) {
    Type result = null;
    // superclass 是 ClassA<T,T> 对应的 ParameterizedType 对象，条件成立
    if (superclass instanceof ParameterizedType) {
      ParameterizedType parentAsType = (ParameterizedType) superclass;
      // 原始类型是 ClassA
      Class<?> parentAsClass = (Class<?>) parentAsType.getRawType();
      // map 字段定义在 ClassA 中，条件成立
      if (declaringClass == parentAsClass) {
        Type[] typeArgs = parentAsType.getActualTypeArguments(); // {T,T}
        // ClassA 中定义的变量类型是 K 和 V
        TypeVariable<?>[] declaredTypeVars = declaringClass.getTypeParameters();
        for (int i = 0; i < declaredTypeVars.length; i++) {
          if (declaredTypeVars[i] == typeVar) { // 解析的目标类型变量是 K
            if (typeArgs[i] instanceof TypeVariable) { // T 是类型变量，条件成立
              // SubClassA 只有一个类型变量 T,且声明的父类是 ClassA<T,T>，本例中 T 被参数化为 Long，则 K 参数化为 Long
              TypeVariable<?>[] typeParams = clazz.getTypeParameters();
              for (int j = 0; j < typeParams.length; j++) {
                if (typeParams[j] == typeArgs[i]) {
                  if (srcType instanceof ParameterizedType) {
                    result = ((ParameterizedType) srcType).getActualTypeArguments()[j];
                  }
                  break;
                }
              }
            } else {
              /*
                  如果 SubClassA 继承了 ClassA<Long,Long>，则 typeArgs[i] 不是
                  TypeVariable 类型，直接返回 Long.class
               */
              result = typeArgs[i];
            }
          }
        }
      } else if (declaringClass.isAssignableFrom(parentAsClass)) {
        // 继续解析父类，直到解析到定义该字段的类
        result = resolveTypeVar(typeVar, parentAsType, declaringClass);
      }
    } else if (superclass instanceof Class) {
      if (declaringClass.isAssignableFrom((Class<?>) superclass)) {
        // 声明的父类不再含有类型变量且不是定义该字段的类，则继续解析
        result = resolveTypeVar(typeVar, superclass, declaringClass);
      }
    }
    return result;
  }

  private TypeParameterResolver() {
    super();
  }

  static class ParameterizedTypeImpl implements ParameterizedType {
    private Class<?> rawType;

    private Type ownerType;

    private Type[] actualTypeArguments;

    public ParameterizedTypeImpl(Class<?> rawType, Type ownerType, Type[] actualTypeArguments) {
      super();
      this.rawType = rawType;
      this.ownerType = ownerType;
      this.actualTypeArguments = actualTypeArguments;
    }

    @Override
    public Type[] getActualTypeArguments() {
      return actualTypeArguments;
    }

    @Override
    public Type getOwnerType() {
      return ownerType;
    }

    @Override
    public Type getRawType() {
      return rawType;
    }

    @Override
    public String toString() {
      return "ParameterizedTypeImpl [rawType=" + rawType + ", ownerType=" + ownerType + ", actualTypeArguments=" + Arrays.toString(actualTypeArguments) + "]";
    }
  }

  static class WildcardTypeImpl implements WildcardType {
    private Type[] lowerBounds;

    private Type[] upperBounds;

    private WildcardTypeImpl(Type[] lowerBounds, Type[] upperBounds) {
      super();
      this.lowerBounds = lowerBounds;
      this.upperBounds = upperBounds;
    }

    @Override
    public Type[] getLowerBounds() {
      return lowerBounds;
    }

    @Override
    public Type[] getUpperBounds() {
      return upperBounds;
    }
  }

  static class GenericArrayTypeImpl implements GenericArrayType {
    private Type genericComponentType;

    private GenericArrayTypeImpl(Type genericComponentType) {
      super();
      this.genericComponentType = genericComponentType;
    }

    @Override
    public Type getGenericComponentType() {
      return genericComponentType;
    }
  }
}
