/*
 * Copyright 2010 @ashigeru.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package com.ashigeru.appengine.tools.classload;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;

/**
 * {@link DatastoreClassLoaderDelegate}のテスト。
 */
public class DatastoreClassLoaderDelegateTest {

    LocalServiceTestHelper testing;
    {
        LocalDatastoreServiceTestConfig datastore = new LocalDatastoreServiceTestConfig();
        datastore.setBackingStoreLocation("target/testing/datastore");
        testing = new LocalServiceTestHelper(datastore);
        testing.setEnvAppId(getClass().getSimpleName());
    }

    /**
     * テストを初期化する。
     * @throws Exception if occur
     */
    @Before
    public void setUp() throws Exception {
        testing.setUp();
    }

    /**
     * テストの情報を破棄する。
     * @throws Exception 例外が発生した場合
     */
    @After
    public void tearDown() throws Exception {
        testing.tearDown();
    }

    /**
     * Test method for {@link DatastoreClassLoaderDelegate#findClass(String)}.
     * @throws Exception if occur
     */
    @Test
    public void 登録してあるクラスを発見できる() throws Exception {
        ResourceStore store = new ResourceStore(
            DatastoreServiceFactory.getDatastoreService(),
            "T");
        InterceptClassLoader loader = new InterceptClassLoader(
            getClass().getClassLoader(),
            Pattern.compile("com/example/.*"),
            Arrays.asList(new DatastoreClassLoaderDelegate(store)));

        store.put(
            "com/example/Hello.class",
            Util.createClass("com.example.Hello", "Hello, world!"));

        assertThat(
            "登録してあるクラスを発見できる",
            loader.loadClass("com.example.Hello").newInstance().toString(),
            is("Hello, world!"));

        try {
            loader.loadClass("com.example.Missing");
            fail("登録してないクラスは発見できない");
        }
        catch (ClassNotFoundException e) {
            // ok.
        }
    }

    /**
     * Test method for {@link DatastoreClassLoaderDelegate#findResourceAsStream(String)}.
     */
    @Test
    public void 登録してあるリソースを発見できる() {
        ResourceStore store = new ResourceStore(
            DatastoreServiceFactory.getDatastoreService(),
            "T");
        InterceptClassLoader loader = new InterceptClassLoader(
            getClass().getClassLoader(),
            Pattern.compile("com/example/.*"),
            Arrays.asList(new DatastoreClassLoaderDelegate(store)));

        store.put(
            "com/example/hello.txt",
            "Hello, world!".getBytes());

        assertThat(
            "登録してあるリソースを発見できる",
            Util.toString(loader.getResourceAsStream("com/example/hello.txt")),
            is("Hello, world!"));

        assertThat(
            "登録してないリソースは発見できない",
            Util.toString(loader.getResourceAsStream("com/example/missing.txt")),
            is(nullValue()));
    }
}
