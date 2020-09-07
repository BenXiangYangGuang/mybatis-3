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
package org.apache.ibatis.type;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Clinton Begin
 * 类型转换器接口：
 * javaType和jdbcType类型转换，
 * 在通过 PreparedStatement 为 SQL 语句绑定参数时，将javaType转换为jdbcType
 * 从 ResultSet 中获取数据时会调用此方法,会将数据由 JdbcType 类型转换成 Java 类型;
 * T 泛型，可以是 Interger String 自定义类型；可以自定义 实现特定类型的 Handler 处理器，继承 BaseTypeHandler<特定类型>
 */
public interface TypeHandler<T> {

  //将javaType 转换为 jdbcType;
  //为PreparedStatement设置java参数

  /**
   *
   * @param ps PreparedStatement
   * @param i PreparedStatement index
   * @param parameter 传递是实参
   * @param jdbcType
   * @throws SQLException
   */
  void setParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException;

  /**
   * @param columnName Colunm name, when configuration <code>useColumnLabel</code> is <code>false</code>
   */
  //jdbcType 转换为 javaType,列明参数
  //将resultSet结果集转换为Java类型
  T getResult(ResultSet rs, String columnName) throws SQLException;
  //jdbcType 转换为 javaType,列的下标
  T getResult(ResultSet rs, int columnIndex) throws SQLException;
  //jdbcType 转换为 javaType
  T getResult(CallableStatement cs, int columnIndex) throws SQLException;

}
