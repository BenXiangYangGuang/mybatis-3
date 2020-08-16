/**
 *    Copyright 2009-2015 the original author or authors.
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

import java.util.Arrays;
import java.util.List;

import org.apache.ibatis.session.Configuration;

/**
 * WhereSqlNode 和 SetSqlNode 都继承了 TrimSqlNode ，其中 WhereSqlNode 定了 prefix 字段为 "WHERE"，
 * prefixesToOverride 集合中的项为 "AND " 和 "OR"， suffix 宇段和 suffixesToOverride 集合 null
 * 也就是说，＜where＞节点解析后的 SQL 语句片段如果以“AND” 或者 "OR" 开头，则将开头处的“AND”或 "OR"删除，
 * 之后再将“WHERE ”关键字添加到 SQL 片段开始位置，从而得到该＜where＞节点最终生成的 SQL 片段。
 * @author Clinton Begin
 */
public class WhereSqlNode extends TrimSqlNode {

  private static List<String> prefixList = Arrays.asList("AND ","OR ","AND\n", "OR\n", "AND\r", "OR\r", "AND\t", "OR\t");

  public WhereSqlNode(Configuration configuration, SqlNode contents) {
    super(configuration, contents, "WHERE", prefixList, null, null);
  }

}
