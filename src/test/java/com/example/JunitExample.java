package com.example;

import org.junit.Assert;
import org.junit.Test;

public class JunitExample {

    @Test
    public void test() {
        String tested = "Test";
        Assert.assertEquals("Test", tested);
    }

}
