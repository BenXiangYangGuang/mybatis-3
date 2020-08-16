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
package org.apache.ibatis.scripting.xmltags;

import java.util.Map;

import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.session.Configuration;

/**
 * 处理 <ForEach> 节点
 * 在动态 SQL 语句中构建 IN 条件语句的时候，通常需要对一个集合进行法代， MyBatis 提供了＜foreach＞标签实现该功能。
 * 在使用＜foreach＞标签法代集合时，不仅可以使用集合的元素和索引值，还可以在循环开始之前或结束之后添加指定的字符串，也允许在迭代过程中添加指定的分隔符。
 * @author Clinton Begin
 */
public class ForEachSqlNode implements SqlNode {
  public static final String ITEM_PREFIX = "__frch_";

  // 用于判断循环的终止条件， ForeachSqlNode 构造方法中会创建该对象
  private final ExpressionEvaluator evaluator;
  // 迭代的集合表达式
  private final String collectionExpression;
  // 记录了该 foreachSqlNode 节点的子节点
  private final SqlNode contents;
  // 在循环开始前要添加的字符串
  private final String open;
  // 在循环结束后要添加的字符串
  private final String close;
  private final String separator; // 循环过程中，每项之间的分隔符

  // index 是当前迭代的次数， item 的值是本次选代的元素。 若迭代集合是 Map ，则 index 是键， item 是值
  private final String item;
  private final String index;
  private final Configuration configuration;

  public ForEachSqlNode(Configuration configuration, SqlNode contents, String collectionExpression, String index, String item, String open, String close, String separator) {
    this.evaluator = new ExpressionEvaluator();
    this.collectionExpression = collectionExpression;
    this.contents = contents;
    this.open = open;
    this.close = close;
    this.separator = separator;
    this.index = index;
    this.item = item;
    this.configuration = configuration;
  }

  @Override
  public boolean apply(DynamicContext context) {
    // 获取参数信息
    Map<String, Object> bindings = context.getBindings();
    // 步骤1:解析集合表达式对应的实际参数
    final Iterable<?> iterable = evaluator.evaluateIterable(collectionExpression, bindings);
    if (!iterable.iterator().hasNext()) {
      return true;
    }
    boolean first = true;
    //步骤2：在循环开始之前，调用 DynamicContext.appendSql()方法添 open 指定的字符串
    applyOpen(context);
    int i = 0;
    for (Object o : iterable) {
      // 记录当前 DynamicContext 对象
      DynamicContext oldContext = context;
      // 步骤3 ：创建 PrefixedContext ，并让 context 指向该 PrefixedContext 对象
      if (first || separator == null) {
        // 如采是集合的第一项，则将 PrefixedContext.prefix 初始化为空字符串
        // 未指定分隔符，如 PrefixedContext.prefix 初始化为空字符串
        context = new PrefixedContext(context, "");
      } else {
        // 如采指定了分隔符，则 PrefixedContext.prefix 初始化为指定分隔符
        context = new PrefixedContext(context, separator);
      }
      // uniqueNumber 0 开始，每次递增 1，用于转换生成新的"#{}"占位符名称
      int uniqueNumber = context.getUniqueNumber();
      // Issue #709
      if (o instanceof Map.Entry) {
        // 如采集合是 Map 类型，将集合中 key 和 value 添加到 DynamicContext.bindings 集合中保存
        @SuppressWarnings("unchecked")
        Map.Entry<Object, Object> mapEntry = (Map.Entry<Object, Object>) o;
        applyIndex(context, mapEntry.getKey(), uniqueNumber); // 步骤4
        applyItem(context, mapEntry.getValue(), uniqueNumber); // 步骤5
      } else {
        applyIndex(context, i, uniqueNumber);
        applyItem(context, o, uniqueNumber);
      }
      // 步骤6: 调用子节点的 apply()方法进行处理，注意 ，这里使用的 FilteredDynamicContext 对象
      contents.apply(new FilteredDynamicContext(configuration, context, index, item, uniqueNumber));
      if (first) {
        first = !((PrefixedContext) context).isPrefixApplied();
      }
      context = oldContext;  // 还原成原来的 content
      i++;
    }
    // 步骤7：循环结束后，调用 DynamicContext.appendSql() 方法添加 close 指定的字符串
    applyClose(context);
    context.getBindings().remove(item);
    context.getBindings().remove(index);
    return true;
  }

