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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

/**
 * 親クラスのクラスロードを阻害して、子クラスを優先してロードを行うクラスローダ。
 */
public class InterceptClassLoader extends ClassLoader {

    private static final String CLASS_EXTENSION = ".class"; //$NON-NLS-1$

    private static final Enumeration<URL> EMPTY = new CompoundEnumeration<URL>();

    private static final String[] EXCLUDES = {
        "java/", //$NON-NLS-1$
        "javax/", //$NON-NLS-1$
        "com/sun/", //$NON-NLS-1$
        "sun/", //$NON-NLS-1$
    };

    private ClassLoader parent;

    private Pattern includes;

    private ClassLoaderDelegate[] delegates;

    private Map<String, Class<?>> parentCache = new HashMap<String, Class<?>>();

    /**
     * インスタンスを生成する。
     * @param parent 親クラスローダ
     * @param includes このクラスローダーが対象とするパスの形式、
     *   すべてのパスは / で区切られた名前となる。
     *   クラスファイルについても同様で、限定名のパッケージを / で区切り、末尾に .class を付与したパスとなる
     * @param delegates 実際のクラスロードやリソースのロードを委譲される。
     *   リストの先頭ほど優先され、クラスやリソースを探しに行く際には先頭から順に探す
     * @throws IllegalArgumentException 引数に{@code null}が含まれる場合
     */
    public InterceptClassLoader(
            ClassLoader parent,
            Pattern includes,
            List<? extends ClassLoaderDelegate> delegates) {
        super(parent);
        if (parent == null) {
            throw new IllegalArgumentException("parent must not be null"); //$NON-NLS-1$
        }
        if (includes == null) {
            throw new IllegalArgumentException("includes must not be null"); //$NON-NLS-1$
        }
        if (delegates == null) {
            throw new IllegalArgumentException("delegates must not be null"); //$NON-NLS-1$
        }
        this.parent = parent;
        this.includes = includes;
        this.delegates = delegates.toArray(new ClassLoaderDelegate[delegates.size()]);
    }

