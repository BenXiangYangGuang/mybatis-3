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
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.CacheRefResolver;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.ResultMapResolver;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Discriminator;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultFlag;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/**
 * mapper 文件解析器
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLMapperBuilder extends BaseBuilder {

  private final XPathParser parser;
  private final MapperBuilderAssistant builderAssistant;
  // sql 节点片段保存
  private final Map<String, XNode> sqlFragments; // key namespace + id 全路径名称，value 为 sql 语句
  private final String resource;  // mapper 文件路径 org/apache/ibatis/builder/BlogMapper.xml

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
    this(reader, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    this(new XPathParser(reader, true, configuration.getVariables(), new XMLMapperEntityResolver()),
        configuration, resource, sqlFragments);
  }

  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
    this(inputStream, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    this(new XPathParser(inputStream, true, configuration.getVariables(), new XMLMapperEntityResolver()),
        configuration, resource, sqlFragments);
  }

  /**
   *
   * @param parser
   * @param configuration
   * @param resource mapper 文件的位置
   * @param sqlFragments configuration 中的 sqlFragments
   */
  private XMLMapperBuilder(XPathParser parser, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    super(configuration);
    this.builderAssistant = new MapperBuilderAssistant(configuration, resource);
    this.parser = parser;
    this.sqlFragments = sqlFragments;
    this.resource = resource;
  }
  /**
   * 解析 mappers 中 mapper 节点
   */
  public void parse() {
    // 判断这个 mapper 是否已经被加载
    if (!configuration.isResourceLoaded(resource)) {
      configurationElement(parser.evalNode("/mapper")); // mapper 文件中的 <mapper> 节点
      configuration.addLoadedResource(resource); // 已经解析，添加这个 mapper.xml 到资源已经解析集合
      // 映射配置文件 mapper.xml 对应 Mapper 接口进行绑定
      bindMapperForNamespace(); //注册 Mapper 接口类
    }
    // pending 即将发生的
    /**
     * XMLMapperBuilder.configurationElement（）方法解析映射配置文件时，是按照从文件头到文
     * 件尾的顺序解析的，但是时候在解析一个节点时， 引用定义在该节点之后的、还未解析的节点，这就会导致解析失败井抛出 IncompleteElementException
     *
     * 根据抛出异常的节点不同， MyBatis 会创建不同 的＊Resolver 对象， 井添加到 Configuration的不同 incomplete 集合中。
     * 例如，上面解析 Mapper 接口中的 方法出现异常时，会创建MethodResolver 对象，并将其追加到 Configuration.incompleteMethods 集合（ LinkedList<MethodResolver＞类型）中暂存；
     * 解析 ＜resultMap＞节点时出现异常， 则会将对应的ResultMapResolver 对象追加到 incompleteResultMaps ( LinkedList ResultMapResolver＞类型）集合中暂存 ；
     * 解析 <cache-ref> 节点时出现异常 ，则会将对应的 CacheRefResolver 对象追加到 incompleteCacheRefs ( LinkedList CacheRefResolver＞类型）集合中暂存：
     * 解析 SQL 语句节点时出现异常， 则会将对应的 XMLStatementBuilder 对象追加到 incompleteStatements （LinkedList<XMLStatementBuilder＞类型〉集合中暂存。
     */

    parsePendingResultMaps(); //处理 configurationElement() 方法中解析失败的 <resultMap> 节点
    parsePendingCacheRefs();  //处理 configurationElement() 方法中解析失败的 <cache-ref> 节点
    parsePendingStatements(); //处理 configurationElement() 方法中解析失败的 SQL 语句节点
  }

  public XNode getSqlFragment(String refid) {
    return sqlFragments.get(refid);
  }

  /**
   * 解析 mapper 文件的各个节点
   * @param context
   */
  private void configurationElement(XNode context) {
    try {
      // 获取<mapper> 中的 <mapper namespace="org.apache.ibatis.domain.blog.mappers.AuthorMapper">
      String namespace = context.getStringAttribute("namespace");
      if (namespace == null || namespace.equals("")) {
        throw new BuilderException("Mapper's namespace cannot be empty");
      }
      // 设置 MapperBuilderAssistant 的 currentNamespce 字段,记录当前命名空间
      builderAssistant.setCurrentNamespace(namespace);
      cacheRefElement(context.evalNode("cache-ref"));  //解析 <cache-ref> 节点
      cacheElement(context.evalNode("cache"));       //解析 <cache> 节点
      parameterMapElement(context.evalNodes("/mapper/parameterMap"));   //解析 <mapper> 下的 <parameterMap> 节点 （该节点 废弃，不再推荐使用，不做详细介绍）
      resultMapElements(context.evalNodes("/mapper/resultMap"));  //解析 <mapper> 下的 <resultMap> 节点
      sqlElement(context.evalNodes("/mapper/sql"));  //解析 <mapper> 下的 <sql> 节点; <sql> 节点 可重用的 SQL 语句片段
      buildStatementFromContext(context.evalNodes("select|insert|update|delete")); //解析 <mapper> 下的 <select|insert|update|delete> 节点
    } catch (Exception e) {
      throw new BuilderException("Error parsing Mapper XML. The XML location is '" + resource + "'. Cause: " + e, e);
    }
  }

  /**
   * 解析 <mapper> 下的 <select|insert|update|delete> 节点
   * @param list
   */
  private void buildStatementFromContext(List<XNode> list) {
    if (configuration.getDatabaseId() != null) {
      buildStatementFromContext(list, configuration.getDatabaseId());
    }
    buildStatementFromContext(list, null);
  }

  /**
   * 解析 <mapper> 下的 <select|insert|update|delete> 节点
   * @param list
   * @param requiredDatabaseId
   */
  private void buildStatementFromContext(List<XNode> list, String requiredDatabaseId) {
    for (XNode context : list) {
      // 一个 SQL 语句节点 ，一个 XMLStatementBuilder 解析对象
      final XMLStatementBuilder statementParser = new XMLStatementBuilder(configuration, builderAssistant, context, requiredDatabaseId);
      try {
        // 具体解析 SQL <select|insert|update|delete> 语句方法
        statementParser.parseStatementNode();
      } catch (IncompleteElementException e) {
        configuration.addIncompleteStatement(statementParser);
      }
    }
  }
  //处理 configurationElement() 方法中解析失败的 <resultMap> 节点
  private void parsePendingResultMaps() {

    Collection<ResultMapResolver> incompleteResultMaps = configuration.getIncompleteResultMaps();
    synchronized (incompleteResultMaps) {
      Iterator<ResultMapResolver> iter = incompleteResultMaps.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().resolve();
          iter.remove();
        } catch (IncompleteElementException e) {
          // ResultMap is still missing a resource...

        }
      }
    }
  }
  //处理 configurationElement() 方法中解析失败的 <cache-ref> 节点
  private void parsePendingCacheRefs() {
    Collection<CacheRefResolver> incompleteCacheRefs = configuration.getIncompleteCacheRefs();
    synchronized (incompleteCacheRefs) {
      Iterator<CacheRefResolver> iter = incompleteCacheRefs.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().resolveCacheRef();
          iter.remove();
        } catch (IncompleteElementException e) {
          // Cache ref is still missing a resource...
        }
      }
    }
  }
  //处理 configurationElement() 方法中解析失败的 SQL 语句节点
  private void parsePendingStatements() {
    // 获取 Configuration.incompleteStatement 集合
    Collection<XMLStatementBuilder> incompleteStatements = configuration.getIncompleteStatements();
    synchronized (incompleteStatements) { // 加锁同步
      Iterator<XMLStatementBuilder> iter = incompleteStatements.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().parseStatementNode(); // 重新解析 SQL 语句节点
          iter.remove(); // 移除 XMLStatementBuilder 对象
        } catch (IncompleteElementException e) {
          // Statement is still missing a resource...
          // 如果无法解析，则忽略改节点
        }
      }
    }
  }
  //通过前面对＜cache＞节点解析过程的介绍我们知道， XMLMapperBuilder.cacheElement（）方
  //会为每个 namespace 创建 个对应的 Cache 对象，井在 Configuration.caches 集合中记
  //namespace Cache 对象之间的对应关系。如果我们希望多个 namespace 共用同一个二级缓存，
  //即同一个 Cache 对象，则可以使用＜cache-ref>节点进行配置。
  private void cacheRefElement(XNode context) {
    if (context != null) {
      // 将当前 Mapper 配置文件的 name space 与被引用的 Cache 所在的 name space 之间的对应关单，
      // 记录 Configuration.cacheRefMap 集合中
      configuration.addCacheRef(builderAssistant.getCurrentNamespace(), context.getStringAttribute("namespace"));
      // 创建 CacheRefResolver 对象
      CacheRefResolver cacheRefResolver = new CacheRefResolver(builderAssistant, context.getStringAttribute("namespace"));
      try {
        // 解析 Cache 引用，该过程主要是设置 MapperBuil derAssistant 中的 currentCache 和 unresolvedCacheRef 字段
        cacheRefResolver.resolveCacheRef();
      } catch (IncompleteElementException e) {
        // 如采解析过程 现异常，则添加到 Configuration.incompleteCacheRefs 集合， 稍后再解析
        configuration.addIncompleteCacheRef(cacheRefResolver);
      }
    }
  }

  /**
   * 解析 <cache> 节点
   * MyBatis 有非常强大的二级缓存功能， 功能可以非常方便地进行配置， MyBatis 默认情
   * 况下没有开启二级缓存，如果要为某命名空间开启 二级缓存功能，则需要在相应映射配置文件
   * 中添加＜cache＞节点，还可以通过配置＜cache＞节点 的相关属性，为二级缓存配置相应的特性 （本
   * 质上就是添加相应的装饰器〉。
   * @param context
   */
  private void cacheElement(XNode context) {
    if (context != null) {
      // 缓存类型
      String type = context.getStringAttribute("type", "PERPETUAL");
      // 查找 type 属性对应的 Cache 接口实现
      Class<? extends Cache> typeClass = typeAliasRegistry.resolveAlias(type);
      // eviction 驱逐,赶出
      // 缓存策略
      String eviction = context.getStringAttribute("eviction", "LRU");
      // 查找 eviction 属性对应的 Cache 接口实现
      Class<? extends Cache> evictionClass = typeAliasRegistry.resolveAlias(eviction);
      // 刷新间隔
      Long flushInterval = context.getLongAttribute("flushInterval");
      //获取＜cache＞节点的 size 属性，默认位是 null
      Integer size = context.getIntAttribute("size");
      //获取＜cache＞节点的 readOnly 属性，默认位是 false
      boolean readWrite = !context.getBooleanAttribute("readOnly", false);
      //获取＜cache＞节点的 blocking 属性，默认位是 false
      boolean blocking = context.getBooleanAttribute("blocking", false);
      // 获取<cache> 节点下的子节点,用于初始化二级缓存
      //	<cache type="org.apache.ibatis.submitted.global_variables.CustomCache">
      //    <property name="stringValue" value="${stringProperty}"/>
      //    <property name="integerValue" value="${integerProperty}"/>
      //    <property name="longValue" value="${longProperty}"/>
      //  </cache>
      Properties props = context.getChildrenAsProperties();
      // 通过 MapperBuilderAssisatant 创建 cache 对象, 并添加到 Configuration.caches 集合
      builderAssistant.useNewCache(typeClass, evictionClass, flushInterval, size, readWrite, blocking, props);
    }
  }

  /**
   * j解析 mapper 中的 parameterMap
   * @param list
   */
  private void parameterMapElement(List<XNode> list) {
    for (XNode parameterMapNode : list) {
      String id = parameterMapNode.getStringAttribute("id");
      String type = parameterMapNode.getStringAttribute("type");
      Class<?> parameterClass = resolveClass(type);
      List<XNode> parameterNodes = parameterMapNode.evalNodes("parameter");
      List<ParameterMapping> parameterMappings = new ArrayList<>();
      for (XNode parameterNode : parameterNodes) {
        String property = parameterNode.getStringAttribute("property");
        String javaType = parameterNode.getStringAttribute("javaType");
        String jdbcType = parameterNode.getStringAttribute("jdbcType");
        String resultMap = parameterNode.getStringAttribute("resultMap");
        String mode = parameterNode.getStringAttribute("mode");
        String typeHandler = parameterNode.getStringAttribute("typeHandler");
        Integer numericScale = parameterNode.getIntAttribute("numericScale");
        ParameterMode modeEnum = resolveParameterMode(mode);
        Class<?> javaTypeClass = resolveClass(javaType);
        JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
        Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
        ParameterMapping parameterMapping = builderAssistant.buildParameterMapping(parameterClass, property, javaTypeClass, jdbcTypeEnum, resultMap, modeEnum, typeHandlerClass, numericScale);
        parameterMappings.add(parameterMapping);
      }
      builderAssistant.addParameterMap(id, parameterClass, parameterMappings);
    }
  }

  private void resultMapElements(List<XNode> list) throws Exception {
    for (XNode resultMapNode : list) {
      try {
        resultMapElement(resultMapNode);
      } catch (IncompleteElementException e) {
        // ignore, it will be retried
      }
    }
  }

  private ResultMap resultMapElement(XNode resultMapNode) throws Exception {
    return resultMapElement(resultMapNode, Collections.emptyList(), null);
  }

  /**
   * 解析 <resultMap> 节点
   * 	<resultMap id="selectAuthor" type="org.apache.ibatis.domain.blog.Author">
   * 		<id column="id" property="id" />
   * 		<result property="username" column="username" />
   * 		<result property="password" column="password" />
   * 		<result property="email" column="email" />
   * 		<result property="bio" column="bio" />
   * 		<result property="favouriteSection" column="favourite_section" />
   * 	</resultMap>
   * @param resultMapNode
   * @param additionalResultMappings 额外的映射关系
   * @param enclosingType
   * @return
   * @throws Exception
   */
  private ResultMap resultMapElement(XNode resultMapNode, List<ResultMapping> additionalResultMappings, Class<?> enclosingType) throws Exception {
    ErrorContext.instance().activity("processing " + resultMapNode.getValueBasedIdentifier());
    String type = resultMapNode.getStringAttribute("type",
        resultMapNode.getStringAttribute("ofType",
            resultMapNode.getStringAttribute("resultType",
                resultMapNode.getStringAttribute("javaType"))));
    // type 实体对象类
    Class<?> typeClass = resolveClass(type);
    if (typeClass == null) {
      typeClass = inheritEnclosingType(resultMapNode, enclosingType);
    }
    // 辨别器，相当于 java 中的 switch 语法
    Discriminator discriminator = null;
    List<ResultMapping> resultMappings = new ArrayList<>(); // resultMap 中的 <result property="username" column="username" /> 对应关系列的 存放集合
    resultMappings.addAll(additionalResultMappings); // 添加额外的映射关系
    List<XNode> resultChildren = resultMapNode.getChildren();
    for (XNode resultChild : resultChildren) {
      // 处理 <constructor> 节点
      if ("constructor".equals(resultChild.getName())) {
        processConstructorElement(resultChild, typeClass, resultMappings);
        // 处理 <discriminator> 节点
      } else if ("discriminator".equals(resultChild.getName())) {
        discriminator = processDiscriminatorElement(resultChild, typeClass, resultMappings);
      } else {
        // 处理 <id＞、＜result＞、 <association＞、＜collection＞等节点
        // id constructor 标识集合， 一个对应关系列的标志，用于一些特殊标志（id constructor）的解析
        List<ResultFlag> flags = new ArrayList<>();
        if ("id".equals(resultChild.getName())) {
          flags.add(ResultFlag.ID);
        }
        // 创建 ResultMapping 对象，添加 ResultMapping 结果到集合
        resultMappings.add(buildResultMappingFromContext(resultChild, typeClass, flags));
      }
    }
    String id = resultMapNode.getStringAttribute("id",
            resultMapNode.getValueBasedIdentifier());
    String extend = resultMapNode.getStringAttribute("extends");
    // 自动映射
    Boolean autoMapping = resultMapNode.getBooleanAttribute("autoMapping");
    // ResultMap 对象构建解决器
    ResultMapResolver resultMapResolver = new ResultMapResolver(builderAssistant, id, typeClass, extend, discriminator, resultMappings, autoMapping);
    try {
      //创建 ResultMap 对象，并添加到 Configuration.resultMaps 集合中，该集合是 StrictMap 类型
      return resultMapResolver.resolve();
    } catch (IncompleteElementException  e) {
      configuration.addIncompleteResultMap(resultMapResolver);
      throw e;
    }
  }

  protected Class<?> inheritEnclosingType(XNode resultMapNode, Class<?> enclosingType) {
    if ("association".equals(resultMapNode.getName()) && resultMapNode.getStringAttribute("resultMap") == null) {
      String property = resultMapNode.getStringAttribute("property");
      if (property != null && enclosingType != null) {
        MetaClass metaResultType = MetaClass.forClass(enclosingType, configuration.getReflectorFactory());
        return metaResultType.getSetterType(property);
      }
    } else if ("case".equals(resultMapNode.getName()) && resultMapNode.getStringAttribute("resultMap") == null) {
      return enclosingType;
    }
    return null;
  }

  /**
   * 解析 <constructor> 节点
   * @param resultChild
   * @param resultType
   * @param resultMappings
   * @throws Exception
   */
  private void processConstructorElement(XNode resultChild, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
    List<XNode> argChildren = resultChild.getChildren();
    for (XNode argChild : argChildren) {
      List<ResultFlag> flags = new ArrayList<>();
      flags.add(ResultFlag.CONSTRUCTOR);
      if ("idArg".equals(argChild.getName())) {
        flags.add(ResultFlag.ID);
      }
      resultMappings.add(buildResultMappingFromContext(argChild, resultType, flags));
    }
  }

  /**
   * 构建一个 Discriminator 对象
   *
   * <resultMap id="vehicleResult" type="Vehicle">
   *   <id property="id" column="id" />
   *   <result property="vin" column="vin"/>
   *   <result property="year" column="year"/>
   *   <result property="make" column="make"/>
   *   <result property="model" column="model"/>
   *   <result property="color" column="color"/>
   *   <discriminator javaType="int" column="vehicle_type">
   *     <case value="1" resultMap="carResult"/>
   *     <case value="2" resultMap="truckResult"/>
   *     <case value="3" resultMap="vanResult"/>
   *     <case value="4" resultMap="suvResult"/>
   *   </discriminator>
   * </resultMap>
   * 参考官方文档:https://mybatis.org/mybatis-3/sqlmap-xml.html
   * @param context
   * @param resultType
   * @param resultMappings
   * @return
   * @throws Exception
   */
  private Discriminator processDiscriminatorElement(XNode context, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
    String column = context.getStringAttribute("column");
    String javaType = context.getStringAttribute("javaType");
    String jdbcType = context.getStringAttribute("jdbcType");
    String typeHandler = context.getStringAttribute("typeHandler");
    Class<?> javaTypeClass = resolveClass(javaType);
    Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
    Map<String, String> discriminatorMap = new HashMap<>();
    for (XNode caseChild : context.getChildren()) {
      String value = caseChild.getStringAttribute("value");
      String resultMap = caseChild.getStringAttribute("resultMap", processNestedResultMappings(caseChild, resultMappings, resultType));
      discriminatorMap.put(value, resultMap);
    }
    return builderAssistant.buildDiscriminator(resultType, column, javaTypeClass, jdbcTypeEnum, typeHandlerClass, discriminatorMap);
  }

  /**
   * 解析 sql 节点
   * @param list
   */
  private void sqlElement(List<XNode> list) {
    if (configuration.getDatabaseId() != null) {
      sqlElement(list, configuration.getDatabaseId());
    }
    sqlElement(list, null);
  }

  /**
   * 解析 sql 节点
   * @param list
   * @param requiredDatabaseId
   */
  private void sqlElement(List<XNode> list, String requiredDatabaseId) {
    for (XNode context : list) {
      String databaseId = context.getStringAttribute("databaseId");
      String id = context.getStringAttribute("id");
      // 根据命名空间 更新 id  全名称
      id = builderAssistant.applyCurrentNamespace(id, false);
      // 检测＜sql＞的 databaseId 与当前 Configuration 中记录的 databaseId 是否一致
      if (databaseIdMatchesCurrent(id, databaseId, requiredDatabaseId)) {
        sqlFragments.put(id, context);
      }
    }
  }

  /**
   * 判断 databaseId  和要求的 requiredDatabaseId 是否相等，或者是否 sqlFragments 是否包含 这个 <sql> 节点
   * @param id
   * @param databaseId
   * @param requiredDatabaseId
   * @return
   */
  private boolean databaseIdMatchesCurrent(String id, String databaseId, String requiredDatabaseId) {
    if (requiredDatabaseId != null) {
      return requiredDatabaseId.equals(databaseId);
    }
    if (databaseId != null) {
      return false;
    }
    if (!this.sqlFragments.containsKey(id)) {
      return true;
    }
    // skip this fragment if there is a previous one with a not null databaseId
    XNode context = this.sqlFragments.get(id);
    return context.getStringAttribute("databaseId") == null;
  }

  /**
   * 构建一个 ResultMapping 对象
   * @param context
   * @param resultType
   * @param flags
   * @return
   * @throws Exception
   */
  private ResultMapping buildResultMappingFromContext(XNode context, Class<?> resultType, List<ResultFlag> flags) throws Exception {
    String property;
    if (flags.contains(ResultFlag.CONSTRUCTOR)) {
      property = context.getStringAttribute("name");
    } else {
      property = context.getStringAttribute("property");
    }
    String column = context.getStringAttribute("column");
    String javaType = context.getStringAttribute("javaType");
    String jdbcType = context.getStringAttribute("jdbcType");
    String nestedSelect = context.getStringAttribute("select");
    // 内部 ResultMaping 映射处理
    String nestedResultMap = context.getStringAttribute("resultMap",
        processNestedResultMappings(context, Collections.emptyList(), resultType));
    String notNullColumn = context.getStringAttribute("notNullColumn");
    String columnPrefix = context.getStringAttribute("columnPrefix");
    String typeHandler = context.getStringAttribute("typeHandler");
    String resultSet = context.getStringAttribute("resultSet");
    String foreignColumn = context.getStringAttribute("foreignColumn");
    boolean lazy = "lazy".equals(context.getStringAttribute("fetchType", configuration.isLazyLoadingEnabled() ? "lazy" : "eager"));
    Class<?> javaTypeClass = resolveClass(javaType);
    Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
    return builderAssistant.buildResultMapping(resultType, property, column, javaTypeClass, jdbcTypeEnum, nestedSelect, nestedResultMap, notNullColumn, columnPrefix, typeHandlerClass, flags, resultSet, foreignColumn, lazy);
  }

  private String processNestedResultMappings(XNode context, List<ResultMapping> resultMappings, Class<?> enclosingType) throws Exception {
    if ("association".equals(context.getName())
        || "collection".equals(context.getName())
        || "case".equals(context.getName())) {
      if (context.getStringAttribute("select") == null) {
        validateCollection(context, enclosingType);
        ResultMap resultMap = resultMapElement(context, resultMappings, enclosingType);
        return resultMap.getId();
      }
    }
    return null;
  }

  protected void validateCollection(XNode context, Class<?> enclosingType) {
    if ("collection".equals(context.getName()) && context.getStringAttribute("resultMap") == null
        && context.getStringAttribute("javaType") == null) {
      MetaClass metaResultType = MetaClass.forClass(enclosingType, configuration.getReflectorFactory());
      String property = context.getStringAttribute("property");
      if (!metaResultType.hasSetter(property)) {
        throw new BuilderException(
          "Ambiguous collection type for property '" + property + "'. You must specify 'javaType' or 'resultMap'.");
      }
    }
  }

  /**
   * 映射配置文件 mapper.xml 对应 Mapper 接口进行绑定
   *
   * 注册 AuthorMapper 接口到 Configuration MapperRegistry 中
   * public interface AuthorMapper
   */
  private void bindMapperForNamespace() {
    // 获取映射配置文件的命名空间
    String namespace = builderAssistant.getCurrentNamespace();
    if (namespace != null) {
      Class<?> boundType = null;
      try {
        boundType = Resources.classForName(namespace); //解析命名空间对应的类型
      } catch (ClassNotFoundException e) {
        //ignore, bound type is not required
      }
      if (boundType != null) {
        if (!configuration.hasMapper(boundType)) {  //是否已经加载了 boundType 接口
          // Spring may not know the real resource name so we set a flag
          // to prevent loading again this resource from the mapper interface
          // look at MapperAnnotationBuilder#loadXmlResource
          // 追加 namespace 前缀，并添加到 Configuration.loadedResources 集合中保存
          configuration.addLoadedResource("namespace:" + namespace);
          // 注册 boundType 接口
          configuration.addMapper(boundType);
        }
      }
    }
  }

}
