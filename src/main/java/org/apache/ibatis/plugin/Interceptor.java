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
package org.apache.ibatis.plugin;

import java.util.Properties;

/**
 * 注册插件，通过拦截器使插件生效
 *
 * Mybatis 允许用户使用自定义拦截器对 SQL 语句执行过程中的某一点进行拦截。
 * 默认情况下， MyBatis 允许拦截器拦截 Executor 方法、 ParameterHandler 方法、 ResultSetHandler方法以及 StatementHandler 的方法。 具体可拦截 方法如下
 * Executor.update()方法、 query()方法、 flushStatements()方法、 commit()方法、 rollback()方法、 getTransaction()方法、 close()方法、 isClosed()方法。
 * ParameterHandler 中的 etParameterObject()方法、 setParameters()方法。
 * ResultSetHandler 中的 handleReultSets()方法、 handleOutputParameters()方法。
 * StatementHandler 中的 prepare()方法、 parameterize()方法、 batch()方法、 update()方法、query()方法
 * Mybatis 中使用的拦截器都需要实现 Interceptor 接口。 Interceptor 接口是 MyBatis 插件模块核心。
 *
 * 1.用户自定义拦截器，需要实现 Interceptor 接口，并在类上使用 @Interceptors 注解，然后具体拦截的方法使用 @Signature 注解
 *
 * @Interceptors({
 *   @Signature(type = Executor.class,method = "query",args = [MappedStatement.class,Object.class,RowBounds.class,ResultHandler.class]),
 *   @Signature(type = Executor.class,method = "close",args = [boolean.class])
 * })
 *
 * 2.然后在 mybatis-config.xml 中对拦截器进行配置
 *
 * <plugins>
 *   <plugin interceptor = "com.test.ExamplePlugin">
 *     <property name="testProp" value="100"/>
 *   </plugin>
 * </plugins>
 *
 * 3.在 Mybatis 初始化时，会通过 XMLConfigBuilder.pluginElement() 方法解析 mybatis-config.xml 配置文件中定义的<plugins> 节点，调用 Interceptor.setProperties() 初始化属性，然后添加到 Configuration.interceptorChain 插件链中。
 *
 * private void pluginElement(XNode parent) throws Exception {
 *     if (parent != null) {
 *       for (XNode child : parent.getChildren()) {
 *         String interceptor = child.getStringAttribute("interceptor");
 *         Properties properties = child.getChildrenAsProperties();
 *         // 创建插件实例,插件是 Interceptor 的具体实现
 *         Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).getDeclaredConstructor().newInstance();
 *         // 设置插件的属性
 *         interceptorInstance.setProperties(properties);
 *         // 设置插件到拦截器链中
 *         configuration.addInterceptor(interceptorInstance);
 *       }
 *     }
 *   }
 * 4. MyBatis 对 Executor、ParameterHandler、ResultSetHandler、StatementHandler 进行拦截。这四个对象都是 Configuration.new*() 系列创建的。
 *    其中会通过 interceptorChain.pluginAll(target) 为目标添加拦截器，然后返回被拦截对象 target 对象的代理。
 *
 *  public Executor newExecutor(Transaction transaction, ExecutorType executorType) {
 *     executorType = executorType == null ? defaultExecutorType : executorType;
 *     executorType = executorType == null ? ExecutorType.SIMPLE : executorType;
 *     Executor executor;
 *     if (ExecutorType.BATCH == executorType) {
 *       executor = new BatchExecutor(this, transaction);
 *     } else if (ExecutorType.REUSE == executorType) {
 *       executor = new ReuseExecutor(this, transaction);
 *     } else {
 *       executor = new SimpleExecutor(this, transaction);
 *     }
 *     // 是否开启二级缓存，装饰器模式
 *     if (cacheEnabled) {
 *       executor = new CachingExecutor(executor);
 *     }
 *     // 通过interceptorChain.pluginAll() 方法创建 Executor 的代理对象
 *     executor = (Executor) interceptorChain.pluginAll(executor);
 *     return executor;
 *   }
 *  5.interceptorChain.pluginAll() 调用  interceptor.plugin(target) 方法返回被拦截对象的代理对象。
 *  6.被代理对象被调用，如果不是拦截方法，则反射进行被拦截对象的方法调用；如果是拦截对象，调用拦截方法，然后调用被拦截对象的真实方法。
 * @author Clinton Begin
 */
public interface Interceptor {
  // 执行拦截逻辑的方法
  Object intercept(Invocation invocation) throws Throwable;
  // 为被拦截对象，装备拦截器，并返回代理对象
  default Object plugin(Object target) {
    return Plugin.wrap(target, this);
  }
  // 根据配置初始化 Interceptor 对象
  default void setProperties(Properties properties) {
    // NOP No Operation Performed
  }

}
