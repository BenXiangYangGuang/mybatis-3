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
package org.apache.ibatis.executor;

import static org.apache.ibatis.executor.ExecutionPlaceholder.EXECUTION_PLACEHOLDER;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.statement.StatementUtil;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.logging.jdbc.ConnectionLogger;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * BaseExecutor 是一个实现了 Executor 接口的抽象类，它实现了 Executor 接口的大部分方法，其中就使用了模板方法模式。
 * BaseExecutor 主要提供了缓存管理和事务管理的基本功能，继承 BaseExecutor 子类只要实现四个基本方法来完成数据库的相关操作即可，
 * 这四个方法分别 doUpdate()方法、 doQuery() 方法、 doQueryCursor() 方法、 doFlushStatement() 方法，其余的功能在 BaseExecutor 实现。
 * @author Clinton Begin
 */
public abstract class BaseExecutor implements Executor {

  private static final Log log = LogFactory.getLog(BaseExecutor.class);
  // 事务对象
  protected Transaction transaction;
  // 封装的 executor 对象
  protected Executor wrapper;
  //延迟加载队列
  protected ConcurrentLinkedQueue<DeferredLoad> deferredLoads;

  //一级缓存，也叫永久缓存，用于缓存该 Executor 对象查询结果集映射得到的结采对象，
  protected PerpetualCache localCache;
  //一级缓存，用于缓存 输出类型的参数
  protected PerpetualCache localOutputParameterCache;
  protected Configuration configuration;

  // 用来记录嵌套查询的层数
  protected int queryStack;
  private boolean closed; // executor status

  protected BaseExecutor(Configuration configuration, Transaction transaction) {
    this.transaction = transaction;
    this.deferredLoads = new ConcurrentLinkedQueue<>();
    this.localCache = new PerpetualCache("LocalCache");
    this.localOutputParameterCache = new PerpetualCache("LocalOutputParameterCache");
    this.closed = false;
    this.configuration = configuration;
    this.wrapper = this;
  }

  @Override
  public Transaction getTransaction() {
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    return transaction;
  }

  //session.close();真实的是executor.close()
  @Override
  public void close(boolean forceRollback) {
    try {
      try {
        // 回滚
        rollback(forceRollback);
      } finally {
        if (transaction != null) {
          transaction.close();
        }
      }
    } catch (SQLException e) {
      // Ignore.  There's nothing that can be done at this point.
      log.warn("Unexpected exception on closing transaction.  Cause: " + e);
      //异常之后,清空所有session资源,并关闭session closed = true;
    } finally {
      // 清楚关于这个 查询的所有参数
      transaction = null;
      deferredLoads = null;
      localCache = null;
      localOutputParameterCache = null;
      closed = true;
    }
  }

  @Override
  public boolean isClosed() {
    return closed;
  }

  @Override
  public int update(MappedStatement ms, Object parameter) throws SQLException {
    ErrorContext.instance().resource(ms.getResource()).activity("executing an update").object(ms.getId());
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    clearLocalCache();
    return doUpdate(ms, parameter);
  }

  @Override
  public List<BatchResult> flushStatements() throws SQLException {
    return flushStatements(false);
  }

  /**
   * 批处理多条 SQL 语句的刷新，提交
   * @param isRollBack 是否执行 Executor 中缓存的 SQL 语句，false 表示执行， true 表示不执行
   * @return
   * @throws SQLException
   */
  public List<BatchResult> flushStatements(boolean isRollBack) throws SQLException {
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    return doFlushStatements(isRollBack);
  }

