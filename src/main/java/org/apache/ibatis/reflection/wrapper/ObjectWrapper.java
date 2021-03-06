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
package org.apache.ibatis.reflection.wrapper;

import java.util.List;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * @author Clinton Begin
 * ObjectWrapper 是对对象的包装的接口，抽象了对象的字段信息、 getter| setter 方法、和上面三个成员的数据类型,它定义了一系列查询对象属性信息的方法,以及更新属性的方法 。
 * 继承关系
 *            ObjectWapper
 *      BaseWrapper       CollectionWrapper
 * BeanWarpper MapWarpper
 */
public interface ObjectWrapper {
  // 如采 ObjectWrapper 中封装的是普通的 Bean 对象 ,则调用相应属性的相应 getter 方法,
  // 如采封装的是集合类,则获取指定 key 或下标对应的 value 位
  Object get(PropertyTokenizer prop);

  // 如果 ObjectWrapper 中封装的是普通的 Bean 对象,则调用相应属性的相应 setter 方法 ,
  // 如果封装的是集合类,则设置指定 key 或下标对应的 value 值
  void set(PropertyTokenizer prop, Object value);

  // 查找属性表达式指定的属性,第二个参数表示是否忽略属性表达式中的下画线
  String findProperty(String name, boolean useCamelCaseMapping);

  String[] getGetterNames();//查找可写属性的名称集合

  String[] getSetterNames(); //查找可读属性的名称集合
  //解析属性表达式指定属性的 setter方法 的参数类型
  Class<?> getSetterType(String name);
  //解析属性表达式指定属性的 getter方法 的返回值类型
  Class<?> getGetterType(String name);
  //判断属性表达式指定属性是 否有 getter/setter 方法
  boolean hasSetter(String name);

  boolean hasGetter(String name);
  // instantiate 例示，举例说明
  // 为属性表达式指定的属性创建相应的MetaObject对象
  MetaObject instantiatePropertyValue(String name, PropertyTokenizer prop, ObjectFactory objectFactory);

  boolean isCollection(); // 封装的对象是否为Collection类型

  void add(Object element); //调用Collection对象的add()方法

  <E> void addAll(List<E> element); //调用Collection对象的addAll()方法

}
