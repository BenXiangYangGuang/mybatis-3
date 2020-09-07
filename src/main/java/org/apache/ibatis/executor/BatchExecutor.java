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

import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

/**
 * BatchExecutor 处理 多条SQL 情况。JDBC 中的批处理只支持 insert、 update delete 等类型的 SQL 语句，不支持 select 类型的SQL 语句。
 * 但是 BatchExecutor 也会有 DoQuery() 和 doQueryCursor() 方法,它不会执行 批量的查询，只会执行一次查询，只是为了使用 BatchExecutor 也有可用的查询方法。
 * BatchExecutor 的 DoQuery() 和 doQueryCursor() 方法，都会最开始调用 flushStatements() 方法，执行缓存的 SQL 语句，这样才能数据库中查询到最新的数据。
 * @author Jeff Butler
 */
public class BatchExecutor extends BaseExecutor {

  //https://my.oschina.net/u/2518341/blog/4299007/print
  //因为批量更新无法得知更新了多少条记录，作者想要个负数，接近于负无穷的数，所以猜测当时写了Integer.MIN_VALUE + 1002这个固定值,但是为什么不返回Integer.MIN_VALUE呢，对吧，相比较之下，难道Integer.MIN_VALUE不是更接近与负无穷么，不得而知了，也许1002是他的Luck number呢，哈哈，天知......
  // -2147482646
  public static final int BATCH_UPDATE_RETURN_VALUE = Integer.MIN_VALUE + 1002;

  // 缓存多个 Statement 对象其中每个 Statement 对象中都缓存了多条 SQL 语句
  private final List<Statement> statementList = new ArrayList<>();
  // 记录批处理的结果， BatchResult 中通过 updateCounts 字段(int[]数纽类型）记录每个 Statement 执行批处理的结果
  private final List<BatchResult> batchResultList = new ArrayList<>();
  // 记录当前执行的 SQL 语句
  private String currentSql;
  // 记录当前执行的 MappedStatement 对象
  private MappedStatement currentStatement;

  public BatchExecutor(Configuration configuration, Transaction transaction) {
    super(configuration, transaction);
  }
  // TODO: 2020/9/3 SQL 模式 Page 346

  /**
   * https://www.baeldung.com/jdbc-batch-processing
   * https://www.cnblogs.com/noteless/p/10307273.html
   *
   * Statement 分为 Statement、PreparedStatement、CallableStatement 三类
   * Statement:普通的不带参数的查询静态SQL,每次执行，每次编译
   * PreparedStatement:支持可变参数的SQL,一次编译，多此执行，会预编译，缓存
   * CallableStatement:支持调用存储过程,提供了对输出和输入/输出参数(INOUT)的支持;
   *
   * 熟悉 JDBC 批处理， Statement 中可以添加不同模式的 SQL ，但是每添加一个新模式的 SQL 语句都会触发一次编译操作。
   * PreparedStatement 中只能添加同一模式的 SQL 语句，只会触发一次编译操，但是可以通过绑定多组不同的实参实现批处理。通过上面对
   * doUpdate() 方法的分析可知， BatchExecutor 会将连续添加的、相同模式的 SQL 语句添加到同
   * Statement/PreparedStatement 对象中，这样可以有效地减少编译操作的次数。
   * 模式 是指 一个 SQL 的表主体，还有 SQL 类型 等是否相等，就是除了参数不一样，其他的都一样。
   * @param ms
   * @param parameterObject parameter 用户实参
   * @return BATCH_UPDATE_RETURN_VALUE 一个非常小的负无穷的数
   * @throws SQLException
   */
  @Override
  public int doUpdate(MappedStatement ms, Object parameterObject) throws SQLException {
    final Configuration configuration = ms.getConfiguration();
    // 创建 StatementHandler
    final StatementHandler handler = configuration.newStatementHandler(this, ms, parameterObject, RowBounds.DEFAULT, null, null);
    final BoundSql boundSql = handler.getBoundSql();
    // 数据库可执行 SQL 语句
    final String sql = boundSql.getSql();
    final Statement stmt;
    // sql 相等 and MappedStatement 相等，如果相等，Statement 不会改变，只会添加实参，进去，然后进行批量执行
    if (sql.equals(currentSql) && ms.equals(currentStatement)) {
      int last = statementList.size() - 1;
      stmt = statementList.get(last);
      // 设置超时时间
      applyTransactionTimeout(stmt);
      // 解析参数
      handler.parameterize(stmt);//fix Issues 322
      BatchResult batchResult = batchResultList.get(last);
      batchResult.addParameterObject(parameterObject);
    } else {
      // 否则创建 Statement
      Connection connection = getConnection(ms.getStatementLog());
      // 创建 Statement
      stmt = handler.prepare(connection, transaction.getTimeout());
      // 绑定参数
      handler.parameterize(stmt);    //fix Issues 322
      currentSql = sql;
      currentStatement = ms;
      statementList.add(stmt);
      batchResultList.add(new BatchResult(ms, sql, parameterObject));
    }
    // 底层通过调用 Statement.addBatch() 方法添加 SQL 语句，分别调用不同 Statement 类型的 addBatch() 方法
    handler.batch(stmt);
    return BATCH_UPDATE_RETURN_VALUE;
  }

