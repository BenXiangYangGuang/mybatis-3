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
package org.apache.ibatis.scripting.xmltags;

/**
 * VarDeclSqlNode 表示的是动态 SQL 语句中的 <bind＞节点, 该节点可以从 OGNL 表达式中创建一个变量并将其记录到上下文中 。
 * @author Frank D. Martinez [mnesarco]
 */
public class VarDeclSqlNode implements SqlNode {

  // 记录＜bind>节点 name 属性值
  private final String name;
  // 记录＜bind＞节点的 value 属性值
  private final String expression;

  public VarDeclSqlNode(String var, String exp) {
    name = var;
    expression = exp;
  }

  @Override
  public boolean apply(DynamicContext context) {
    // 解析 OGNL 表达式的值
    final Object value = OgnlCache.getValue(expression, context.getBindings());
    // 将 name 和 表达式的值存入 DynamicContext.bindings 集合中
    context.bind(name, value);
    return true;
  }

}
