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
package org.apache.ibatis.builder;

import org.apache.ibatis.cache.Cache;

/**
 * 根据 cacheRef namespace 找到 对应点 Cache
 * cacheRefResolver 是一个简单的 cache 引用解析器， 其中封装了被引用 namespace 以及
 * 当前 XMLMapperBuilder 对应的 MapperBuilderAssistant 对象 CacheRefResolver.resolveCacheRef()
 * 方法会调 MapperBuilder Assistant. useCacheRef（） 方法。在 MapperBuilder Assistant.useCacheRef()
 * 方法中会通过 namespace 找被引用的 Cache 对象
 * @author Clinton Begin
 */
public class CacheRefResolver {
  private final MapperBuilderAssistant assistant;
  private final String cacheRefNamespace;

  public CacheRefResolver(MapperBuilderAssistant assistant, String cacheRefNamespace) {
    this.assistant = assistant;
    this.cacheRefNamespace = cacheRefNamespace;
  }

  public Cache resolveCacheRef() {
    return assistant.useCacheRef(cacheRefNamespace);
  }
}
