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
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;

/**
 * {@link ResourceStore}のテスト.
 */
public class ResourceStoreTest {

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
     * 存在しないリソースは取得できない。
     */
    @Test
    public void 存在しないエンティティ() {
        ResourceStore store = new ResourceStore(
            DatastoreServiceFactory.getDatastoreService(),
            "A");

        assertThat(
            "まだ追加していないリソースは取得できない",
            store.get("hello.txt"),
            is(nullValue()));
    }

    /**
     * 追加したリソースを取得できる。
     */
    @Test
    public void 追加したリソースを取得できる() {
        ResourceStore store = new ResourceStore(
            DatastoreServiceFactory.getDatastoreService(),
            "A");

        store.put("hello.txt", conv("Hello, world!"));
        assertThat(
            "追加したリソースを取得できる",
            conv(store.get("hello.txt")),
            is("Hello, world!"));
    }

    /**
     * 複数のリソースを追加できる。
     */
    @Test
    public void 複数のリソースを追加できる() {
        ResourceStore store = new ResourceStore(
            DatastoreServiceFactory.getDatastoreService(),
            "A");

        store.put("a.txt", conv("a"));
        store.put("b.txt", conv("b"));
        store.put("c.txt", conv("c"));
        assertThat(conv(store.get("a.txt")), is("a"));
        assertThat(conv(store.get("b.txt")), is("b"));
        assertThat(conv(store.get("c.txt")), is("c"));
    }

    /**
     * リソースを上書きできる。
     */
    @Test
    public void リソースを上書きできる() {
        ResourceStore store = new ResourceStore(
            DatastoreServiceFactory.getDatastoreService(),
            "A");

        store.put("a.txt", conv("a"));
        store.put("b.txt", conv("b"));
        assertThat(conv(store.get("a.txt")), is("a"));
        assertThat(conv(store.get("b.txt")), is("b"));

        store.put("a.txt", conv("A"));
        assertThat(conv(store.get("a.txt")), is("A"));
        assertThat(conv(store.get("b.txt")), is("b"));
    }

    /**
     * リソースを削除できる。
     */
    @Test
    public void リソースを削除できる() {
        ResourceStore store = new ResourceStore(
            DatastoreServiceFactory.getDatastoreService(),
            "A");

        store.put("a.txt", conv("a"));
        store.put("b.txt", conv("b"));
        assertThat(conv(store.get("a.txt")), is("a"));
        assertThat(conv(store.get("b.txt")), is("b"));

        store.delete("a.txt");
        assertThat(conv(store.get("a.txt")), is(nullValue()));
        assertThat(conv(store.get("b.txt")), is("b"));

        store.delete("c.txt"); // ok
    }

    /**
     * カインドごとに名前空間を分けられる。
     */
    @Test
    public void カインドごとに名前空間を分けられる() {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        ResourceStore a = new ResourceStore(datastore, "A");
        ResourceStore b = new ResourceStore(datastore, "B");

        a.put("a.txt", conv("a"));
        b.put("a.txt", conv("b"));

        assertThat(conv(a.get("a.txt")), is("a"));
        assertThat(conv(b.get("a.txt")), is("b"));
    }

    /**
     * パスのような複雑な名前を指定できる。
     */
    @Test
    public void パスのような複雑な名前を指定できる() {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        ResourceStore store = new ResourceStore(datastore, "A");

        store.put(
            "com/example/~$New Document (1).doc",
            conv("a"));

        assertThat(
            conv(store.get("com/example/~$New Document (1).doc")),
            is("a"));

        store.delete("com/example/~$New Document (1).doc");

        assertThat(
            conv(store.get("com/example/~$New Document (1).doc")),
            is(nullValue()));
    }

    /**
     * 追加したリソースのカインドで検索できる。
     */
    @Test
    public void 追加したリソースのカインドで検索できる() {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        ResourceStore store = new ResourceStore(datastore, "Testing");
        assertThat(
            "追加前のカウント数は0",
            datastore.prepare(new Query("Testing")).countEntities(),
            is(0));

        store.put("hello.txt", conv("Hello, world!"));
        assertThat(
            "追加後のカウント数は1",
            datastore.prepare(new Query("Testing")).countEntities(),
            is(1));

        store.put("hello2.txt", conv("Hello, world!"));
        assertThat(
            "さらに追加後のカウント数は2",
            datastore.prepare(new Query("Testing")).countEntities(),
            is(2));

        store.delete("hello.txt");
        assertThat(
            "1つ消すとカウント数は1",
            datastore.prepare(new Query("Testing")).countEntities(),
            is(1));
    }

    /**
     * 複数のリソースを同時に操作できる。
     */
    @Test
    public void 複数のリソースを同時に操作できる() {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        ResourceStore store = new ResourceStore(datastore, "Testing");

        Map<String, byte[]> map = new HashMap<String, byte[]>();
        map.put("a.txt", conv("A"));
        map.put("b.txt", conv("B"));
        map.put("c.txt", conv("C"));
        map.put("d.txt", conv("D"));
        store.put(map);

        assertThat(conv(store.get("a.txt")), is("A"));
        assertThat(conv(store.get("b.txt")), is("B"));
        assertThat(conv(store.get("c.txt")), is("C"));
        assertThat(conv(store.get("d.txt")), is("D"));
        assertThat(conv(store.get("e.txt")), is(nullValue()));

        Map<String, byte[]> get = store.get(Arrays.asList(
            "a.txt", "c.txt", "e.txt"));
        assertThat(get.size(), is(2));
        assertThat(conv(get.get("a.txt")), is("A"));
        assertThat(conv(get.get("c.txt")), is("C"));

        store.delete(Arrays.asList("b.txt", "d.txt", "e.txt"));
        assertThat(conv(store.get("a.txt")), is("A"));
        assertThat(conv(store.get("b.txt")), is(nullValue()));
        assertThat(conv(store.get("c.txt")), is("C"));
        assertThat(conv(store.get("d.txt")), is(nullValue()));
    }

    private byte[] conv(String string) {
        return string.getBytes();
    }

    private String conv(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return new String(bytes);
    }
}
