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
package org.apache.ibatis.reflection.wrapper;

import java.util.List;

import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ReflectionException;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * BeanWrapper 是对象的信息封装，包括 getter、setter 方法、字段、及(getter、setter 方法、字段)的返回数据类型。
 * 包装了一个 object 对象，还有这个对象所属类的 MetaClass 对象，然后包装了 MetaClass 对象的方法，对外提供方法调用。
 * @author Clinton Begin
 */
public class BeanWrapper extends BaseWrapper {

  private final Object object;
  private final MetaClass metaClass;

  public BeanWrapper(MetaObject metaObject, Object object) {
    super(metaObject);
    this.object = object;
    this.metaClass = MetaClass.forClass(object.getClass(), metaObject.getReflectorFactory());
  }

  /**
   * 获取属性表达式的值
   * @param prop
   * @return
   */
  @Override
  public Object get(PropertyTokenizer prop) {
    // 存在索引信息，则表示属性表达式中的 name 部分为集合属性
    if (prop.getIndex() != null) {
      // 通过 MetaObject.getValue() 方法获取 Object 对象中的指定集合属性
      Object collection = resolveCollection(prop, object);
      return getCollectionValue(prop, collection);
    } else {
      // name 属性为普通对象，查找并调用 Invoker 相关方法获取属性
      return getBeanProperty(prop, object);
    }
  }

  /**
   * 设置属性表达式的值
   * @param prop
   * @param value
   */
  @Override
  public void set(PropertyTokenizer prop, Object value) {
    if (prop.getIndex() != null) {
      Object collection = resolveCollection(prop, object);
      setCollectionValue(prop, collection, value);
    } else {
      setBeanProperty(prop, object, value);
    }
  }

  /**
   * 是否包含这个属性表达式
   * @param name
   * @param useCamelCaseMapping
   * @return
   */
  @Override
  public String findProperty(String name, boolean useCamelCaseMapping) {
    return metaClass.findProperty(name, useCamelCaseMapping);
  }

  /**
   * 获取对象的可读属性们
   * @return
   */
  @Override
  public String[] getGetterNames() {
    return metaClass.getGetterNames();
  }

  /**
   * 获取对象的可写属性们
   * @return
   */
  @Override
  public String[] getSetterNames() {
    return metaClass.getSetterNames();
  }

  /**
   * 属性表达式 set 方法参数类型
   * @param name
   * @return
   */
  @Override
  public Class<?> getSetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaObject metaValue = metaObject.metaObjectForProperty(prop.getIndexedName());
      if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
        return metaClass.getSetterType(name);
      } else {
        return metaValue.getSetterType(prop.getChildren());
      }
    } else {
      return metaClass.getSetterType(name);
    }
  }

  /**
   * 属性表达式的 get 方法的返回值类型
   * @param name
   * @return
   */
  @Override
  public Class<?> getGetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaObject metaValue = metaObject.metaObjectForProperty(prop.getIndexedName());
      if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
        return metaClass.getGetterType(name);
      } else {
        return metaValue.getGetterType(prop.getChildren());
      }
    } else {
      return metaClass.getGetterType(name);
    }
  }

  /**
   * 是否包含属性表达式的 setter 方法
   * @param name
   * @return
   */
  @Override
  public boolean hasSetter(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      if (metaClass.hasSetter(prop.getIndexedName())) {
        MetaObject metaValue = metaObject.metaObjectForProperty(prop.getIndexedName());
        if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
          return metaClass.hasSetter(name);
        } else {
          return metaValue.hasSetter(prop.getChildren());
        }
      } else {
        return false;
      }
    } else {
      return metaClass.hasSetter(name);
    }
  }

  /**
   * 是否包含表达式的 getter 方法
   * @param name
   * @return
   */
  @Override
  public boolean hasGetter(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      if (metaClass.hasGetter(prop.getIndexedName())) {
        MetaObject metaValue = metaObject.metaObjectForProperty(prop.getIndexedName());
        if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
          return metaClass.hasGetter(name);
        } else {
          return metaValue.hasGetter(prop.getChildren());
        }
      } else {
        return false;
      }
    } else {
      return metaClass.hasGetter(name);
    }
  }

  /**
   * 为属性表达式指定的属性创建相应的 MetaObject 对象
   * @param name
   * @param prop
   * @param objectFactory
   * @return
   */
  @Override
  public MetaObject instantiatePropertyValue(String name, PropertyTokenizer prop, ObjectFactory objectFactory) {
    MetaObject metaValue;
    Class<?> type = getSetterType(prop.getName());
    try {
      Object newObject = objectFactory.create(type);
      metaValue = MetaObject.forObject(newObject, metaObject.getObjectFactory(), metaObject.getObjectWrapperFactory(), metaObject.getReflectorFactory());
      set(prop, newObject);
    } catch (Exception e) {
      throw new ReflectionException("Cannot set value of property '" + name + "' because '" + name + "' is null and cannot be instantiated on instance of " + type.getName() + ". Cause:" + e.toString(), e);
    }
    return metaValue;
  }

  /**
   * 调用对象的 getter 方法，获取对象属性值
   * @param prop
   * @param object
   * @return
   */
  private Object getBeanProperty(PropertyTokenizer prop, Object object) {
    try {
      Invoker method = metaClass.getGetInvoker(prop.getName());
      try {
        return method.invoke(object, NO_ARGUMENTS);
      } catch (Throwable t) {
        throw ExceptionUtil.unwrapThrowable(t);
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Throwable t) {
      throw new ReflectionException("Could not get property '" + prop.getName() + "' from " + object.getClass() + ".  Cause: " + t.toString(), t);
    }
  }

  /**
   * 调用对象的 setter 方法，设置对象属性值
   * @param prop
   * @param object
   * @param value
   */
  private void setBeanProperty(PropertyTokenizer prop, Object object, Object value) {
    try {
      Invoker method = metaClass.getSetInvoker(prop.getName());
      Object[] params = {value};
      try {
        method.invoke(object, params);
      } catch (Throwable t) {
        throw ExceptionUtil.unwrapThrowable(t);
      }
    } catch (Throwable t) {
      throw new ReflectionException("Could not set property '" + prop.getName() + "' of '" + object.getClass() + "' with value '" + value + "' Cause: " + t.toString(), t);
    }
  }

  /**
   * 对象不是 Collection ，返回 False
   * @return
   */
  @Override
  public boolean isCollection() {
    return false;
  }

  /**
   * 对象不支持 add，集合支持
   * @param element
   */
  @Override
  public void add(Object element) {
    throw new UnsupportedOperationException();
  }
  /**
   * 对象不支持 addAll，集合支持
   * @param list
   */
  @Override
  public <E> void addAll(List<E> list) {
    throw new UnsupportedOperationException();
  }

}
