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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import ognl.Ognl;
import ognl.OgnlException;

import org.apache.ibatis.builder.BuilderException;

/**
 * Caches OGNL parsed expressions.
 *
 * OgnlCache 主要是用来解析 Mybatis 中 mapper.xml 文件中的表达式来使用。
 *
 * OGNL 是 Object-Graph Navigation Language 的缩写，从语言角度来说：它是一个功能强大的表达式语言(EL)，用来获取和设置 java 对象的属性 , 它旨在提供一个更高抽象度语法来对 java 对象图进行导航。
 *
 * Jsp 页面中的 jsp 标签就是一种表达语言。
 *
 * OgnlCache 对原生 OGNL 进行了封装，OGNL 表达式解析过程比较耗时，为了提高效率，
 * OgnlCache 中使用 expressionCache 字段（静态成员，ConcurrentHashMap<String, Object> 对解析后的对象进行缓存。
 * ConcurrentHashMap 的吨 Object> 对解析 OGNL 达式进行 O
 *
 * Ognl : https://www.jianshu.com/p/86e00c1fee57
 *
 * @author Eduardo Macarron
 *
 * @see <a href='http://code.google.com/p/mybatis/issues/detail?id=342'>Issue 342</a>
 */
public final class OgnlCache {

  private static final OgnlMemberAccess MEMBER_ACCESS = new OgnlMemberAccess();
  private static final OgnlClassResolver CLASS_RESOLVER = new OgnlClassResolver();
  private static final Map<String, Object> expressionCache = new ConcurrentHashMap<>();

  private OgnlCache() {
    // Prevent Instantiation of Static Class
  }

  public static Object getValue(String expression, Object root) {
    try {
      // 创建 OgnlContext 对象 OgnlClassResolver 替代了 OGNL 中原有的 DefaultClassResolver,
      // 其主要功能是使用前 介绍 Resource 具类定位资源
      Map context = Ognl.createDefaultContext(root, MEMBER_ACCESS, CLASS_RESOLVER, null);
      // 使用 OGNL 执行 expression 表达式
      return Ognl.getValue(parseExpression(expression), context, root);
    } catch (OgnlException e) {
      throw new BuilderException("Error evaluating expression '" + expression + "'. Cause: " + e, e);
    }
  }

  private static Object parseExpression(String expression) throws OgnlException {
    Object node = expressionCache.get(expression);
    if (node == null) {
      node = Ognl.parseExpression(expression); // 解析表达式
      expressionCache.put(expression, node);
    }
    return node;
  }

}
