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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.appengine.api.datastore.Blob;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Transaction;

/**
 * データストアを利用して小さなデータをパス上に配置する。
 */
public class ResourceStore {

    private static final String PROPERTY_VERSION = "v"; //$NON-NLS-1$

    private static final String PROPERTY_CONTENTS = "c"; //$NON-NLS-1$

    private static final Long CURRENT_VERSION = Long.valueOf(1L);

    private DatastoreService service;

    private String kindName;

    /**
     * インスタンスを生成する。
     * @param service 保存に利用するデータストアサービス
     * @param kindName リソースを保存するカインド名
     * @throws IllegalArgumentException 引数に{@code null}が含まれる場合
     */
    public ResourceStore(
            DatastoreService service,
            String kindName) {
        if (service == null) {
            throw new IllegalArgumentException("service must not be null"); //$NON-NLS-1$
        }
        if (kindName == null) {
            throw new IllegalArgumentException("kindName must not be null"); //$NON-NLS-1$
        }
        this.service = service;
        this.kindName = kindName;
    }

    /**
     * リソースを保存するカインド名を返す。
     * @return リソースを保存するカインド名
     */
    public String getKindName() {
        return this.kindName;
    }

    /**
     * 指定のパスのファイルを保存するためのキーを作成して返す。
     * @param path 対象のパス
     * @return 対応するキー
     * @throws IllegalArgumentException 引数に{@code null}が含まれる場合
     */
    Key createKey(String path) {
        assert path != null;
        String name = mangle(path);
        return KeyFactory.createKey(getKindName(), name);
    }

    private String mangle(String path) {
        assert path != null;
        return '/' + path;
    }

    /**
     * 指定のパスに対応するファイルの内容をデータストアから読み出して返す。
     * @param path 対象のパス
     * @return 対応するファイルの内容、存在しない場合は{@code null}
     * @throws IllegalArgumentException 引数に{@code null}が含まれる場合
     */
    public byte[] get(String path) {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null"); //$NON-NLS-1$
        }
        Key key = createKey(path);
        Entity entity;
        try {
            entity = service.get(null, key);
        }
        catch (EntityNotFoundException e) {
            return null;
        }
        return fromEntity(entity);
    }

    /**
     * 指定のパスに対応するファイルの内容を、データストアに書き出す。
     * <p>
     * 指定のパスに対応するファイルが既にデータストア上に存在する場合、新しい内容で上書きする。
     * </p>
     * @param path 対象のパス
     * @param contents 対象のファイルの内容
     * @throws IllegalArgumentException 引数に{@code null}が含まれる場合
     */
    public void put(String path, byte[] contents) {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null"); //$NON-NLS-1$
        }
        if (contents == null) {
            throw new IllegalArgumentException("contents must not be null"); //$NON-NLS-1$
        }
        Entity entity = toEntity(path, contents);
        service.put(null, entity);
    }

    /**
     * 指定のパスに対応するデータストア上のデータを削除する。
     * <p>
     * データストア上にそのようなデータが存在しない場合、この呼び出しはなにも行わない。
     * </p>
     * @param path 対象のパス
     * @throws IllegalArgumentException 引数に{@code null}が含まれる場合
     */
    public void delete(String path) {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null"); //$NON-NLS-1$
        }
        Key key = createKey(path);
        service.delete((Transaction) null, key);
    }

    /**
     * 指定のパスに対応するファイルの内容をデータストアから読み出して返す。
     * <p>
     * この操作によって一度に取得できるファイルの数や、合計のサイズはデータストアの制限に因る。
     * </p>
     * @param paths 対象のパス一覧
     * @return 対応するファイルの内容、存在しない場合は{@code null}
     * @throws IllegalArgumentException 引数に{@code null}が含まれる場合
     */
    public Map<String, byte[]> get(Iterable<String> paths) {
        if (paths == null) {
            throw new IllegalArgumentException("paths must not be null"); //$NON-NLS-1$
        }
        Map<Key, String> keys = new HashMap<Key, String>();
        for (String path : paths) {
            Key key = createKey(path);
            keys.put(key, path);
        }

        Map<Key, Entity> entities = service.get(null, keys.keySet());
        Map<String, byte[]> contents = new HashMap<String, byte[]>();
        for (Map.Entry<Key, Entity> entry : entities.entrySet()) {
            Entity entity = entry.getValue();
            if (entity != null) {
                byte[] bytes = fromEntity(entity);
                if (bytes != null) {
                    contents.put(keys.get(entry.getKey()), bytes);
                }
            }
        }
        return contents;
    }

    /**
     * 指定のパスに対応するファイルの内容を、データストアに書き出す。
     * <p>
     * 指定のパスに対応するファイルが既にデータストア上に存在する場合、新しい内容で上書きする。
     * </p>
     * <p>
     * この操作によって一度に追加できるファイルの数や、合計のサイズはデータストアの制限に因る。
     * </p>
     * @param pathAndContents 対象のパスと内容の一覧
     * @throws IllegalArgumentException 引数に{@code null}が含まれる場合
     */
    public void put(Map<String, byte[]> pathAndContents) {
        if (pathAndContents == null) {
            throw new IllegalArgumentException("pathAndContents must not be null"); //$NON-NLS-1$
        }
        List<Entity> entities = new ArrayList<Entity>();
        for (Map.Entry<String, byte[]> entry : pathAndContents.entrySet()) {
            Entity entity = toEntity(entry.getKey(), entry.getValue());
            entities.add(entity);
        }
        service.put(entities);
    }

    /**
     * 指定のパスに対応するデータストア上のデータを削除する。
     * <p>
     * データストア上にそのようなデータが存在しない場合、この呼び出しはなにも行わない。
     * </p>
     * <p>
     * この操作によって一度に削除できるファイルの数はデータストアの制限に因る。
     * </p>
     * @param paths 対象のパス一覧
     * @throws IllegalArgumentException 引数に{@code null}が含まれる場合
     */
    public void delete(Iterable<String> paths) {
        if (paths == null) {
            throw new IllegalArgumentException("paths must not be null"); //$NON-NLS-1$
        }
        List<Key> keys = new ArrayList<Key>();
        for (String path : paths) {
            keys.add(createKey(path));
        }
        service.delete((Transaction) null, keys);
    }

    private byte[] fromEntity(Entity entity) {
        assert entity != null;
        if (entity.hasProperty(PROPERTY_CONTENTS) == false) {
            return null;
        }
        Blob contents = (Blob) entity.getProperty(PROPERTY_CONTENTS);
        return contents.getBytes();
    }

    private Entity toEntity(String path, byte[] contents) {
        assert path != null;
        assert contents != null;
        Key key = createKey(path);
        Entity entity = new Entity(key);
        entity.setUnindexedProperty(PROPERTY_CONTENTS, new Blob(contents));
        entity.setUnindexedProperty(PROPERTY_VERSION, CURRENT_VERSION);
        return entity;
    }
}
