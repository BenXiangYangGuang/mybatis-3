package org.apache.ibatis.reflection.property;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @Author: fei2
 * @Date: 2020/3/4 下午3:21
 * @Description:
 * @Refer To:
 */
class PropertyTokenizerTest {

    @Test
    void next() {
      String fullName = "orders[0].items[0].name";
      PropertyTokenizer prop = new PropertyTokenizer(fullName);
      assertEquals("items[0].name",prop.getChildren(),"full name children");
    }
}
