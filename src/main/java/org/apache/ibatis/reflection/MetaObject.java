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

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;
import org.apache.ibatis.reflection.wrapper.BeanWrapper;
import org.apache.ibatis.reflection.wrapper.CollectionWrapper;
import org.apache.ibatis.reflection.wrapper.MapWrapper;
import org.apache.ibatis.reflection.wrapper.ObjectWrapper;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;

/**
 * MetaObject 是对象层级的包装，对象的 getter、setter、filed、和他们的数据类型的方法。
 *
 *
 * MetaObject 是 Mybatis 提供的一个用于方便、优雅访问对象属性的对象，
 * 通过它可以简化代码、不需要 try/catch 各种 reflect 异常，同时它支持对 JavaBean、Collection、Map 三种类型对象的操作。
 *
 * MetaObject 包装了 objectWrapper，从而提供了对 对象属性 的操作。
 *
 * 反射包的大部分类都是为 MetaObject 服务的，这个是对外暴露的对象原信息操作的接口。
 * 服务类包括：Reflector、ReflectorFactory、MetaClass、ObjectFactory 接口和实现、Invoker 接口和实现、ObjectWrapper 接口和实现、Property 属性工具类。
 * 包装关系：MetaObject -> ObjectWrapper -> MetaClass -> Reflector -> 元类的基础信息（filed、getter、setter 方法、读写属性、成员的数据类型、方法的调用）
 *
 * @author Clinton Begin
 */
public class MetaObject {

  private final Object originalObject;  //原始JavaBean对象
  private final ObjectWrapper objectWrapper; //其中封装了originalObject对象
  private final ObjectFactory objectFactory; //创建对象的工厂
  private final ObjectWrapperFactory objectWrapperFactory; //创建封装对象的工厂
  private final ReflectorFactory reflectorFactory; //反射工厂

  private MetaObject(Object object, ObjectFactory objectFactory, ObjectWrapperFactory objectWrapperFactory, ReflectorFactory reflectorFactory) {
    this.originalObject = object;
    this.objectFactory = objectFactory;
    this.objectWrapperFactory = objectWrapperFactory;
    this.reflectorFactory = reflectorFactory;
    //赋值给objectWrapper
    if (object instanceof ObjectWrapper) {
      this.objectWrapper = (ObjectWrapper) object;
    } else if (objectWrapperFactory.hasWrapperFor(object)) {
      this.objectWrapper = objectWrapperFactory.getWrapperFor(this, object);
    } else if (object instanceof Map) {
      this.objectWrapper = new MapWrapper(this, (Map) object);
    } else if (object instanceof Collection) {
      this.objectWrapper = new CollectionWrapper(this, (Collection) object);
    } else {
      this.objectWrapper = new BeanWrapper(this, object);
    }
  }

  public static MetaObject forObject(Object object, ObjectFactory objectFactory, ObjectWrapperFactory objectWrapperFactory, ReflectorFactory reflectorFactory) {
    if (object == null) {
      //系统初始化，默认的对象
      return SystemMetaObject.NULL_META_OBJECT;
    } else {
      return new MetaObject(object, objectFactory, objectWrapperFactory, reflectorFactory);
    }
  }

  public ObjectFactory getObjectFactory() {
    return objectFactory;
  }

  public ObjectWrapperFactory getObjectWrapperFactory() {
    return objectWrapperFactory;
  }

  public ReflectorFactory getReflectorFactory() {
    return reflectorFactory;
  }

  public Object getOriginalObject() {
    return originalObject;
  }

  public String findProperty(String propName, boolean useCamelCaseMapping) {
    return objectWrapper.findProperty(propName, useCamelCaseMapping);
  }

  public String[] getGetterNames() {
    return objectWrapper.getGetterNames();
  }

  public String[] getSetterNames() {
    return objectWrapper.getSetterNames();
  }

  public Class<?> getSetterType(String name) {
    return objectWrapper.getSetterType(name);
  }

  public Class<?> getGetterType(String name) {
    return objectWrapper.getGetterType(name);
  }

  //判断属性表达式指定属性是 否有 setter 方法
  public boolean hasSetter(String name) {
    return objectWrapper.hasSetter(name);
  }
  //判断属性表达式指定属性是 否有 getter 方法
  public boolean hasGetter(String name) {
    return objectWrapper.hasGetter(name);
  }
  //获取对象属性
  public Object getValue(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaObject metaValue = metaObjectForProperty(prop.getIndexedName());
      if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
        return null;
      } else {
        return metaValue.getValue(prop.getChildren());
      }
    } else {
      return objectWrapper.get(prop);
    }
  }
  //设置对象属性
  public void setValue(String name, Object value) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaObject metaValue = metaObjectForProperty(prop.getIndexedName());
      if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
        if (value == null) {
          // don't instantiate child path if value is null
          return;
        } else {
          // 为属性表达式指定的属性创建相应的MetaObject对象
          metaValue = objectWrapper.instantiatePropertyValue(name, prop, objectFactory);
        }
      }
      metaValue.setValue(prop.getChildren(), value);
    } else {
      objectWrapper.set(prop, value);
    }
  }
  //根据属性名称获得MetaObject对象
  public MetaObject metaObjectForProperty(String name) {
    Object value = getValue(name);
    return MetaObject.forObject(value, objectFactory, objectWrapperFactory, reflectorFactory);
  }

  public ObjectWrapper getObjectWrapper() {
    return objectWrapper;
  }

  /**
   * 是否是集合
   * @return
   */
  public boolean isCollection() {
    return objectWrapper.isCollection();
  }

  /**
   * 向集合中添加元素
   * @param element
   */
  public void add(Object element) {
    objectWrapper.add(element);
  }

  /**
   * 向集合中添加多个元素
   * @param list
   * @param <E>
   */
  public <E> void addAll(List<E> list) {
    objectWrapper.addAll(list);
  }

}
