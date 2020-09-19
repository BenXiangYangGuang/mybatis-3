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
package org.apache.ibatis.reflection.invoker;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.ibatis.reflection.Reflector;

/**
 * 一个类的 getter 和 setter 方法的封装，方便反射调用，这个类的getter 和 setter 方法，进行 属性赋值
 * @author Clinton Begin
 */
public class MethodInvoker implements Invoker {

  // getter、setter 方法的类型
  private final Class<?> type; //java.lang.Long
  // getter、setter 方法
  private final Method method; //public java.lang.Long org.apache.ibatis.reflection.ReflectorTest$AbstractEntity.getId()

  public MethodInvoker(Method method) {
    this.method = method;
    //set()
    if (method.getParameterTypes().length == 1) {
      type = method.getParameterTypes()[0];
    } else {
      //get()
      type = method.getReturnType();
    }
  }

  /**
   * 调用 target 对象的 getter、setter 方法
   * @param target
   * @param args
   * @return
   * @throws IllegalAccessException
   * @throws InvocationTargetException
   */
  @Override
  public Object invoke(Object target, Object[] args) throws IllegalAccessException, InvocationTargetException {
    try {
      return method.invoke(target, args);
    } catch (IllegalAccessException e) {
      if (Reflector.canControlMemberAccessible()) {
        method.setAccessible(true);
        return method.invoke(target, args);
      } else {
        throw e;
      }
    }
  }

  /**
   * 返回 getter 方法的返回数据类型，setter 方法的参数类型
   * @return
   */
  @Override
  public Class<?> getType() {
    return type;
  }
}
