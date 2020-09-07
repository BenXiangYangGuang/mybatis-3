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

import java.sql.SQLException;
import java.util.List;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cache.TransactionalCacheManager;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

/**
 * CachingExecutor 是一个 Executor 接口的装饰器，它为 Executor 对象增加了二级缓存的相关功能。
 * 它封装了一个用于执行数据库操作的 Executor 对象，以及一个用于管理缓存的 TransactionalCacheManager 对象。
 * TransactionalCache 和 TransactionalCacheManager 是 CachingExecutor 依赖的两个组件。
 *
 * CachingExecutor 为 Executor 装饰了二级缓存功能
 * TransactionalCacheManager 管理了所有的 以 namespace 为存储单位的二级缓存
 * TransactionalCache 是二级缓存 Cache 的代理类，对外提供了二级缓存的功能，里面包装了二级缓存 Cache 对象(delegate)，这个delegate 提供了真实的数据缓存功能。
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class CachingExecutor implements Executor {

  private final Executor delegate;
  private final TransactionalCacheManager tcm = new TransactionalCacheManager();

  public CachingExecutor(Executor delegate) {
    this.delegate = delegate;
    delegate.setExecutorWrapper(this);
  }

  @Override
  public Transaction getTransaction() {
    return delegate.getTransaction();
  }

  @Override
  public void close(boolean forceRollback) {
    try {
      //issues #499, #524 and #573
      if (forceRollback) {
        tcm.rollback();
      } else {
        tcm.commit();
      }
    } finally {
      delegate.close(forceRollback);
    }
  }

  @Override
  public boolean isClosed() {
    return delegate.isClosed();
  }

  @Override
  public int update(MappedStatement ms, Object parameterObject) throws SQLException {
    flushCacheIfRequired(ms);
    return delegate.update(ms, parameterObject);
  }

  /**
   * 查询过程
   * (1）获取 BoundSql 对象，创建查询语句对应的 CacheKey 对象
   * (2）检测是否开启了二级缓存，如果没有开启二级缓存，则直接调用底层 Executor 对象的 query() 方法查询数据库。如果开启了二级缓存，则继续后面的步骤
   * (3）检测查询操作是否包含输出类型的参数，如果是这种情况，则报错
   * (4）调用 TransactionalCacheManager.getObject（）方法查询二级缓存，如果二级缓存中查找到相应的结果对象，则直接将该结果对象返回。
   * (5）如果二级缓存没有相应的结果对象，则调用底层 Executor 对象的 query() 方法，正如前面介绍的 ，它会先查询一级缓存，一级缓存未命中时，才会查询数据库。
   * 最后还会将得到的结果对象放入 TransactionalCache.entriesToAddOnCommit 集合中保存。
   *
   * @param ms
   * @param parameterObject
   * @param rowBounds
   * @param resultHandler
   * @param <E>
   * @return
   * @throws SQLException
   */

  @Override
  public <E> List<E> query(MappedStatement ms, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException {
    // 步骤1：获取 BoundSql 对象，解析 BoundSql
    BoundSql boundSql = ms.getBoundSql(parameterObject);
    // 创建 CacheKey 对象
    CacheKey key = createCacheKey(ms, parameterObject, rowBounds, boundSql);
    return query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
  }

  @Override
  public <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException {
    flushCacheIfRequired(ms);
    return delegate.queryCursor(ms, parameter, rowBounds);
  }

  @Override
  public <E> List<E> query(MappedStatement ms, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql)
      throws SQLException {
    // 获取查询语句所在命名空间对应的二级缓存
    Cache cache = ms.getCache();
    // 步骤2：是否开启了二级缓存功能
    if (cache != null) {
      flushCacheIfRequired(ms);   // 根据 <select> 节点的配置，决定是否需要清空二级缓存
      // 检测 SQL 节点的 useCache 配置以及是否使用了 resultHandler 配置
      if (ms.isUseCache() && resultHandler == null) {
        //步骤3： 二级缓存不能保存输出类型的参数 如果查询操作调用了包含输出参数的存储过程，则报错
        ensureNoOutParams(ms, boundSql);
        // 步骤4：查询二级缓存
        @SuppressWarnings("unchecked")
        List<E> list = (List<E>) tcm.getObject(cache, key);
        if (list == null) {
          // 步骤5：二级缓存没用相应的结果对象，调用封装的 Executor 对象的 query() 方法，这个 query() 方法会先查询一级缓存
          list = delegate.query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
          // 将查询结果保存到 TransactionalCache.entriesToAddOnCommit 集合中
          tcm.putObject(cache, key, list); // issue #578 and #116
        }
        return list;
      }
    }
    // 没有启动二级缓存，直接调用底层 Executor 执行数据数据库查询操作
    return delegate.query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
  }

  /**
   * 调用底层 Executor 的 flushStatements() 方法
   * @return
   * @throws SQLException
   */
  @Override
  public List<BatchResult> flushStatements() throws SQLException {
    return delegate.flushStatements();
  }

  /**
   * 首先调用底层 Executor 对象对应的方法完成事务的提交，然后再调用 TransactionalCacheManager 的对应方法完成对二级缓存的相应操作
   * @param required
   * @throws SQLException
   */
  @Override
  public void commit(boolean required) throws SQLException {
    delegate.commit(required);  // 调用底层的 Executor 提交事务
    tcm.commit();   //遍历所有相关的 TransactionalCache 对象执行 commit() 方法
  }
  /**
   * 首先调用底层 Executor 对象对应的方法完成事务的回滚，然后再调用 TransactionalCacheManager 的对应方法完成对二级缓存的相应操作
   * @param required
   * @throws SQLException
   */
  @Override
  public void rollback(boolean required) throws SQLException {
    try {
      delegate.rollback(required); // 调用底层的 Executor 回滚事务
    } finally {
      if (required) {
        tcm.rollback(); //遍历所有相关的 TransactionalCache 对象执行 rollback() 方法
      }
    }
  }

  /**
   * 二级缓存不能保存输出类型的参数 如果查询操作调用了包含输出参数的存储过程，则报错
   * @param ms
   * @param boundSql
   */
  private void ensureNoOutParams(MappedStatement ms, BoundSql boundSql) {
    if (ms.getStatementType() == StatementType.CALLABLE) {
      for (ParameterMapping parameterMapping : boundSql.getParameterMappings()) {
        if (parameterMapping.getMode() != ParameterMode.IN) {
          throw new ExecutorException("Caching stored procedures with OUT params is not supported.  Please configure useCache=false in " + ms.getId() + " statement.");
        }
      }
    }
  }

  @Override
  public CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql) {
    return delegate.createCacheKey(ms, parameterObject, rowBounds, boundSql);
  }

  @Override
  public boolean isCached(MappedStatement ms, CacheKey key) {
    return delegate.isCached(ms, key);
  }

  @Override
  public void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key, Class<?> targetType) {
    delegate.deferLoad(ms, resultObject, property, key, targetType);
  }

  /**
   * 清空一级缓存
   */
  @Override
  public void clearLocalCache() {
    delegate.clearLocalCache();
  }

  /**
   * 是否要求刷新 一级和二级缓存
   * @param ms
   */
  private void flushCacheIfRequired(MappedStatement ms) {
    // 获取 二级缓存
    Cache cache = ms.getCache();
    if (cache != null && ms.isFlushCacheRequired()) {
      tcm.clear(cache);
    }
  }

  @Override
  public void setExecutorWrapper(Executor executor) {
    throw new UnsupportedOperationException("This method should not be called");
  }

}