  @Override
  public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException {
    // 获取 BoundSql 对象
    BoundSql boundSql = ms.getBoundSql(parameter);
    // 创建 select 语句的缓存 key
    CacheKey key = createCacheKey(ms, parameter, rowBounds, boundSql);
    return query(ms, parameter, rowBounds, resultHandler, key, boundSql);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql) throws SQLException {
    ErrorContext.instance().resource(ms.getResource()).activity("executing a query").object(ms.getId());
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    // 非嵌套查询，并且 <select> 节点配置的 flushCacheRequired 属性为 true 时，才会清空一级缓存
    if (queryStack == 0 && ms.isFlushCacheRequired()) {
      clearLocalCache();
    }
    List<E> list;
    try {
      // 增加查询层数
      queryStack++;
      // TODO: 2020/9/2 resultHandler queryStack
      // 查询一级缓存
      list = resultHandler == null ? (List<E>) localCache.getObject(key) : null;
      if (list != null) {
        // 针对存储过程调用的处理，其功能是：在一级缓存命中时，获取缓存中保存的输出类型参数，并设置到用户传入的实参(parameter)对象中
        handleLocallyCachedOutputParameters(ms, key, parameter, boundSql);
      } else {
        //缓存没有,从数据库查询
        list = queryFromDatabase(ms, parameter, rowBounds, resultHandler, key, boundSql);
      }
    } finally {
      // 当前查询完成，查询层数减少
      queryStack--;
    }
    // 在最外层的查询结束时，所有嵌套查询也已经完全加载，所以这里可以触发 DeferredLoad 加载一级缓存中记录的嵌套查询的结果对象
    if (queryStack == 0) {
      // 加载 延迟加载的对象，deferredLoads 延迟加载队列
      for (DeferredLoad deferredLoad : deferredLoads) {
        deferredLoad.load();
      }
      // issue #601
      deferredLoads.clear(); // 清楚 deferredLoads 队列
      // 生命周期为 STATEMENT，清除一级缓存
      if (configuration.getLocalCacheScope() == LocalCacheScope.STATEMENT) {
        // issue #482
        clearLocalCache();
      }
    }
    return list;
  }

  /**
   * 游标查询数据库，它不会直接将结果映射为结果对象，而是将结果集封装成 Cursor 对象并返回，待用户遍历 Cursor 时才真正完成结果集的映射操作
   * @param ms
   * @param parameter
   * @param rowBounds
   * @param <E>
   * @return
   * @throws SQLException
   */
  @Override
  public <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException {
    BoundSql boundSql = ms.getBoundSql(parameter);
    return doQueryCursor(ms, parameter, rowBounds, boundSql);
  }

