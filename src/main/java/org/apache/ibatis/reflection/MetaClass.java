/**
 *    Copyright 2009-2017 the original author or authors.
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * 元类
 *
 * @author Clinton Begin
 */
public class MetaClass {

  /** 用于缓存 Reflector 对象 */
  private final ReflectorFactory reflectorFactory;

  /** 在创建 MetaClass 时会指定一个类，该 Reflector 对象会用于记录该类相关的元信息 */
  private final Reflector reflector;

  /**
   * MetaClass 的构造方法是使用 private 修饰的
   */
  private MetaClass(Class<?> type, ReflectorFactory reflectorFactory) {
    this.reflectorFactory = reflectorFactory;
    this.reflector = reflectorFactory.findForClass(type);
  }

  /**
   * 使用静态方法创建 MetaClass 对象
   */
  public static MetaClass forClass(Class<?> type, ReflectorFactory reflectorFactory) {
    return new MetaClass(type, reflectorFactory);
  }

  public MetaClass metaClassForProperty(String name) {
    // 查找指定属性对应的 Class
    Class<?> propType = reflector.getGetterType(name);
    // 为该属性创建对应的 MetaClass 对象
    return MetaClass.forClass(propType, reflectorFactory);
  }

  /**
   * 【重要方法】
   */
  public String findProperty(String name) {
    StringBuilder prop = buildProperty(name, new StringBuilder());
    return prop.length() > 0 ? prop.toString() : null;
  }

  public String findProperty(String name, boolean useCamelCaseMapping) {
    if (useCamelCaseMapping) {
      name = name.replace("_", "");
    }
    return findProperty(name);
  }

  public String[] getGetterNames() {
    return reflector.getGetablePropertyNames();
  }

  public String[] getSetterNames() {
    return reflector.getSetablePropertyNames();
  }

  public Class<?> getSetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop.getName());
      return metaProp.getSetterType(prop.getChildren());
    } else {
      return reflector.getSetterType(prop.getName());
    }
  }

  public Class<?> getGetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop);
      return metaProp.getGetterType(prop.getChildren());
    }
    // issue #506. Resolve the type inside a Collection Object
    return getGetterType(prop);
  }

  private MetaClass metaClassForProperty(PropertyTokenizer prop) {
    // 获取表达式所表示的属性的类型
    Class<?> propType = getGetterType(prop);
    return MetaClass.forClass(propType, reflectorFactory);
  }

  private Class<?> getGetterType(PropertyTokenizer prop) {
    // 获取属性类型
    Class<?> type = reflector.getGetterType(prop.getName());
    // 该表达式中是否使用"[]"指定了下标，且是 collection 子类
    if (prop.getIndex() != null && Collection.class.isAssignableFrom(type)) {
      // 通过 TypeParameterResolver 工具类解析属性的类型
      Type returnType = getGenericGetterType(prop.getName());
      // 针对 ParameterizedType 进行处理，即针对泛型集合类型进行处理
      if (returnType instanceof ParameterizedType) {
        // 获取实际的类型参数
        Type[] actualTypeArguments = ((ParameterizedType) returnType).getActualTypeArguments();
        if (actualTypeArguments != null && actualTypeArguments.length == 1) {
          // 泛型的类型
          returnType = actualTypeArguments[0];
          if (returnType instanceof Class) {
            type = (Class<?>) returnType;
          } else if (returnType instanceof ParameterizedType) {
            type = (Class<?>) ((ParameterizedType) returnType).getRawType();
          }
        }
      }
    }
    return type;
  }

  private Type getGenericGetterType(String propertyName) {
    try {
      /*
          根据 Reflector.getMethods 集合中记录的 Invoker 实现类的类型，决定解析 getter 方法返回值
          类型还是解析字段类型
       */
      Invoker invoker = reflector.getGetInvoker(propertyName);
      if (invoker instanceof MethodInvoker) {
        Field _method = MethodInvoker.class.getDeclaredField("method");
        _method.setAccessible(true);
        Method method = (Method) _method.get(invoker);
        return TypeParameterResolver.resolveReturnType(method, reflector.getType());
      } else if (invoker instanceof GetFieldInvoker) {
        Field _field = GetFieldInvoker.class.getDeclaredField("field");
        _field.setAccessible(true);
        Field field = (Field) _field.get(invoker);
        return TypeParameterResolver.resolveFieldType(field, reflector.getType());
      }
    } catch (NoSuchFieldException e) {
    } catch (IllegalAccessException e) {
    }
    return null;
  }

  public boolean hasSetter(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      if (reflector.hasSetter(prop.getName())) {
        MetaClass metaProp = metaClassForProperty(prop.getName());
        return metaProp.hasSetter(prop.getChildren());
      } else {
        return false;
      }
    } else {
      return reflector.hasSetter(prop.getName());
    }
  }

  public boolean hasGetter(String name) {
    // 解析属性表达式
    PropertyTokenizer prop = new PropertyTokenizer(name);
    // 存在待处理的子表达式
    if (prop.hasNext()) {
      // PropertyTokenizer.name 指定的属性有 getter 方法，才能处理表达式
      if (reflector.hasGetter(prop.getName())) {
        /*
            注意，这里的 metaClassForProperty(PropertyTokenizer) 方法是上面介绍
            的 metaClassForProperty(String) 方法的重载，但两者逻辑相差有点大
         */
        MetaClass metaProp = metaClassForProperty(prop);
        // 递归入口
        return metaProp.hasGetter(prop.getChildren());
      } else {
        // 递归出口
        return false;
      }
    } else {
      // 递归出口
      return reflector.hasGetter(prop.getName());
    }
  }

  public Invoker getGetInvoker(String name) {
    return reflector.getGetInvoker(name);
  }

  public Invoker getSetInvoker(String name) {
    return reflector.getSetInvoker(name);
  }

  /**
   * 【重要方法】
   * 将类属性转换为表达式，如 类： User.tele.num
   * 递归调用  append("User").append(".").append("tele").append(".").append("num)
   */
  private StringBuilder buildProperty(String name, StringBuilder builder) {
    // 解析属性表达式
    PropertyTokenizer prop = new PropertyTokenizer(name);
    // 是否含有子表达式
    if (prop.hasNext()) {
      // 查找 PropertyTokenizer.name 对应的属性
      String propertyName = reflector.findPropertyName(prop.getName());
      if (propertyName != null) {
        // 追加属性名
        builder.append(propertyName);
        builder.append(".");
        // 为该属性创建对应的 MetaClass 对象
        MetaClass metaProp = metaClassForProperty(propertyName);
        // 递归解析 PropertyTokenizer.children 字段，并将解析结果添加到 builder 中保存
        metaProp.buildProperty(prop.getChildren(), builder);
      }
    } else {
      String propertyName = reflector.findPropertyName(name);
      if (propertyName != null) {
        builder.append(propertyName);
      }
    }
    return builder;
  }

  public boolean hasDefaultConstructor() {
    return reflector.hasDefaultConstructor();
  }

}
