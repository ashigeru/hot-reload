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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.Test;

/**
 * {@link InterceptClassLoader}のテスト。
 */
public class InterceptClassLoaderTest {

    /**
     * Test method for {@link InterceptClassLoader#loadClass(String, boolean)}.
     */
    @Test
    public void loadClass_フィルタがかかっていないクラス() {
        MockClassLoaderDelegate delegate = new MockClassLoaderDelegate();
        InterceptClassLoader loader = new InterceptClassLoader(
            getClass().getClassLoader(),
            Pattern.compile("com/example/.*"),
            Arrays.asList(delegate));

        delegate.define("com.example.Hello");

        assertThat(
            "存在しているクラスはロードできる",
            Util.toString(loader, "com.example.Hello"),
            is("Hello"));

        assertThat(
            "存在しないクラスはロードできない",
            Util.toString(loader, "com.example.NotDefined"),
            is(nullValue()));
    }

    /**
     * Test method for {@link InterceptClassLoader#loadClass(String, boolean)}.
     * @throws Exception if occur
     */
    @Test
    public void loadClass_フィルタがかかっているクラス() throws Exception {
        MockClassLoaderDelegate delegate = new MockClassLoaderDelegate();
        InterceptClassLoader loader = new InterceptClassLoader(
            getClass().getClassLoader(),
            Pattern.compile("java/.*"),
            Arrays.asList(delegate));

        delegate.define("com.example.Hello");

        assertThat(
            "パターンにマッチしないクラスはロードできない",
            Util.toString(loader, "com.example.Hello"),
            is(nullValue()));

        Class<?> jtable = loader.loadClass("javax.swing.JTable");
        assertThat(
            "規約で禁止されているクラスはこのローダでロードできない",
            jtable.getClassLoader(),
            not(sameInstance((Object) loader)));
    }

    /**
     * Test method for {@link InterceptClassLoader#loadClass(String, boolean)}.
     * @throws Exception if occur
     */
    @Test
    public void loadClass_子クラスローダでのロードを優先() throws Exception {
        MockClassLoaderDelegate dParent = new MockClassLoaderDelegate();
        InterceptClassLoader parent = new InterceptClassLoader(
            getClass().getClassLoader(),
            Pattern.compile("com/example/.*"),
            Arrays.asList(dParent));
        dParent.define("com.example.Parent");
        dParent.define("com.example.Hello");

        MockClassLoaderDelegate delegate = new MockClassLoaderDelegate();
        InterceptClassLoader loader = new InterceptClassLoader(
            parent,
            Pattern.compile(".*"),
            Arrays.asList(delegate));
        delegate.define("com.example.Hello");

        Class<?> klass = loader.loadClass("com.example.Parent");
        assertThat(
            "親クラスローダのクラスも強制的にロードする",
            klass.getClassLoader(),
            not(sameInstance((Object) parent)));

        Class<?> retry = loader.loadClass("com.example.Parent");
        assertThat(
            "複数回ロードしても同じ結果",
            klass,
            sameInstance((Object) retry));

        Class<?> hello = loader.loadClass("com.example.Hello");
        assertThat(
            "子クラスローダを優先する",
            hello.getClassLoader(),
            sameInstance((Object) loader));
    }

    /**
     * Test method for {@link InterceptClassLoader#loadClass(String, boolean)}.
     */
    @Test
    public void loadClass_委譲の優先順位() {
        MockClassLoaderDelegate delegate1 = new MockClassLoaderDelegate();
        MockClassLoaderDelegate delegate2 = new MockClassLoaderDelegate();
        InterceptClassLoader loader = new InterceptClassLoader(
            getClass().getClassLoader(),
            Pattern.compile("com/example/.*"),
            Arrays.asList(delegate1, delegate2));

        delegate1.define("com.example.A", "1");
        delegate2.define("com.example.A", "2");
        delegate2.define("com.example.B", "2");

        assertThat(
            "先頭の委譲を優先",
            Util.toString(loader, "com.example.A"),
            is("1"));

        assertThat(
            "先頭になければ次を探す",
            Util.toString(loader, "com.example.B"),
            is("2"));
    }

