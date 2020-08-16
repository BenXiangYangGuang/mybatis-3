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
package org.apache.ibatis.scripting.defaults;

import java.util.HashMap;

import org.apache.ibatis.builder.SqlSourceBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.DynamicContext;
import org.apache.ibatis.scripting.xmltags.DynamicSqlSource;
import org.apache.ibatis.scripting.xmltags.SqlNode;
import org.apache.ibatis.session.Configuration;

/**
 * Static SqlSource. It is faster than {@link DynamicSqlSource} because mappings are
 * calculated during startup.
 * 负责处理静态 SQL 语句,可能包含"#{}" 占位符
 *
 * XMLScriptBuilder. parseDynamicTags() 方法时提到过，如果节点只包含 "#{}" 占位符，而不包含动态 SQL 点或未解析的 "${}"占位符的话，
 * 则不是动态 SQL 语句，会创建相应的 StaticTextSqlNode 对象。在 XMLScriptBuilder.parseScriptNode() 方法中会判断整 SQL 点是否为动态的，
 * 如果不是动态的 SQL ，则创建相应的 RawSqlSource 对象
 * @since 3.2.0
 * @author Eduardo Macarron
 */
public class RawSqlSource implements SqlSource {

  private final SqlSource sqlSource;  // StaticSqlSource 对象

  /**
   * RawSqlSource 在构造方法中首先会调用 getSql() 方法, 其中通过调用 SqlNode.apply() 方法完SQL 语句的拼装和初步处理;
   * 之后会使用 SqlSourceBuilder 完成占位符的替换和 ParameterMapping 集合的创建，井返回 StaticSqlSource 对象。
   *
   */
  public RawSqlSource(Configuration configuration, SqlNode rootSqlNode, Class<?> parameterType) {
    // 调用 getSql()方法，完成 SQL 语句的拼笨和初步解析
    this(configuration, getSql(configuration, rootSqlNode), parameterType);
  }

  public RawSqlSource(Configuration configuration, String sql, Class<?> parameterType) {
    // 通过 SqlSourceBuilder 完成占住符的解析和替换操作
    SqlSourceBuilder sqlSourceParser = new SqlSourceBuilder(configuration);
    Class<?> clazz = parameterType == null ? Object.class : parameterType;
    // SqlSourceBuilder.parse() 方法返回 的是 StaticSqlSource
    sqlSource = sqlSourceParser.parse(sql, clazz, new HashMap<>());
  }

  private static String getSql(Configuration configuration, SqlNode rootSqlNode) {
    DynamicContext context = new DynamicContext(configuration, null);
    rootSqlNode.apply(context);
    return context.getSql();
  }

  @Override
  public BoundSql getBoundSql(Object parameterObject) {
    return sqlSource.getBoundSql(parameterObject);
  }

}
