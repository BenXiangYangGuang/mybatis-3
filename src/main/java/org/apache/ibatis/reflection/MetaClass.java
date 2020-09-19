/**
 *    Copyright 2009-2020 the original author or authors.
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
 * @author Clinton Begin
 * MetaClass 是对 Reflector 的封装，是类层次的信息封装
 *
 * MetaClass 通过 Reflector 和 PropertyTokenizer 组合使用, 实现了对复杂的属性表达式的解析,并实现了获取指定属性描述信息的功能。
 * 主要实现了 1.检测是否包含这个复杂的属性表达式，2.属性表达式的返回数据类型 3.返回 Reflector 对象的集合信息们。
 */
public class MetaClass {
  //ReflectorFactory 对象,用于缓存 Reflector 对象
  private final ReflectorFactory reflectorFactory;
  //在创建 MetaClass 时会指定一个类,该 Reflector 对象会用于记录该类相关 的元信息
  private final Reflector reflector;

  private MetaClass(Class<?> type, ReflectorFactory reflectorFactory) {
    this.reflectorFactory = reflectorFactory;
    this.reflector = reflectorFactory.findForClass(type);
  }

  public static MetaClass forClass(Class<?> type, ReflectorFactory reflectorFactory) {
    return new MetaClass(type, reflectorFactory);
  }
  /**
   * 根据属性名字，创建这个属性 数据类型的 MetaClass 对象
   */
  public MetaClass metaClassForProperty(String name) {
    Class<?> propType = reflector.getGetterType(name);
    return MetaClass.forClass(propType, reflectorFactory);
  }

  /**
   * 查找是否有相应的 name(user.address.country) 复杂属性表达式,并返回；只进行了 . 这样复杂属性的导航，并没有对下表进行解析
   * @param name
   * @return
   */
  public String findProperty(String name) {
    // 构建的属性信息 user.address.country
    StringBuilder prop = buildProperty(name, new StringBuilder());
    return prop.length() > 0 ? prop.toString() : null;
  }

  /**
   * 查找是否有相应的 name 属性表达式,并返回
   * @param name
   * @param useCamelCaseMapping 第二个参数表示是否忽略属性表达式中的下画线
   * @return
   */
  public String findProperty(String name, boolean useCamelCaseMapping) {
    if (useCamelCaseMapping) {
      name = name.replace("_", "");
    }
    return findProperty(name);
  }

  /**
   * 获取类的可读属性们
   * @return
   */
  public String[] getGetterNames() {
    return reflector.getGetablePropertyNames();
  }
  /**
   * 获取类的可写属性们
   * @return
   */
  public String[] getSetterNames() {
    return reflector.getSetablePropertyNames();
  }

  /**
   * 获取属性表达式的数据类型
   * @param name
   * @return
   */
  public Class<?> getSetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop.getName());
      return metaProp.getSetterType(prop.getChildren());
    } else {
      return reflector.getSetterType(prop.getName());
    }
  }

  /**
   * 获取类字段 get 方法的返回值类型
   * @param name
   * @return
   */
  public Class<?> getGetterType(String name) {
    // 新建属性表达式解析器
    PropertyTokenizer prop = new PropertyTokenizer(name);
    // 是否有孩子节点
    if (prop.hasNext()) {
      //
      MetaClass metaProp = metaClassForProperty(prop);
      return metaProp.getGetterType(prop.getChildren());
    }
    // issue #506. Resolve the type inside a Collection Object
    return getGetterType(prop);
  }

  /**
   * 根据包含属性表达式的 PropertyTokenizer 解析器，创建属性的 MetaClass 对象
   * @param prop
   * @return
   */
  private MetaClass metaClassForProperty(PropertyTokenizer prop) {
    Class<?> propType = getGetterType(prop);
    return MetaClass.forClass(propType, reflectorFactory);
  }

  /**
   * 根据包含属性的 PropertyTokenizer 对象，返回属性表达式对应的数据类型
   * @param prop
   * @return
   */
  private Class<?> getGetterType(PropertyTokenizer prop) {
    // 返回属性数据类型
    Class<?> type = reflector.getGetterType(prop.getName());
    // 该表达式中是否使用 [] 下表，且是 Collection 子类
    if (prop.getIndex() != null && Collection.class.isAssignableFrom(type)) {
      // 通过 TypeParameterResolver 工具类解析属性的类
      Type returnType = getGenericGetterType(prop.getName());
      // 针对 ParameterizedType 进行处理，即针对泛型集合类型进行处理
      if (returnType instanceof ParameterizedType) {
        // 获取实际的参数类型
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

  /**
   *
   * @param propertyName
   * @return
   */
  private Type getGenericGetterType(String propertyName) {
    try {
      // 根据 Reflector.getMethods 集合中记录的 Invoker 实现类的类型，决定解析 getter 方法返回值类型，还是解析字段类型
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
    } catch (NoSuchFieldException | IllegalAccessException ignored) {
    }
    return null;
  }

  /**
   * 检查被包装类的属性表达式值是否有Setter()方法
   * @param name
   * @return
   */
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
  /**
   * 检查被包装类的属性表达式值是否有 Getter()方法，比如 orders[0].id 属性表达式
   * @param name
   * @return
   */
  public boolean hasGetter(String name) {
    // 解析属性表达式
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) { // 存在子表达式
      if (reflector.hasGetter(prop.getName())) {
        // 创建 属性的 MetaClass 对象，递归处理
        MetaClass metaProp = metaClassForProperty(prop);
        return metaProp.hasGetter(prop.getChildren());
      } else {
        return false;
      }
    } else {
      return reflector.hasGetter(prop.getName());
    }
  }

  /**
   * 获取 get 字段 封装的 Invoker 对象
   * @param name
   * @return
   */
  public Invoker getGetInvoker(String name) {
    return reflector.getGetInvoker(name);
  }

  /**
   * 获取 set 字段 封装的 Invoker 对象
   * @param name
   * @return
   */
  public Invoker getSetInvoker(String name) {
    return reflector.getSetInvoker(name);
  }

  /**
   * 构建多个属性信息
   * 根据属性表达式名称，比如：user.address.country，解析递归，查找在多个类中的多个属性信息，然后拼接属性信息，返回 StringBuilder 对象
   * @param name
   * @param builder
   * @return
   */
  private StringBuilder buildProperty(String name, StringBuilder builder) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    // 判断 name 属性是否 包含 . ，即是否包含子节点
    if (prop.hasNext()) {
      // 如果包含子节点，获取父节点的属性名称
      String propertyName = reflector.findPropertyName(prop.getName());
      if (propertyName != null) {
        // 添加到 builder 中
        builder.append(propertyName);
        builder.append(".");
        // 创建这个属性数据类型的的 MetaClass 对象
        MetaClass metaProp = metaClassForProperty(propertyName);
        // 然后根据父节点的 MetaClass 对象，查找子节点的属性，进行递归查询
        metaProp.buildProperty(prop.getChildren(), builder);
      }
    } else {
      // 不包含，然后获取属性名称
      String propertyName = reflector.findPropertyName(name);
      if (propertyName != null) {
        builder.append(propertyName);
      }
    }
    return builder;
  }
  // 是否有默认的构造方法
  public boolean hasDefaultConstructor() {
    return reflector.hasDefaultConstructor();
  }

}