    /**
     * 指定のパス上のデータをこのクラスローダで取り扱う場合のみ{@code true}を返す。
     * @param path 対象のパス
     * @return このクラスローダで取り扱う場合のみ{@code true}
     * @throws IllegalArgumentException 引数に{@code null}が含まれる場合
     */
    protected boolean accepts(String path) {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null"); //$NON-NLS-1$
        }
        for (String prefix : EXCLUDES) {
            if (path.startsWith(prefix)) {
                return false;
            }
        }
        return includes.matcher(path).matches();
    }

    @Override
    public synchronized Class<?> loadClass(String binaryName, boolean resolve)
            throws ClassNotFoundException {
        Class<?> loaded = findLoadedClass(binaryName);
        if (loaded != null) {
            return loaded;
        }
        Class<?> loadedInParent = parentCache.get(binaryName);
        if (loadedInParent != null) {
            return loadedInParent;
        }

        try {
            Class<?> found = findClass(binaryName);
            if (resolve) {
                resolveClass(found);
            }
            return found;
        }
        catch (ClassNotFoundException ignore) {
            // continue...
        }
        Class<?> fromParent = super.loadClass(binaryName, resolve);
        parentCache.put(binaryName, fromParent);
        return fromParent;
    }

    @Override
    public URL getResource(String path) {
        URL resource = findResource(path);
        if (resource != null) {
            return resource;
        }
        return parent.getResource(path);
    }

    @Override
    public Enumeration<URL> getResources(String path) throws IOException {
        Enumeration<URL> resources = findResources(path);
        Enumeration<URL> parentResources = parent.getResources(path);
        if (resources.hasMoreElements()) {
            return new CompoundEnumeration<URL>(resources, parentResources);
        }
        return parentResources;
    }

    @Override
    public InputStream getResourceAsStream(String path) {
        InputStream stream = findResourceAsStream(path);
        if (stream != null) {
            return stream;
        }
        return parent.getResourceAsStream(path);
    }

    @Override
    protected Class<?> findClass(String binaryName) throws ClassNotFoundException {
        String path = toClassFilePath(binaryName);
        if (accepts(path) == false) {
            throw new ClassNotFoundException(binaryName);
        }

        byte[] fromDelegate = findClassBytesFromDelegate(binaryName);
        if (fromDelegate != null) {
            return defineClass(binaryName, fromDelegate, 0, fromDelegate.length, null);
        }

        byte[] fromParent = findClassBytesFromParent(binaryName);
        if (fromParent != null) {
            return defineClass(binaryName, fromParent, 0, fromParent.length, null);
        }

        throw new ClassNotFoundException(binaryName);
    }

    private byte[] findClassBytesFromDelegate(String binaryName) {
        assert binaryName != null;
        for (ClassLoaderDelegate delegate : delegates) {
            byte[] bytes = delegate.findClass(binaryName);
            if (bytes != null) {
                return bytes;
            }
        }
        return null;
    }

    private byte[] findClassBytesFromParent(String binaryName) {
        assert binaryName != null;
        String path = toClassFilePath(binaryName);
        InputStream in = parent.getResourceAsStream(path);
        if (in == null) {
            return null;
        }
        try {
            ByteArrayOutputStream results = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            while (true) {
                int read = in.read(buf);
                if (read == -1) {
                    break;
                }
                results.write(buf, 0, read);
            }
            return results.toByteArray();
        }
        catch (IOException e) {
            return null;
        }
        finally {
            try {
                in.close();
            }
            catch (IOException e) {
                // ignored
            }
        }
    }

    @Override
    protected URL findResource(String path) {
        if (accepts(path) == false) {
            return null;
        }
        for (ClassLoaderDelegate delegate : delegates) {
            URL found = delegate.findResource(path);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Override
    protected Enumeration<URL> findResources(String path) throws IOException {
        if (accepts(path) == false) {
            return EMPTY;
        }
        List<URL> results = new LinkedList<URL>();
        for (ClassLoaderDelegate delegate : delegates) {
            Iterable<URL> found = delegate.findAllResources(path);
            if (found != null) {
                for (URL url : found) {
                    results.add(url);
                }
            }
        }
        return new IteratorEnumeration<URL>(results.iterator());
    }

    private InputStream findResourceAsStream(String path) {
        if (accepts(path) == false) {
            return null;
        }
        for (ClassLoaderDelegate delegate : delegates) {
            InputStream found = delegate.findResourceAsStream(path);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * クラスのバイナリ名に対応する標準的なファイルパスを返す。
     * @param binaryName クラスのバイナリ名
     * @return 対応するファイルパス
     * @throws IllegalArgumentException 引数に{@code null}が含まれる場合
     */
    public static String toClassFilePath(String binaryName) {
        if (binaryName == null) {
            throw new IllegalArgumentException("binaryName must not be null"); //$NON-NLS-1$
        }
        return binaryName.replace('.', '/') + CLASS_EXTENSION;
    }

    /**
     * {@link Iterator}をラップした{@link Enumeration}。
     * @param <E> 列挙する要素の型
     */
    static class IteratorEnumeration<E> implements Enumeration<E> {

        private Iterator<? extends E> iterator;

        /**
         * インスタンスを生成する。
         * @param iterator ラップする反復子
         * @throws IllegalArgumentException 引数に{@code null}が含まれる場合
         */
        public IteratorEnumeration(Iterator<? extends E> iterator) {
            if (iterator == null) {
                throw new IllegalArgumentException("iterator must not be null"); //$NON-NLS-1$
            }
            this.iterator = iterator;
        }

        @Override
        public boolean hasMoreElements() {
            return iterator.hasNext();
        }

        @Override
        public E nextElement() {
            return iterator.next();
        }
    }

    /**
     * 複数の{@link Enumeration}を連接した{@code Enumeration}。
     * @param <E> 列挙する要素の型
     */
    static class CompoundEnumeration<E> implements Enumeration<E> {

        private LinkedList<Enumeration<? extends E>> enumerations;

        /**
         * 要素が存在しないインスタンスを生成する。
         * @throws IllegalArgumentException 引数に{@code null}が含まれる場合
         */
        public CompoundEnumeration() {
            enumerations = new LinkedList<Enumeration<? extends E>>();
        }

        /**
         * 2つの列挙を連接したインスタンスを生成する。
         * @param first 最初に探索する列挙
         * @param rest {@code first}の探索が完了後に探索する列挙
         * @throws IllegalArgumentException 引数に{@code null}が含まれる場合
         */
        public CompoundEnumeration(
                Enumeration<? extends E> first,
                Enumeration<? extends E> rest) {
            if (first == null) {
                throw new IllegalArgumentException("first must not be null"); //$NON-NLS-1$
            }
            if (rest == null) {
                throw new IllegalArgumentException("rest must not be null"); //$NON-NLS-1$
            }
            enumerations = new LinkedList<Enumeration<? extends E>>();
            enumerations.add(first);
            enumerations.add(rest);
        }

        @Override
        public boolean hasMoreElements() {
            while (enumerations.isEmpty() == false) {
                if (enumerations.getFirst().hasMoreElements()) {
                    return true;
                }
                enumerations.removeFirst();
            }
            return false;
        }

        @Override
        public E nextElement() {
            while (enumerations.isEmpty() == false) {
                Enumeration<? extends E> first = enumerations.getFirst();
                if (first.hasMoreElements()) {
                    return first.nextElement();
                }
                enumerations.removeFirst();
            }
            throw new NoSuchElementException();
        }
    }
}
