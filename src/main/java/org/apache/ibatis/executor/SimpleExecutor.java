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

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

/**
 * SimpleExecutor 继承了 BaseExecutor 抽象类 它是最简单的 Executor 接口实现。Executor 使用了模板方法模式，一级缓存等固定不变的操作都封装到了 BaseExecutor 中，
 * 在SimpleExecutor 中就不必再关系一级缓存等操作，只需要专注实现4个基本方法的实现即可。
 * @author Clinton Begin
 */
public class SimpleExecutor extends BaseExecutor {

  public SimpleExecutor(Configuration configuration, Transaction transaction) {
    super(configuration, transaction);
  }

  /**
   *
   * @param ms
   * @param parameter 用户实参
   * @return
   * @throws SQLException
   */
  @Override
  public int doUpdate(MappedStatement ms, Object parameter) throws SQLException {
    Statement stmt = null;
    try {
      Configuration configuration = ms.getConfiguration();
      // 创建 StatementHandler 对象，StatementHandler 在创建 Statement 对象
      // 实际返回 RoutingStatementHandler 对象，根据 statementType 选择具体的 StatementHandler 实现
      StatementHandler handler = configuration.newStatementHandler(this, ms, parameter, RowBounds.DEFAULT, null, null);
      // 处理占位符
      stmt = prepareStatement(handler, ms.getStatementLog());
      // StatementHandler 对象执行 Statement，从而执行 SQL 语句
      return handler.update(stmt);
    } finally {
      closeStatement(stmt);
    }

  }

  @Override
  public <E> List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
    Statement stmt = null;
    try {
      Configuration configuration = ms.getConfiguration();
      StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, resultHandler, boundSql);
      stmt = prepareStatement(handler, ms.getStatementLog());
      // 调用 StatementHandler.query() 方法，执行 SQL 语句，并通过 ResultSetHandler 完成结果集映射
      return handler.query(stmt, resultHandler);
    } finally {
      closeStatement(stmt);
    }
  }

  @Override
  protected <E> Cursor<E> doQueryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds, BoundSql boundSql) throws SQLException {
    Configuration configuration = ms.getConfiguration();
    StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, null, boundSql);
    Statement stmt = prepareStatement(handler, ms.getStatementLog());
    Cursor<E> cursor = handler.queryCursor(stmt);
    stmt.closeOnCompletion();
    return cursor;
  }

  /**
   * SimpleExecutor 不提供批量处理 SQL 语句的功能，所以其 doFlushStatements() 方法直接返回空集合，不做其他任何操作。
   * @param isRollback
   * @return
   */
  @Override
  public List<BatchResult> doFlushStatements(boolean isRollback) {
    return Collections.emptyList();
  }

  /**
   * 获取 Statement 对象
   * @param handler
   * @param statementLog
   * @return
   * @throws SQLException
   */
  private Statement prepareStatement(StatementHandler handler, Log statementLog) throws SQLException {
    Statement stmt;
    Connection connection = getConnection(statementLog);
    // 创建 Statement 对象
    stmt = handler.prepare(connection, transaction.getTimeout());
    // 处理 "?" 占位符
    handler.parameterize(stmt);
    return stmt;
  }

}
