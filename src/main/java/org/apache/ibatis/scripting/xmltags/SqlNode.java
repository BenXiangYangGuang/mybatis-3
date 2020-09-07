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
 * SqlNode 是 mapper.xml 文件中的 SQL 语句节点中，动态节点片段的解析后的对象;
 *
 * SqlNode 接口有多个实现类，每个实现类对应一个动态 SQL 节点;按照组合模式的角色来划分,
 * SqlNode 扮演了抽象组件的角色, MixedSqlNode 扮演了树枝节点的角色, TextSqlNode 扮演了树叶节点的角色。
 *
 * TextSqlNode StaticTextSql 等都是 SqIN ode 接口的实现， SqlNode 接口的
 * 每个实现都对应于不同的动态 SQL 节点类型
 * @author Clinton Begin
 */
public interface SqlNode {
  /**
   * apply() 方法会根据用户传入的实参,解析改 SqlNode 所记录的动态 SQL 节点,
   * 并调用 DynamicContext.appendSql() 方法将解析后的 SQL 片段追加到 DynamicContext.sqlBuilder 中保存；
   * 当 SQL 节点下的所有 SqlNode 完成解析后,我们就可以从 DynamicContext 中获取一条动态生成的、完整的 SQL 语句
   * @param context
   * @return
   */
  boolean apply(DynamicContext context);
}
