/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.gtask.exception;

/**
 * Google Task 云同步网络通信失败异常类（受检异常，继承自 Exception）。
 * 当 HTTP GET/POST 请求超时、连接中断、登录请求失败或响应流读取发生 I/O 错误时抛出，
 * 由 GTaskManager 捕获并转换为 STATE_NETWORK_ERROR 网络错误状态提示用户。
 */
public class NetworkFailureException extends Exception {
    // 序列化版本唯一标识 UID
    private static final long serialVersionUID = 2107610287180234136L;

    /**
     * 无参构造方法，创建默认的网络通信失败异常。
     */
    public NetworkFailureException() {
        super();
    }

    /**
     * 带错误描述信息的构造方法。
     *
     * @param paramString 网络异常描述信息
     */
    public NetworkFailureException(String paramString) {
        super(paramString);
    }

    /**
     * 带错误描述信息及底层 I/O 或协议根因异常的构造方法。
     *
     * @param paramString    网络异常描述信息
     * @param paramThrowable 底层原始异常对象
     */
    public NetworkFailureException(String paramString, Throwable paramThrowable) {
        super(paramString, paramThrowable);
    }
}
