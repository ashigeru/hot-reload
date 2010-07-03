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

/**
 * データストアからクラスパス上の情報を取得する。
 */
public class DatastoreClassLoaderDelegate extends ClassLoaderDelegate {

    private ResourceStore datastore;

    /**
     * インスタンスを生成する。
     * @param datastore リソースファイルを参照する
     * @throws IllegalArgumentException 引数に{@code null}が含まれる場合
     */
    public DatastoreClassLoaderDelegate(ResourceStore datastore) {
        if (datastore == null) {
            throw new IllegalArgumentException("datastore must not be null"); //$NON-NLS-1$
        }
        this.datastore = datastore;
    }

    @Override
    public byte[] findClass(String binaryName) {
        if (binaryName == null) {
            throw new IllegalArgumentException("name must not be null"); //$NON-NLS-1$
        }
        String path = InterceptClassLoader.toClassFilePath(binaryName);
        return datastore.get(path);
    }

    @Override
    public InputStream findResourceAsStream(String path) {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null"); //$NON-NLS-1$
        }
        byte[] bytes = datastore.get(path);
        if (bytes != null) {
            return new ByteArrayInputStream(bytes);
        }
        return null;
    }
}
