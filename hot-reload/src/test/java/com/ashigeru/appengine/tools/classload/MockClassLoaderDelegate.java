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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * Mock implementation of {@link ClassLoaderDelegate};
 */
public class MockClassLoaderDelegate extends ClassLoaderDelegate {

    private Map<String, LinkedList<byte[]>> contents =
        new HashMap<String, LinkedList<byte[]>>();

    private Map<String, LinkedList<URL>> locations =
        new HashMap<String, LinkedList<URL>>();

    /**
     * 指定の名前のクラスを追加する。
     * <p>
     * クラスは{@link Util#createClass(String)}を利用して作成する。
     * </p>
     * @param className クラス名
     */
    void define(String className) {
        byte[] content = Util.createClass(className);
        add(className.replace('.', '/') + ".class", content);
    }

    /**
     * 指定の名前で、指定の文字列を返すクラスを追加する。
     * <p>
     * クラスは{@link Util#createClass(String, String)}を利用して作成する。
     * </p>
     * @param className クラス名
     * @param string 返す文字列
     */
    void define(String className, String string) {
        byte[] content = Util.createClass(className, string);
        add(className.replace('.', '/') + ".class", content);
    }

    /**
     * ロード可能なデータを追加する。
     * @param path 配置するパス
     * @param content 内容
     */
    void add(String path, byte[] content) {
        LinkedList<byte[]> list = contents.get(path);
        if (list == null) {
            list = new LinkedList<byte[]>();
            contents.put(path, list);
        }
        list.addLast(content);
    }

    /**
     * 検出可能な位置を追加する。
     * @param path 配置するパス
     * @param url 対応する位置
     */
    void add(String path, String url) {
        LinkedList<URL> list = locations.get(path);
        if (list == null) {
            list = new LinkedList<URL>();
            locations.put(path, list);
        }
        try {
            list.addLast(new URL(url));
        }
        catch (MalformedURLException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public InputStream findResourceAsStream(String path) {
        LinkedList<byte[]> list = contents.get(path);
        if (list == null) {
            return null;
        }
        return new ByteArrayInputStream(list.getFirst());
    }

    @Override
    public Iterable<URL> findAllResources(String path) {
        LinkedList<URL> list = locations.get(path);
        if (list == null) {
            return Collections.emptyList();
        }
        return list;
    }
}
