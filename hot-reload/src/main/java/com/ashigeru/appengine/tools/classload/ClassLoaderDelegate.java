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
import java.util.Collections;
import java.util.Iterator;

/**
 * {@link InterceptClassLoader}から委譲されるクラスローダが実際にリソースを読み出す処理。
 * <p>
 * この実装では、次のような連鎖が行われているため、必要に応じてメソッドをオーバーライドすること。
 * </p>
 * <ol>
 * <li>
 *     {@link #findClass(String)} → バイナリ名に対応するパスを計算し、
 *     {@link #findResourceAsStream(String)}を利用してバイナリを取得する
 * </li>
 * <li>
 *     {@link #findResourceAsStream(String)} → 対応するリソースの位置を
 *     {@link #findResource(String)}で探し、ストリームを開く
 * </li>
 * <li>
 *     {@link #findResource(String)} → 関連するすべての位置を
 *     {@link #findAllResources(String)}で探し、先頭の要素を返す
 * </li>
 * <li>
 *     {@link #findAllResources(String)} → 空の要素を返す
 * </li>
 * </ol>
 */
public abstract class ClassLoaderDelegate {

    /**
     * {@link InterceptClassLoader#loadClass(String, boolean)}から呼び出され、
     * 対応するクラスのバイナリ表現を返す。
     * <p>
     * 委譲元の{@link InterceptClassLoader}が当該クラス名のクラスを対象としない場合、
     * このメソッドは呼び出されない。
     * </p>
     * <p>
     * このメソッドが{@code null}を返す(見つからない)場合、次の委譲先に問い合わせる。
     * すべての委譲先が{@code null}を返す場合、クラスの探索は失敗する。
     * </p>
     * @param binaryName ロードするクラスのバイナリ名
     * @return 対応するクラスのバイナリ、発見できない場合は{@code null}
     * @throws IllegalArgumentException 引数に{@code null}が含まれる場合
     */
    protected byte[] findClass(String binaryName) {
        InputStream stream = findResourceAsStream(InterceptClassLoader.toClassFilePath(binaryName));
        if (stream == null) {
            return null;
        }
        try {
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[1000];
            while (true) {
                int read = stream.read(buffer);
                if (read < 0) {
                    break;
                }
                result.write(buffer, 0, read);
            }
            return result.toByteArray();
        }
        catch (IOException e) {
            return null;
        }
        finally {
            try {
                stream.close();
            }
            catch (IOException e) {
                // ignored.
            }
        }
    }

    /**
     * {@link InterceptClassLoader#getResource(String)}から呼び出され、
     * 対応するクラスパス上のリソースへのURLを返す。
     * <p>
     * 委譲元の{@link InterceptClassLoader}が当該リソースを対象としない場合、
     * このメソッドは呼び出されない。
     * </p>
     * <p>
     * このメソッドが{@code null}を返す(見つからない)場合、次の委譲先に問い合わせる。
     * すべての委譲先が{@code null}を返す場合、リソースの探索は失敗する。
     * </p>
     * @param path 探索するリソースへのパス
     * @return 対応するリソースのURL、発見できない場合やURLで表現できない場合は{@code null}
     * @throws IllegalArgumentException 引数に{@code null}が含まれる場合
     */
    protected URL findResource(String path) {
        Iterator<URL> found = findAllResources(path).iterator();
        if (found.hasNext()) {
            return found.next();
        }
        return null;
    }

    /**
     * {@link InterceptClassLoader#getResources(String)}から呼び出され、
     * 対応するクラスパス上のリソースへのURL一覧を返す。
     * <p>
     * 委譲元の{@link InterceptClassLoader}が当該リソースを対象としない場合、
     * このメソッドは呼び出されない。
     * </p>
     * @param path 探索するリソースへのパス
     * @return 対応するリソースのURL一覧、発見できない場合やURLで表現できない場合は空
     * @throws IllegalArgumentException 引数に{@code null}が含まれる場合
     */
    protected Iterable<URL> findAllResources(String path) {
        return Collections.emptyList();
    }

    /**
     * {@link InterceptClassLoader#getResourceAsStream(String)}から呼び出され、
     * 対応するクラスパス上のリソースの内容を返す。
     * <p>
     * 委譲元の{@link InterceptClassLoader}が当該リソースを対象としない場合、
     * このメソッドは呼び出されない。
     * </p>
     * <p>
     * このメソッドが{@code null}を返す(見つからない)場合、次の委譲先に問い合わせる。
     * すべての委譲先が{@code null}を返す場合、リソースの探索は失敗する。
     * </p>
     * @param path 探索するリソースへのパス
     * @return 対応するリソースの内容を返すストリーム、
     *     発見できない場合やURLで表現できない場合は{@code null}
     * @throws IllegalArgumentException 引数に{@code null}が含まれる場合
     */
    protected InputStream findResourceAsStream(String path) {
        URL location = findResource(path);
        if (location == null) {
            return null;
        }
        try {
            return location.openStream();
        }
        catch (IOException e) {
            return null;
        }
    }
}