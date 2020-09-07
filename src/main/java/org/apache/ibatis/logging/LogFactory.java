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
package org.apache.ibatis.logging;

import java.lang.reflect.Constructor;

/**
 * 日志工厂，决定使用哪一个第三方日志
 * mybatis 采用适配器模式，适配了 slf4j，log4j 等多个日志框架；
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public final class LogFactory {

  /**
   * Marker to be used by logging implementations that support markers.
   */
  public static final String MARKER = "MYBATIS";

  private static Constructor<? extends Log> logConstructor; // 记录当前使用的第三方日志组件所对应的适配器的构造方法
  // 以此从上之下，尝试初始化第三方日志模块，直到 logConstructor ！= null
  static {
    // 函数式接口，执行顺序 tryImplementation，然后执行 函数表达式，再执行函数表达式中的 setImplementation()
    tryImplementation(LogFactory::useSlf4jLogging);
    tryImplementation(LogFactory::useCommonsLogging);
    tryImplementation(LogFactory::useLog4J2Logging);
    tryImplementation(LogFactory::useLog4JLogging);
    tryImplementation(LogFactory::useJdkLogging);
    tryImplementation(LogFactory::useNoLogging);
  }

  private LogFactory() {
    // disable construction
  }

  /**
   * 返回 log 对象
   * @param aClass 使用 log 对象的类名称
   * @return
   */
  public static Log getLog(Class<?> aClass) {
    return getLog(aClass.getName());
  }

  /**
   * 根据一个类名称，返回一个 Log 对象
   * @param logger
   * @return
   */
  public static Log getLog(String logger) {
    try {
      return logConstructor.newInstance(logger);
    } catch (Throwable t) {
      throw new LogException("Error creating logger for logger " + logger + ".  Cause: " + t, t);
    }
  }

  public static synchronized void useCustomLogging(Class<? extends Log> clazz) {
    setImplementation(clazz);
  }

  public static synchronized void useSlf4jLogging() {
    setImplementation(org.apache.ibatis.logging.slf4j.Slf4jImpl.class);
  }

  public static synchronized void useCommonsLogging() {
    setImplementation(org.apache.ibatis.logging.commons.JakartaCommonsLoggingImpl.class);
  }

  public static synchronized void useLog4JLogging() {
    setImplementation(org.apache.ibatis.logging.log4j.Log4jImpl.class);
  }

  public static synchronized void useLog4J2Logging() {
    setImplementation(org.apache.ibatis.logging.log4j2.Log4j2Impl.class);
  }

  public static synchronized void useJdkLogging() {
    setImplementation(org.apache.ibatis.logging.jdk14.Jdk14LoggingImpl.class);
  }

  public static synchronized void useStdOutLogging() {
    setImplementation(org.apache.ibatis.logging.stdout.StdOutImpl.class);
  }

  public static synchronized void useNoLogging() {
    setImplementation(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
  }

  /**
   * 如果 logConstructor == null，就不会再执行 run(),
   * 在 本类上面 static 代码块，会多次调用这个方法，根据调用顺序优先级，只会初始化一个 logConstructor，logConstructor 只会被一个三方日志中的一个初始化。
   * @param runnable 作为 java8 的新特性，函数式接口；和 Thread 没有半毛钱关系；
   *
   */
  private static void tryImplementation(Runnable runnable) {
    if (logConstructor == null) {
      try {
        // run() 函数式接口中方法的执行，比如这个 LogFactory::useSlf4jLogging lambda 表达式
        runnable.run();
      } catch (Throwable t) {
        // ignore
      }
    }
  }

  private static void setImplementation(Class<? extends Log> implClass) {
    try {
      //  三方日志的带有 String 参数的构造器，通过构造器创建 log 实例，并 赋值给 logConstructor
      Constructor<? extends Log> candidate = implClass.getConstructor(String.class);
      // LogFactory.class.getName() 仅作为 log 具体实现类中构造函数的参数；
      Log log = candidate.newInstance(LogFactory.class.getName());
      if (log.isDebugEnabled()) {
        log.debug("Logging initialized using '" + implClass + "' adapter.");
      }
      logConstructor = candidate;
    } catch (Throwable t) {
      throw new LogException("Error setting Log implementation.  Cause: " + t, t);
    }
  }

}