  private void applyIndex(DynamicContext context, Object o, int i) {
    if (index != null) {
      context.bind(index, o);
      context.bind(itemizeItem(index, i), o);
    }
  }

  private void applyItem(DynamicContext context, Object o, int i) {
    if (item != null) {
      context.bind(item, o);
      context.bind(itemizeItem(item, i), o);
    }
  }

  private void applyOpen(DynamicContext context) {
    if (open != null) {
      context.appendSql(open);
    }
  }

  private void applyClose(DynamicContext context) {
    if (close != null) {
      context.appendSql(close);
    }
  }

  private static String itemizeItem(String item, int i) {
    return ITEM_PREFIX + item + "_" + i;
  }

  /**
   * FilteredDynamicContext 继承了 DynamicContext ，同时也是 DynamicContext 的代理类。
   * FilteredDynamicContext 负责处理 "#{}" 占位符，但并未完全解析 "#{}" 占位符
   */
  private static class FilteredDynamicContext extends DynamicContext {
    private final DynamicContext delegate;  // 底层封装的 DynamicContext 对象
    private final int index;  // 对应集合项在集合中的索引位置
    private final String itemIndex; // 对应集合项的 index ，参见对 ForeachSqlNode.index 字段的介绍
    private final String item;  // 对应集合项的 item ，参见对 ForeachSqlNode.item 字段的介绍

    public FilteredDynamicContext(Configuration configuration,DynamicContext delegate, String itemIndex, String item, int i) {
      super(configuration, null);
      this.delegate = delegate;
      this.index = i;
      this.itemIndex = itemIndex;
      this.item = item;
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
    public String getSql() {
      return delegate.getSql();
    }

    /**
     * appendSql() 方法会将“#{item}”占位符转换成“＃{＿frch_ item_ 1}”的格式，
     * 其中“_frch_”是固定的前缀，"item" 与处理前的占位符一样，未发生改变， 1 则是
     * FilteredDynamicContext 产生的单调递增值；还会将“#{itemIndex}”占位符转换成
     * "#{_frch_itemIndex_1"的格式，其中各个部分的含义同上。
     *
     * @param sql
     */
    @Override
    public void appendSql(String sql) {
      // 创建 GenericTokenParser 解析器，注意这里匿名实现的 TokenHandler 对象
      GenericTokenParser parser = new GenericTokenParser("#{", "}", content -> {
        // 对 item 处理
        String newContent = content.replaceFirst("^\\s*" + item + "(?![^.,:\\s])", itemizeItem(item, index));
        if (itemIndex != null && newContent.equals(content)) {
          // 对 itemIndex 进行处理
          newContent = content.replaceFirst("^\\s*" + itemIndex + "(?![^.,:\\s])", itemizeItem(itemIndex, index));
        }
        return "#{" + newContent + "}";
      });
      // 将解析后的 SQL 语句片段追加到 delegate 中保存
      delegate.appendSql(parser.parse(sql));
    }

    @Override
    public int getUniqueNumber() {
      return delegate.getUniqueNumber();
    }

  }

  /**
   * PrefixedContext 继承了 DynamicContext ，同时也是 DynamicContext 的代理类。
   */
  private class PrefixedContext extends DynamicContext {
    private final DynamicContext delegate; // 底层封装的 DynamicContext 对象
    private final String prefix;   // 指定的前缀
    private boolean prefixApplied; // 是否已经处理过前缀

    public PrefixedContext(DynamicContext delegate, String prefix) {
      super(configuration, null);
      this.delegate = delegate;
      this.prefix = prefix;
      this.prefixApplied = false;
    }

    public boolean isPrefixApplied() {
      return prefixApplied;
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
    public void appendSql(String sql) {
      if (!prefixApplied && sql != null && sql.trim().length() > 0) { // 判断是否需要追加前缀
        delegate.appendSql(prefix);   // 追加前缀
        prefixApplied = true;  // 表示已经处理过前缀
      }
      delegate.appendSql(sql); // 追加 sql 片段
    }

    @Override
    public String getSql() {
      return delegate.getSql();
    }

    @Override
    public int getUniqueNumber() {
      return delegate.getUniqueNumber();
    }
  }

}
