/**
 *    Copyright 2009-2018 the original author or authors.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.ibatis.session.Configuration;

/**
 * TrimSqlNode 会根据子节点的解析结果，添加或删 相应的前缀或后缀。
 * @author Clinton Begin
 */
public class TrimSqlNode implements SqlNode {
  // 该＜trim> 节点的子节点
  private final SqlNode contents;
  // 记录 前缀字符串（为＜trim＞节点包袤的 SQL 语句添加的前缀）
  private final String prefix;
  // 记录了后缀字符串（为＜trim＞节点包袤的 SQL 语句添加的后缀）
  private final String suffix;
  // 如果＜trim＞节点包袤的 SQL 语句是空语句（经常 现在 if 判断为否的情况下），删除指定的前缀，如 where
  private final List<String> prefixesToOverride;
  // 如采 trim 包袤的 SQL 语句是空语句（经常出现在 if 判断为否的情况下），删除指定的后缀，如逗号
  private final List<String> suffixesToOverride;
  private final Configuration configuration;

  /**
   * 在 TrimSqlNode 的构造函数中，会调用 parseOverrides()，方法对参数 prefixesToOverride （应＜trim＞节点的 prefixOverrides 属性）
   * 和 suffixesToOverride （对应＜trim＞节点的suffixOverrides 属性）进行解析,并初始化 prefixesToOverride 和 suffixesToOverride
   * @param configuration
   * @param contents
   * @param prefix
   * @param prefixesToOverride
   * @param suffix
   * @param suffixesToOverride
   */
  public TrimSqlNode(Configuration configuration, SqlNode contents, String prefix, String prefixesToOverride, String suffix, String suffixesToOverride) {
    this(configuration, contents, prefix, parseOverrides(prefixesToOverride), suffix, parseOverrides(suffixesToOverride));
  }

  protected TrimSqlNode(Configuration configuration, SqlNode contents, String prefix, List<String> prefixesToOverride, String suffix, List<String> suffixesToOverride) {
    this.contents = contents;
    this.prefix = prefix;
    this.prefixesToOverride = prefixesToOverride;
    this.suffix = suffix;
    this.suffixesToOverride = suffixesToOverride;
    this.configuration = configuration;
  }

  /**
   * 首先解析子节点，然后根据子节点的解析结果处理前缀和后缀
   * @param context
   * @return
   */
  @Override
  public boolean apply(DynamicContext context) {
    // 创建 FilteredDynamicContext 对象，其中封装了 DynamicContext
    FilteredDynamicContext filteredDynamicContext = new FilteredDynamicContext(context);
    // 调用子节点的 apply() 方法进行解析
    boolean result = contents.apply(filteredDynamicContext);
    // 处理前缀和后缀
    filteredDynamicContext.applyAll();
    return result;
  }

  /**
   *
   * @param overrides
   * @return
   */
  private static List<String> parseOverrides(String overrides) {
    if (overrides != null) {
      // 按照 "|" 进行分割
      final StringTokenizer parser = new StringTokenizer(overrides, "|", false);
      final List<String> list = new ArrayList<>(parser.countTokens());
      while (parser.hasMoreTokens()) {
        // 转换大写，并添加到集合
        list.add(parser.nextToken().toUpperCase(Locale.ENGLISH));
      }
      return list;
    }
    return Collections.emptyList();
  }

  /**
   * FilteredDynamicContext 用来处理前缀和后缀，继承了 DynamicContext，同时也是 DynamicContext 的代理类。
   * FilteredDynamicContext 除了将对应方法调用委托给其中封装的 DynamicContext 对象，还提供了处理前缀和后缀的 applyAll() 方法。
   */
  private class FilteredDynamicContext extends DynamicContext {
    // 底层封装 DynamicContext 对象
    private DynamicContext delegate;
    // 是否已经处理过前缀和后缀，初始值都为 false
    private boolean prefixApplied;
    private boolean suffixApplied;

    // 用于记录子节点解析后的结果，FilteredDynamicContext.appendSql()方法会向改字段添加解析结果，
    // 而不是调用 delegate.appendSql() 方法
    private StringBuilder sqlBuffer;

    public FilteredDynamicContext(DynamicContext delegate) {
      super(configuration, null);
      this.delegate = delegate;
      this.prefixApplied = false;
      this.suffixApplied = false;
      this.sqlBuffer = new StringBuilder();
    }

    public void applyAll() {
      // 获取子节点解析后的结果，并全部转换为大写
      sqlBuffer = new StringBuilder(sqlBuffer.toString().trim());
      String trimmedUppercaseSql = sqlBuffer.toString().toUpperCase(Locale.ENGLISH);
      if (trimmedUppercaseSql.length() > 0) {
        applyPrefix(sqlBuffer, trimmedUppercaseSql);  // 处理前缀
        applySuffix(sqlBuffer, trimmedUppercaseSql);  // 处理后缀
      }
      delegate.appendSql(sqlBuffer.toString()); // 将解析后的结果添加到 delegate 中
    }

    @Override
    public Map<String, Object> getBindings() {
      return delegate.getBindings();
    }

    @Override
    public void bind(String name, Object value) {
      delegate.bind(name, value);
    }

    @Override
    public int getUniqueNumber() {
      return delegate.getUniqueNumber();
    }

    @Override
    public void appendSql(String sql) {
      sqlBuffer.append(sql);
    }

    @Override
    public String getSql() {
      return delegate.getSql();
    }

    /**
     * 处理前缀前缀
     * @param sql
     * @param trimmedUppercaseSql
     */
    private void applyPrefix(StringBuilder sql, String trimmedUppercaseSql) {
      if (!prefixApplied) {  // 检测是否已经处理过前缀
        prefixApplied = true; //标记已处理过前缀
        if (prefixesToOverride != null) {
          for (String toRemove : prefixesToOverride) {
            // 遍历 prefixesToOverride 集合，如果 prefixesToOverride 中某项开头，则将改项从 SQL 语句开头删除掉
            if (trimmedUppercaseSql.startsWith(toRemove)) {
              sql.delete(0, toRemove.trim().length());
              break;
            }
          }
        }
        if (prefix != null) { // 添加 prefix 前缀
          sql.insert(0, " ");
          sql.insert(0, prefix);
        }
      }
    }

    /**
     * 处理后缀
     * @param sql
     * @param trimmedUppercaseSql
     */
    private void applySuffix(StringBuilder sql, String trimmedUppercaseSql) {
      if (!suffixApplied) {  // 检测是否已经处理过前缀
        suffixApplied = true;  //标记已处理过前缀
        if (suffixesToOverride != null) {
          // 遍历 suffixesToOverride 集合，如果 suffixesToOverride 中某项开头，则将改项从 SQL 语句开头删除掉
          for (String toRemove : suffixesToOverride) {
            if (trimmedUppercaseSql.endsWith(toRemove) || trimmedUppercaseSql.endsWith(toRemove.trim())) {
              int start = sql.length() - toRemove.trim().length();
              int end = sql.length();
              sql.delete(start, end);
              break;
            }
          }
        }
        if (suffix != null) {  // 添加 suffix 后缀
          sql.append(" ");
          sql.append(suffix);
        }
      }
    }

  }

}
