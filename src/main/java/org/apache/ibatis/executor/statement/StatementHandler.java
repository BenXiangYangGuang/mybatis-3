/**
 *    Copyright 2009-2016 the original author or authors.
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
package org.apache.ibatis.executor.statement;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.ResultHandler;

/**
 * Statement 是 java.sql 包中的一个类，主要用于 sql 语句的执行和 sql 执行返回结果的处理。
 * StatementHandler 是对 Statement 的一个封装，提供了 Statement 的所有功能，使 Mybatis 使用 Statement 对象更方便、快捷。
 * 提供了 创建 Statement 对象，绑定执行的实参，批量执行 Sql语句，执行 select、update、delete、insert 语句功能，还有将结果集映射为成结果对象。
 *
 * StatementHandler 依赖 ParameterHandler 和 ResultSetHandler
 * 完成了 Mybatis 的核心功能，它控制着参数绑定、 SQL 语句执行、结果集映射等一系列核心过程。
 * @author Clinton Begin
 */
public interface StatementHandler {

  // 从连接中获取一个 Statement
  Statement prepare(Connection connection, Integer transactionTimeout)
      throws SQLException;
  // 绑定 statement 执行时所需的实参
  void parameterize(Statement statement)
      throws SQLException;
  // 批量执行 SQL 语句
  void batch(Statement statement)
      throws SQLException;
  // 执行 update/insert/delete 语句
  int update(Statement statement)
      throws SQLException;
  // 执行 select 语句
  <E> List<E> query(Statement statement, ResultHandler resultHandler)
      throws SQLException;
  // 执行 select 语句
  <E> Cursor<E> queryCursor(Statement statement)
      throws SQLException;

  BoundSql getBoundSql();
  // 获取 ParameterHandler 对象
  ParameterHandler getParameterHandler();

}
