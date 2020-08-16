/**
 *    Copyright 2009-2017 the original author or authors.
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
package org.apache.ibatis.scripting.xmltags;

import java.util.List;

/**
 * 如果在编写动态 SQL 语句时需要类似 Java 中的 switch 语句的功能，可以考虑使用 <choose>、
 * <when>和<otherwise>三个标签 组合。 MyBatis 会将<choose>标签解析成 ChooseSqlNode，
 * 将<when>标签解析成 IfSqlNode ，将 <otherwise>标签解析成 MixedSqlNode。
 * @author Clinton Begin
 */
public class ChooseSqlNode implements SqlNode {
  private final SqlNode defaultSqlNode; // <otherwise> 节点对应的 SqlNode 集合
  private final List<SqlNode> ifSqlNodes; // <when> 节点对应的 ifSqlNode 集合

  public ChooseSqlNode(List<SqlNode> ifSqlNodes, SqlNode defaultSqlNode) {
    this.ifSqlNodes = ifSqlNodes;
    this.defaultSqlNode = defaultSqlNode;
  }

  /**
   * 解析 动态节点
   * @param context
   * @return
   */
  @Override
  public boolean apply(DynamicContext context) {
    // 遍历 ifSqlNodes 集合并调用其中 SqlNode 对象的 apply() 方法
    for (SqlNode sqlNode : ifSqlNodes) {
      if (sqlNode.apply(context)) {
        return true;
      }
    }
    if (defaultSqlNode != null) { // 调用 defaultSqlNode.apply() 方法
      defaultSqlNode.apply(context);
      return true;
    }
    return false;
  }
}
