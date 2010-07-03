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

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Scanner;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.Modifier;

/**
 * このパッケージのテストユーティリティ。
 */
class Util {

    /**
     * 指定の名前で、{@link MockMyName}を継承しただけのクラスを生成して返す。
     * @param className 生成するクラスの名称
     * @return 生成したクラスを表すバイナリ
     */
    public static byte[] createClass(String className) {
        try {
            ClassPool pool = new ClassPool(true);
            CtClass parent = pool.get(MockMyName.class.getName());
            CtClass created = pool.makeClass(className, parent);
            created.setModifiers(Modifier.PUBLIC);
            return created.toBytecode();
        }
        catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    /**
     * 指定の名前で、{@code toString()}メソッドが指定の文字列を返すようなクラスを生成して返す。
     * @param className 生成するクラスの名称
     * @param string {@code toString()}メソッドが返す文字列
     *     (文字列リテラルと同様の方式でエスケープされている必要がある)
     * @return 生成したクラスを表すバイナリ
     */
    public static byte[] createClass(String className, String string) {
        try {
            ClassPool pool = new ClassPool(true);
            CtClass created = pool.makeClass(className);
            created.setModifiers(Modifier.PUBLIC);
            CtMethod toString = new CtMethod(
                pool.get("java.lang.String"),
                "toString",
                new CtClass[0],
                created);
            toString.setBody(MessageFormat.format(
                "return \"{0}\";",
                string));
            created.addMethod(toString);
            return created.toBytecode();
        }
        catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    /**
     * 指定のクラスをロードし、インスタンス化したものに{@code toString()}を実行した結果を返す。
     * @param loader 対象のローダ
     * @param binaryName 対象のクラスバイナリ名
     * @return 実行結果の文字列
     */
    public static String toString(ClassLoader loader, String binaryName) {
        try {
            Class<?> loaded = loader.loadClass(binaryName);
            return loaded.newInstance().toString();
        }
        catch (ClassNotFoundException e) {
            return null;
        }
        catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    /**
     * 指定のストリームの内容を、現在のエンコーディングで文字列に変換する。
     * @param input 対象のストリーム
     * @return 変換後の文字列
     */
    public static String toString(InputStream input) {
        if (input == null) {
            return null;
        }
        try {
            try {
                Scanner s = new Scanner(input);
                assertThat(s.hasNextLine(), is(true));
                String result = s.nextLine();
                assertThat(s.hasNextLine(), is(false));
                s.close();
                return result;
            }
            finally {
                input.close();
            }
        }
        catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