    /**
     * Test method for {@link InterceptClassLoader#getResource(String)}.
     * @throws Exception if occr
     */
    @Test
    public void getResource_フィルタがかかっていないリソース() throws Exception {
        MockClassLoaderDelegate delegate = new MockClassLoaderDelegate();
        InterceptClassLoader loader = new InterceptClassLoader(
            getClass().getClassLoader(),
            Pattern.compile("com/example/.*"),
            Arrays.asList(delegate));

        delegate.add("com/example/a.txt", "http://example.com/a.txt");

        assertThat(
            "存在しているリソースの位置を返す",
            loader.getResource("com/example/a.txt"),
            is(new URL("http://example.com/a.txt")));

        assertThat(
            "存在しないリソースはnull",
            loader.getResource("com/example/missing.txt"),
            is(nullValue()));
    }

    /**
     * Test method for {@link InterceptClassLoader#getResource(String)}.
     * @throws Exception if occr
     */
    @Test
    public void getResource_フィルタがかかっているリソース() throws Exception {
        MockClassLoaderDelegate delegate = new MockClassLoaderDelegate();
        InterceptClassLoader loader = new InterceptClassLoader(
            getClass().getClassLoader(),
            Pattern.compile("com/example/pub/.*"),
            Arrays.asList(delegate));

        delegate.add("com/example/a.txt", "http://example.com/a.txt");

        assertThat(
            "フィルタがかかっているリソースは取り出せない",
            loader.getResource("com/example/a.txt"),
            is(nullValue()));
    }

    /**
     * Test method for {@link InterceptClassLoader#getResource(String)}.
     * @throws Exception if occr
     */
    @Test
    public void getResource_子クラスローダのリソースを優先() throws Exception {
        MockClassLoaderDelegate dParent = new MockClassLoaderDelegate();
        InterceptClassLoader parent = new InterceptClassLoader(
            getClass().getClassLoader(),
            Pattern.compile("com/example/.*"),
            Arrays.asList(dParent));
        dParent.add("com/example/parent.txt", "http://example.com/super.txt");
        dParent.add("com/example/hello.txt", "http://example.com/hello.txt");

        MockClassLoaderDelegate delegate = new MockClassLoaderDelegate();
        InterceptClassLoader loader = new InterceptClassLoader(
            parent,
            Pattern.compile(".*"),
            Arrays.asList(delegate));
        delegate.add("com/example/parent.txt", "http://example.com/sub.txt");

        assertThat(
            "子クラスローダのリソースを優先",
            loader.getResource("com/example/parent.txt"),
            is(new URL("http://example.com/sub.txt")));

        assertThat(
            "子になければ親から探す",
            loader.getResource("com/example/hello.txt"),
            is(new URL("http://example.com/hello.txt")));
    }

    /**
     * Test method for {@link InterceptClassLoader#getResource(String)}.
     * @throws Exception if occur
     */
    @Test
    public void getResource_委譲の優先順位() throws Exception {
        MockClassLoaderDelegate delegate1 = new MockClassLoaderDelegate();
        MockClassLoaderDelegate delegate2 = new MockClassLoaderDelegate();
        InterceptClassLoader loader = new InterceptClassLoader(
            getClass().getClassLoader(),
            Pattern.compile("com/example/.*"),
            Arrays.asList(delegate1, delegate2));

        delegate1.add("com/example/a.txt", "http://example.com/1");
        delegate2.add("com/example/a.txt", "http://example.com/2");
        delegate2.add("com/example/b.txt", "http://example.com/2");

        assertThat(
            "先頭の委譲を優先",
            loader.getResource("com/example/a.txt"),
            is(new URL("http://example.com/1")));

        assertThat(
            "先頭になければ次を探す",
            loader.getResource("com/example/b.txt"),
            is(new URL("http://example.com/2")));
    }

    /**
     * Test method for {@link InterceptClassLoader#getResources(String)}.
     * @throws Exception if occur
     */
    @Test
    public void getResources_フィルタがかかっていないリソース() throws Exception {
        MockClassLoaderDelegate delegate = new MockClassLoaderDelegate();
        InterceptClassLoader loader = new InterceptClassLoader(
            getClass().getClassLoader(),
            Pattern.compile("com/example/.*"),
            Arrays.asList(delegate));

        delegate.add("com/example/a.txt", "http://example.com/a.txt");
        delegate.add("com/example/b.txt", "http://example.com/b.txt");
        delegate.add("com/example/b.txt", "http://example.com/c.txt");

        List<URL> a = list(loader.getResources("com/example/a.txt"));
        assertThat(
            "存在しているリソースの位置を返す",
            a,
            is(urlList("http://example.com/a.txt")));

        List<URL> b = list(loader.getResources("com/example/b.txt"));
        assertThat(
            "複数のリソースがある場合にはそれらが返される",
            b.size(),
            is(2));
        assertThat(
            "複数のリソースがある場合にはそれらが返される",
            range(b, 0, 2),
            is(urlSet(
                "http://example.com/b.txt",
                "http://example.com/c.txt")));

        List<URL> c = list(loader.getResources("com/example/c.txt"));
        assertThat(
            "存在していないリソースは見つからない",
            c.size(),
            is(0));
    }