  @Override
  public <E> List<E> doQuery(MappedStatement ms, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql)
      throws SQLException {
    Statement stmt = null;
    try {
      flushStatements();
      Configuration configuration = ms.getConfiguration();
      StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameterObject, rowBounds, resultHandler, boundSql);
      Connection connection = getConnection(ms.getStatementLog());
      stmt = handler.prepare(connection, transaction.getTimeout());
      handler.parameterize(stmt);
      return handler.query(stmt, resultHandler);
    } finally {
      closeStatement(stmt);
    }
  }

  @Override
  protected <E> Cursor<E> doQueryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds, BoundSql boundSql) throws SQLException {
    flushStatements();
    Configuration configuration = ms.getConfiguration();
    StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, null, boundSql);
    Connection connection = getConnection(ms.getStatementLog());
    Statement stmt = handler.prepare(connection, transaction.getTimeout());
    handler.parameterize(stmt);
    Cursor<E> cursor = handler.queryCursor(stmt);
    stmt.closeOnCompletion();
    return cursor;
  }

  /**
   * 执行 缓存中的 SQL 语句
   * @param isRollback
   * @return
   * @throws SQLException
   */
  @Override
  public List<BatchResult> doFlushStatements(boolean isRollback) throws SQLException {
    try {
      // results 集合用于存储批处理的结果
      List<BatchResult> results = new ArrayList<>();
      // 如果明确指定了要回滚事务，则直接返回空集合，忽略 statementList 集合中记录的 SQL 语句
      if (isRollback) {
        return Collections.emptyList();
      }
      for (int i = 0, n = statementList.size(); i < n; i++) { //遍历 statementList
        Statement stmt = statementList.get(i);
        applyTransactionTimeout(stmt); // 设置超时时间
        BatchResult batchResult = batchResultList.get(i); // 获取 BatchResult 结果
        try {
          // 调用 Statement.executeBatch() 方法批量执行其中记录的 SQL 语句，并使用返回的 int 数组，一个 Statement 可以包含 多条 SQL 语句，只是 SQL 语句的实参不相同
          // 更新 batchResult.updateCounts 字段，其中每一个元素都表示一条 SQL 语句影响的记录条数
          batchResult.setUpdateCounts(stmt.executeBatch());
          // 会写 每一条 表记录的 主键 Id
          MappedStatement ms = batchResult.getMappedStatement();
          List<Object> parameterObjects = batchResult.getParameterObjects();
          // 获取配置的 KeyGenerator 对象
          KeyGenerator keyGenerator = ms.getKeyGenerator();
          if (Jdbc3KeyGenerator.class.equals(keyGenerator.getClass())) {
            Jdbc3KeyGenerator jdbc3KeyGenerator = (Jdbc3KeyGenerator) keyGenerator;
            // 获取数据库生成的主键，并设置到 parameterObjects 中
            jdbc3KeyGenerator.processBatch(ms, stmt, parameterObjects);
          } else if (!NoKeyGenerator.class.equals(keyGenerator.getClass())) { //issue #141
            // 对于其他类型 KeyGenerator，会调用 processAfter() 方法
            for (Object parameter : parameterObjects) {
              keyGenerator.processAfter(this, ms, stmt, parameter);
            }
          }
          // Close statement to close cursor #1109
          closeStatement(stmt);
        } catch (BatchUpdateException e) {
          StringBuilder message = new StringBuilder();
          message.append(batchResult.getMappedStatement().getId())
              .append(" (batch index #")
              .append(i + 1)
              .append(")")
              .append(" failed.");
          if (i > 0) {
            message.append(" ")
                .append(i)
                .append(" prior sub executor(s) completed successfully, but will be rolled back.");
          }
          throw new BatchExecutorException(message.toString(), e, results, batchResult);
        }
        // 添加 batchResult 到结果集 集合 results
        results.add(batchResult);
      }
      return results;
    } finally {
      // 关闭所有 Statement 对象，并清空 currentSql 字段，清空 statementList、batchResultList 集合
      for (Statement stmt : statementList) {
        closeStatement(stmt);
      }
      currentSql = null;
      statementList.clear();
      batchResultList.clear();
    }
  }

}
