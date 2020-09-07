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

/**
 * ParameterMode 枚举类型表示存储过程传参
 * in ：给参数传入值，定义的参数就得到了值
 * out：模式定义的参数只能在过程体内部赋值，表示该参数可以将某个值传递回调用他的过程（在存储过程内部，该参数初始值为 null，无论调用者是否给存储过程参数设置值）
 * inout：调用者还可以通过 inout 参数传递值给存储过程，也可以从存储过程内部传值给调用者
 *
 *
 * 如果仅仅想把数据传给 MySQL 存储过程，那就使用“in” 类型参数；
 * 如果仅仅从 MySQL 存储过程返回值，那就使用“out” 类型参数；
 * 如果需要把数据传给 MySQL 存储过程，还要经过一些计算后再传回给我们，此时，要使用“inout” 类型参数。
 * MySQL 存储过程参数如果不显式指定"in"、"out"、"inout"，则默认为"in"。
 *
 * @author Clinton Begin
 */
public enum ParameterMode {
  IN, OUT, INOUT
}
