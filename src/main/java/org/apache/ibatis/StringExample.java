package org.apache.ibatis;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Objects;

/**
 * @Author: fei2
 * @Date: 2020/4/29 上午11:04
 * @Description:
 * @Refer To:
 */
public final class StringExample {
  private String age;

  public void setAge(String age) {
    this.age = age;
  }

  public String getAge() {
    return age;
  }


  public static void main(String[] args) {
    char data[] = {'a','b','c'};
    String five = new String(data);

    String one = "abc";
    String two = one ;

    String six = "abc";
    String three = "abc" + one ;

    String four = one + two;
    String seven = "abcabc";
    System.out.println(four);

    String name = "baeldung";
    String newName = name.replace("dung", "----");


    System.out.println(newName);

    final StringExample ex = new StringExample();
    ex.setAge("22");

    System.out.println(ex.getAge());


    System.out.println("Classloader of ArrayList:"
      + ArrayList.class.getClassLoader());


    System.out.println(Charset.defaultCharset().displayName());
  }
}
