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
package org.apache.ibatis.mapping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.property.PropertyTokenizer;
import org.apache.ibatis.session.Configuration;

/**
 * An actual SQL String got from an {@link SqlSource} after having processed any dynamic content.
 * The SQL may have SQL placeholders "?" and an list (ordered) of an parameter mappings
 * with the additional information for each parameter (at least the property name of the input object to read
 * the value from).
 * <p>
 * Can also have additional parameters that are created by the dynamic language (for loops, bind...).
 *
 * 一个可以执行的真正的 Sql，已转把动态 SQL 转化完毕，包含占位符 ？ 和 实参等；
 * BoundSql 中还提供了从 additionalParameters 集合中获取／设置指定值的方法
 *
 * bound 准备就绪的，装订好的，受约束的
 * @author Clinton Begin
 */
public class BoundSql {

  // 该字段中记录了 SQL 句，该 SQL 语句中可能含有 "?" 占位符
  private final String sql;
  // SQL 中的参数属性集合， ParameterMapping 的集合
  private final List<ParameterMapping> parameterMappings;
  // 客户端执行 SQ 时传入的实际参数
  private final Object parameterObject; //Author
  // 空的 HashMap 合，之后会复制 DynamicContext.bindings 集合中的内容
  private final Map<String, Object> additionalParameters; // example: loops, bind
  // additionalParameters 集合对应的 MetaObject 对象
  private final MetaObject metaParameters; //处理additionalParameters 放入到 metaParameters

  public BoundSql(Configuration configuration, String sql, List<ParameterMapping> parameterMappings, Object parameterObject) {
    this.sql = sql;
    this.parameterMappings = parameterMappings;
    this.parameterObject = parameterObject;
    this.additionalParameters = new HashMap<>();
    this.metaParameters = configuration.newMetaObject(additionalParameters);
  }

  public String getSql() {
    return sql;
  }

  public List<ParameterMapping> getParameterMappings() {
    return parameterMappings;
  }

  public Object getParameterObject() {
    return parameterObject;
  }

  public boolean hasAdditionalParameter(String name) {
    String paramName = new PropertyTokenizer(name).getName();
    return additionalParameters.containsKey(paramName);
  }
  //也会赋值给additionalParameters
  public void setAdditionalParameter(String name, Object value) {
    metaParameters.setValue(name, value);
  }

  public Object getAdditionalParameter(String name) {
    return metaParameters.getValue(name);
  }
}