    /**
     * Test method for {@link InterceptClassLoader#getResources(String)}.
     * @throws Exception if occur
     */
    @Test
    public void getResources_フィルタがかかっているリソース() throws Exception {
        MockClassLoaderDelegate delegate = new MockClassLoaderDelegate();
        InterceptClassLoader loader = new InterceptClassLoader(
            getClass().getClassLoader(),
            Pattern.compile("com/example/pub/.*"),
            Arrays.asList(delegate));

        delegate.add("com/example/a.txt", "http://example.com/a.txt");

        assertThat(
            "フィルタがかかっているリソースは取り出せない",
            loader.getResources("com/example/a.txt").hasMoreElements(),
            is(false));
    }

    /**
     * Test method for {@link InterceptClassLoader#getResources(String)}.
     * @throws Exception if occr
     */
    @Test
    public void getResources_子クラスローダのリソースを優先() throws Exception {
        MockClassLoaderDelegate dParent = new MockClassLoaderDelegate();
        InterceptClassLoader parent = new InterceptClassLoader(
            getClass().getClassLoader(),
            Pattern.compile("com/example/.*"),
            Arrays.asList(dParent));
        dParent.add("com/example/parent.txt", "http://example.com/super.txt");
        dParent.add("com/example/hello.txt", "http://example.com/hello.txt");

        MockClassLoaderDelegate delegate = new MockClassLoaderDelegate();
        InterceptClassLoader loader = new InterceptClassLoader(
            parent,
            Pattern.compile(".*"),
            Arrays.asList(delegate));
        delegate.add("com/example/parent.txt", "http://example.com/sub.txt");

        assertThat(
            "子クラスローダのリソースを優先",
            list(loader.getResources("com/example/parent.txt")),
            is(urlList(
                "http://example.com/sub.txt",
                "http://example.com/super.txt")));

        assertThat(
            "子になければ親から探す",
            list(loader.getResources("com/example/hello.txt")),
            is(urlList("http://example.com/hello.txt")));
    }

    /**
     * Test method for {@link InterceptClassLoader#getResources(String)}.
     * @throws Exception if occur
     */
    @Test
    public void getResources_委譲の優先順位() throws Exception {
        MockClassLoaderDelegate delegate1 = new MockClassLoaderDelegate();
        MockClassLoaderDelegate delegate2 = new MockClassLoaderDelegate();
        InterceptClassLoader loader = new InterceptClassLoader(
            getClass().getClassLoader(),
            Pattern.compile("com/example/.*"),
            Arrays.asList(delegate1, delegate2));

        delegate1.add("com/example/a.txt", "http://example.com/1");
        delegate2.add("com/example/a.txt", "http://example.com/2");
        delegate2.add("com/example/b.txt", "http://example.com/2");

        assertThat(
            "先頭の委譲を優先",
            list(loader.getResources("com/example/a.txt")),
            is(urlList(
                "http://example.com/1",
                "http://example.com/2")));

        assertThat(
            "先頭になければ次を探す",
            list(loader.getResources("com/example/b.txt")),
            is(urlList("http://example.com/2")));
    }

    /**
     * Test method for {@link InterceptClassLoader#getResourceAsStream(String)}.
     */
    @Test
    public void getResourceAsStream_フィルタがかかっていないリソース() {
        MockClassLoaderDelegate delegate = new MockClassLoaderDelegate();
        InterceptClassLoader loader = new InterceptClassLoader(
            getClass().getClassLoader(),
            Pattern.compile("com/example/.*"),
            Arrays.asList(delegate));

        delegate.add("com/example/a.txt", bytes("hello"));

        assertThat(
            "存在しているリソースはロードできる",
            Util.toString(loader.getResourceAsStream("com/example/a.txt")),
            is("hello"));

        assertThat(
            "存在しないリソースはロードできない",
            Util.toString(loader.getResourceAsStream("com/example/missing.txt")),
            is(nullValue()));
    }

