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
package org.apache.ibatis.type;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * 类型引用
 * 3.1新加的类型引用,为了引用一个泛型类型
 * 参考一个基础类型
 * References a generic type.
 * 这个类型引用的作用是用于获取原生类型，Java中的原生类型又称为基本类型，即byte、short、int、long、float、double、boolean、char八大基本数据类型。

 * 这个类型引用的目的，它就是为了持有这个具体的类型处理器所处理的Java类型的原生类型。
 * 我们可以看到在该类中还有两个方法getRawType()和toString()方法，这两个方法都是public修饰的，是对外公开的方法，那么也就意味着这个原生类型是为了被外部调用而设。
 * https://www.cnblogs.com/V1haoge/p/6715063.html
 * @param <T> the referenced type
 * @since 3.1.0
 * @author Simone Tripodi
 */
public abstract class TypeReference<T> {
  //java中类型的接口，Class
  //引用的原生类型
  private final Type rawType;

  protected TypeReference() {
    //getClass() Object类中定义的方法 返回当前类（实例）的类类型
    rawType = getSuperclassTypeParameter(getClass());
  }

  /**
   *
   * @param clazz org.apache.ibatis.type.IntegerTypeHandler
   * @return
   */
  Type getSuperclassTypeParameter(Class<?> clazz) {
    //获取到此类的superclass  org.apache.ibatis.type.BaseTypeHandler<java.lang.Integer>
    Type genericSuperclass = clazz.getGenericSuperclass();

    //type include Class ParameterizedType TypeVariable GenericArrayType
    if (genericSuperclass instanceof Class) {
      // try to climb up the hierarchy until meet something useful
      if (TypeReference.class != genericSuperclass) {
        return getSuperclassTypeParameter(clazz.getSuperclass());
      }

      throw new TypeException("'" + getClass() + "' extends TypeReference but misses the type parameter. "
        + "Remove the extension or add a type parameter to it.");
    }
    //获取泛型<T>中的T类型 java.lang.Integer
    Type rawType = ((ParameterizedType) genericSuperclass).getActualTypeArguments()[0];
    // TODO remove this when Reflector is fixed to return Types
    if (rawType instanceof ParameterizedType) {
      rawType = ((ParameterizedType) rawType).getRawType();
    }

    return rawType;
  }

  public final Type getRawType() {
    return rawType;
  }

  @Override
  public String toString() {
    return rawType.toString();
  }

}
