/**
 *    Copyright 2009-2019 the original author or authors.
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
package org.apache.ibatis.executor;

/**
 * 错误上下文、环境，用来存储已经发生错误的信息,主要被 ExceptionFactory 对象使用
 * ErrorContext 主要用来存储当前线程的错误信息，ExceptionFactory 使用 ErrorContext 当前线程中存储的错误信息，来包装成 RuntimeException 对象进行错误抛出。
 * 主要使用在三个方面：
 * 1. SqlSessionFactoryBuilder 中 build() 方法中解析 mybatis-config.xml、**.mapper 实体文件的错误，来构建 SqlSessionFactory 对象
 * 2. DefaultSqlSessionFactory 中获取 Session 对象的时候的错误
 * 3. DefaultSqlSession 对象中进行数据查询、更新、新增、删除等数据库操作时的错误，还有事务回滚、提交、SQL 刷新等
r Clinton Begin
 */
public class ErrorContext {

  private static final String LINE_SEPARATOR = System.getProperty("line.separator","\n");
  // 本地线程变量、线程安全
  private static final ThreadLocal<ErrorContext> LOCAL = new ThreadLocal<>();

  private ErrorContext stored;
  // 错误资源文件 **.mapper 实体 mapper 文件
  private String resource;
  // 错误事件 解析 **.mapper 文件中的 sqlNode 节点事件
  private String activity;
  // 存在错误的主体对象
  private String object;
  // 错误信息
  private String message;

  // 错误 SQL
  private String sql;
  // 错误原因
  private Throwable cause;

  private ErrorContext() {
  }

  public static ErrorContext instance() {
    ErrorContext context = LOCAL.get();
    if (context == null) {
      context = new ErrorContext();
      LOCAL.set(context);
    }
    return context;
  }

  /**
   * 把当前线程的 ErrorContext 对象，存储在新建的 newContext.stored 变量中，然后 newContext 变为 ThreadLocal 新的错误上下文对象
   * @return
   */
  public ErrorContext store() {
    ErrorContext newContext = new ErrorContext();
    newContext.stored = this;
    LOCAL.set(newContext);
    return LOCAL.get();
  }

  /**
   * 重新设置本地线程中的 ErrorContext 对象，为原来存储的 stored 中的 ErrorContext 对象
   * @return
   */
  public ErrorContext recall() {
    if (stored != null) {
      LOCAL.set(stored);
      stored = null;
    }
    return LOCAL.get();
  }

  public ErrorContext resource(String resource) {
    this.resource = resource;
    return this;
  }

  public ErrorContext activity(String activity) {
    this.activity = activity;
    return this;
  }

  public ErrorContext object(String object) {
    this.object = object;
    return this;
  }

  public ErrorContext message(String message) {
    this.message = message;
    return this;
  }

  public ErrorContext sql(String sql) {
    this.sql = sql;
    return this;
  }

  public ErrorContext cause(Throwable cause) {
    this.cause = cause;
    return this;
  }

  public ErrorContext reset() {
    resource = null;
    activity = null;
    object = null;
    message = null;
    sql = null;
    cause = null;
    LOCAL.remove();
    return this;
  }

  @Override
  public String toString() {
    StringBuilder description = new StringBuilder();

    // message
    if (this.message != null) {
      description.append(LINE_SEPARATOR);
      description.append("### ");
      description.append(this.message);
    }

    // resource
    if (resource != null) {
      description.append(LINE_SEPARATOR);
      description.append("### The error may exist in ");
      description.append(resource);
    }

    // object
    if (object != null) {
      description.append(LINE_SEPARATOR);
      description.append("### The error may involve ");
      description.append(object);
    }

    // activity
    if (activity != null) {
      description.append(LINE_SEPARATOR);
      description.append("### The error occurred while ");
      description.append(activity);
    }

    // sql
    if (sql != null) {
      description.append(LINE_SEPARATOR);
      description.append("### SQL: ");
      description.append(sql.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim());
    }

    // cause
    if (cause != null) {
      description.append(LINE_SEPARATOR);
      description.append("### Cause: ");
      description.append(cause.toString());
    }

    return description.toString();
  }

}