    /**
     * Test method for {@link InterceptClassLoader#getResourceAsStream(String)}.
     */
    @Test
    public void getResourceAsStream_フィルタがかかっているリソース() {
        MockClassLoaderDelegate delegate = new MockClassLoaderDelegate();
        InterceptClassLoader loader = new InterceptClassLoader(
            getClass().getClassLoader(),
            Pattern.compile("com/example/pub/.*"),
            Arrays.asList(delegate));

        delegate.add("com/example/a.txt", bytes("hello"));

        assertThat(
            "フィルタがかかっているリソースはロードできない",
            Util.toString(loader.getResourceAsStream("com/example/a.txt")),
            is(nullValue()));
    }

    /**
     * Test method for {@link InterceptClassLoader#getResourceAsStream(String)}.
     */
    @Test
    public void getResourceAsStream_子クラスローダのリソースを優先() {
        MockClassLoaderDelegate dParent = new MockClassLoaderDelegate();
        InterceptClassLoader parent = new InterceptClassLoader(
            getClass().getClassLoader(),
            Pattern.compile("com/example/.*"),
            Arrays.asList(dParent));
        dParent.add("com/example/parent.txt", bytes("parent"));
        dParent.add("com/example/hello.txt", bytes("hello"));

        MockClassLoaderDelegate delegate = new MockClassLoaderDelegate();
        InterceptClassLoader loader = new InterceptClassLoader(
            parent,
            Pattern.compile(".*"),
            Arrays.asList(delegate));
        delegate.add("com/example/parent.txt", bytes("child"));

        assertThat(
            "子クラスローダのリソースを優先",
            Util.toString(loader.getResourceAsStream("com/example/parent.txt")),
            is("child"));

        assertThat(
            "子になければ親から探す",
            Util.toString(loader.getResourceAsStream("com/example/hello.txt")),
            is("hello"));
    }

    /**
     * Test method for {@link InterceptClassLoader#getResourceAsStream(String)}.
     * @throws Exception if occur
     */
    @Test
    public void getResourceAsStream_委譲の優先順位() throws Exception {
        MockClassLoaderDelegate delegate1 = new MockClassLoaderDelegate();
        MockClassLoaderDelegate delegate2 = new MockClassLoaderDelegate();
        InterceptClassLoader loader = new InterceptClassLoader(
            getClass().getClassLoader(),
            Pattern.compile("com/example/.*"),
            Arrays.asList(delegate1, delegate2));

        delegate1.add("com/example/a.txt", bytes("1"));
        delegate2.add("com/example/a.txt", bytes("2"));
        delegate2.add("com/example/b.txt", bytes("2"));

        assertThat(
            "先頭の委譲を優先",
            Util.toString(loader.getResourceAsStream("com/example/a.txt")),
            is("1"));

        assertThat(
            "先頭になければ次を探す",
            Util.toString(loader.getResourceAsStream("com/example/b.txt")),
            is("2"));
    }

    private static byte[] bytes(String string) {
        return string.getBytes();
    }

    private static <T> List<T> list(Enumeration<T> target) {
        List<T> results = new ArrayList<T>();
        while (target.hasMoreElements()) {
            results.add(target.nextElement());
        }
        return results;
    }

    private static List<URL> urlList(String...urls) {
        List<URL> results = new ArrayList<URL>();
        for (String url : urls) {
            try {
                results.add(new URL(url));
            }
            catch (MalformedURLException e) {
                throw new AssertionError(e);
            }
        }
        return results;
    }

    private static Set<URL> urlSet(String...urls) {
        return new HashSet<URL>(urlList(urls));
    }

    private static <T> Set<T> range(Iterable<T> target, int start, int end) {
        Iterator<T> iter = target.iterator();
        for (int i = 0; i < start; i++) {
            assertThat(iter.hasNext(), is(true));
            iter.next();
        }
        Set<T> results = new HashSet<T>();
        for (int i = start; i < end; i++) {
            assertThat(iter.hasNext(), is(true));
            results.add(iter.next());
        }
        return results;
    }
}