  /**
   * 创建 DeferredLoad 对象，并添加到 deferredLoads 队列中
   * @param ms
   * @param resultObject
   * @param property
   * @param key
   * @param targetType
   */
  @Override
  public void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key, Class<?> targetType) {
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    DeferredLoad deferredLoad = new DeferredLoad(resultObject, property, key, localCache, configuration, targetType);
    if (deferredLoad.canLoad()) {
      // 一级缓存中已经记录了指定查询的结果对象，直接从缓存中加载对象，并设置到外层对象中
      deferredLoad.load();
    } else {
      // 将 DeferredLoad 对象添加到 deferredLoads 队列中，待整个外层查询结束后，再加载该结果对象
      deferredLoads.add(new DeferredLoad(resultObject, property, key, localCache, configuration, targetType));
    }
  }
  //根据查询语句的不同,生成特别的唯一的key
  @Override
  public CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql) {
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    // 创建 CacheKey 对象
    CacheKey cacheKey = new CacheKey();
    // MappedStatement 的 id
    cacheKey.update(ms.getId());
    // offset
    cacheKey.update(rowBounds.getOffset());
    // 偏移量
    cacheKey.update(rowBounds.getLimit());
    // sql 语句
    cacheKey.update(boundSql.getSql());
    // 用户实参
    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    // 类型注册器
    TypeHandlerRegistry typeHandlerRegistry = ms.getConfiguration().getTypeHandlerRegistry();
    // mimic DefaultParameterHandler logic
    for (ParameterMapping parameterMapping : parameterMappings) {
      // 过滤掉 输出类型的参数
      // 遍历用户实参，更新 cacheKey
      if (parameterMapping.getMode() != ParameterMode.OUT) {
        Object value;
        String propertyName = parameterMapping.getProperty();
        // 额外参数 bind、loops 等
        if (boundSql.hasAdditionalParameter(propertyName)) {
          value = boundSql.getAdditionalParameter(propertyName);
        } else if (parameterObject == null) {
          value = null;
        } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
          value = parameterObject;
        } else {
          MetaObject metaObject = configuration.newMetaObject(parameterObject);
          value = metaObject.getValue(propertyName);
        }
        cacheKey.update(value);
      }
    }
    if (configuration.getEnvironment() != null) {
      // issue #176
      cacheKey.update(configuration.getEnvironment().getId());
    }
    return cacheKey;
  }

  /**
   * 检测是否缓存了 key
   * @param ms
   * @param key
   * @return
   */
  @Override
  public boolean isCached(MappedStatement ms, CacheKey key) {
    return localCache.getObject(key) != null;
  }

  /**
   * 提交事务
   * @param required 是否提交事务
   * @throws SQLException
   */
  @Override
  public void commit(boolean required) throws SQLException {
    if (closed) {
      throw new ExecutorException("Cannot commit, transaction is already closed");
    }
    clearLocalCache();
    flushStatements();
    if (required) {
      transaction.commit();
    }
  }

  @Override
  public void rollback(boolean required) throws SQLException {
    if (!closed) {
      try {
        clearLocalCache(); //清空缓存
        flushStatements(true); // 刷新所有statements，导致 Executor 中缓存的 SQL语句全部被忽略，不会发送到数据库进行执行
      } finally {
        if (required) { //true:事务回滚
          transaction.rollback();
        }
      }
    }
  }

  /**
   * 清空缓存
   */
  @Override
  public void clearLocalCache() {
    if (!closed) {
      localCache.clear();
      localOutputParameterCache.clear();
    }
  }

  protected abstract int doUpdate(MappedStatement ms, Object parameter)
      throws SQLException;

  protected abstract List<BatchResult> doFlushStatements(boolean isRollback)
      throws SQLException;

  protected abstract <E> List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql)
      throws SQLException;

  protected abstract <E> Cursor<E> doQueryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds, BoundSql boundSql)
      throws SQLException;

  protected void closeStatement(Statement statement) {
    if (statement != null) {
      try {
        statement.close();
      } catch (SQLException e) {
        // ignore
      }
    }
  }

  /**
   * Apply a transaction timeout.
   * @param statement a current statement
   * @throws SQLException if a database access error occurs, this method is called on a closed <code>Statement</code>
   * @since 3.4.0
   * @see StatementUtil#applyTransactionTimeout(Statement, Integer, Integer)
   */
  protected void applyTransactionTimeout(Statement statement) throws SQLException {
    StatementUtil.applyTransactionTimeout(statement, statement.getQueryTimeout(), transaction.getTimeout());
  }

  /**
   * 针对存储过程调用的处理，其功能是：在一级缓存命中时，获取缓存中保存的输出类型参数，并设置到用户传入的实参(parameter)对象中
   * @param ms
   * @param key
   * @param parameter sql 查询用户实参
   * @param boundSql
   */
  private void handleLocallyCachedOutputParameters(MappedStatement ms, CacheKey key, Object parameter, BoundSql boundSql) {
    // 存储过程
    if (ms.getStatementType() == StatementType.CALLABLE) {
      final Object cachedParameter = localOutputParameterCache.getObject(key); // 存储过程中 value
      if (cachedParameter != null && parameter != null) {
        final MetaObject metaCachedParameter = configuration.newMetaObject(cachedParameter); // 存储过程 value 的包装对象
        final MetaObject metaParameter = configuration.newMetaObject(parameter);    //用户实参的 包装对象
        for (ParameterMapping parameterMapping : boundSql.getParameterMappings()) {  // 用户实参的 ParameterMappings 对象
          if (parameterMapping.getMode() != ParameterMode.IN) {     // 过滤存储过程参数，输入参数
            final String parameterName = parameterMapping.getProperty();
            final Object cachedValue = metaCachedParameter.getValue(parameterName);
            metaParameter.setValue(parameterName, cachedValue);  //将缓存中存储过程的出参，设置到用户传入的实参中去
          }
        }
      }
    }
  }

  /**
   * 从数据库，查询数据
   * @param ms
   * @param parameter
   * @param rowBounds
   * @param resultHandler
   * @param key
   * @param boundSql
   * @param <E>
   * @return
   * @throws SQLException
   */
  private <E> List<E> queryFromDatabase(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql) throws SQLException {
    List<E> list;
    // 标识 sql 查询
    localCache.putObject(key, EXECUTION_PLACEHOLDER);
    try {
      // 查询数据库
      list = doQuery(ms, parameter, rowBounds, resultHandler, boundSql);
    } finally {
      // 移除标识
      localCache.removeObject(key);
    }
    // 将结果放入一级缓存
    localCache.putObject(key, list);
    // 存储过程
    if (ms.getStatementType() == StatementType.CALLABLE) {
      // 存储过程中 出参类型缓存
      localOutputParameterCache.putObject(key, parameter);
    }
    return list;
  }

  protected Connection getConnection(Log statementLog) throws SQLException {
    Connection connection = transaction.getConnection();
    if (statementLog.isDebugEnabled()) {
      return ConnectionLogger.newInstance(connection, statementLog, queryStack);
    } else {
      return connection;
    }
  }

  @Override
  public void setExecutorWrapper(Executor wrapper) {
    this.wrapper = wrapper;
  }

  /**
   * 从 localCache 缓存中，延迟加载结果对象；
   * 前面在分析嵌套查询时，如果一级缓存中缓存了嵌套查询的结果对象，则可以从一级缓存中直接加载该结果对象：如果一级缓存中记录的嵌套查询的结对象并未完全加载，则可以通过 DeferredLoad 实现类似延迟加载的功能。
   * 针对一对多的嵌套查询，它只实现了懒查询，它并没有真正的去查询数据库
   * https://blog.csdn.net/liuao107329/article/details/41829767
   */
  private static class DeferredLoad {
    // 外层对象对应的 MetaObject 对象
    private final MetaObject resultObject;
    // 延迟加载的属性名称
    private final String property;
    // 延迟加载的属性类型
    private final Class<?> targetType;
    // 延迟加载的结果对象在一级缓存中相应的 CacheKey 对象
    private final CacheKey key;
    // 一级缓存，与 BaseExecutor.localCache 字段指向同一 PerpetualCache 对象
    private final PerpetualCache localCache;
    private final ObjectFactory objectFactory;
    // 负责结果对象的类型转换
    private final ResultExtractor resultExtractor;

    // issue #781
    public DeferredLoad(MetaObject resultObject,
                        String property,
                        CacheKey key,
                        PerpetualCache localCache,
                        Configuration configuration,
                        Class<?> targetType) {
      this.resultObject = resultObject;
      this.property = property;
      this.key = key;
      this.localCache = localCache;
      this.objectFactory = configuration.getObjectFactory();
      this.resultExtractor = new ResultExtractor(configuration, objectFactory);
      this.targetType = targetType;
    }

    /**
     * 检测缓存项 是否缓存，还有查询数据库是否已经查询完毕
     * @return
     */
    public boolean canLoad() {
      // 是否缓存，还有缓存已经查询完毕
      return localCache.getObject(key) != null && localCache.getObject(key) != EXECUTION_PLACEHOLDER;
    }

    /**
     * 从缓存加载对象，并结果对象类型转换
     */
    public void load() {
      @SuppressWarnings("unchecked")
      // we suppose we get back a List
      // 查询缓存对象
      List<Object> list = (List<Object>) localCache.getObject(key);
      // 将缓存的结果对象转换成指定类型
      Object value = resultExtractor.extractObjectFromList(list, targetType);
      // 设置到外层对象的对应属性
      resultObject.setValue(property, value);
    }

  }

}
