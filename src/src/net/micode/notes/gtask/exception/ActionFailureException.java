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
 * Google Task 同步动作执行失败异常类（非受检运行时异常，继承自 RuntimeException）。
 * 当构造/解析同步 JSON 报文失败、账号认证失败或本地同步数据库提交出错时抛出，
 * 由 GTaskManager 统一捕获并转换为 STATE_INTERNAL_ERROR 内部错误状态。
 */
public class ActionFailureException extends RuntimeException {
    // 序列化版本唯一标识 UID
    private static final long serialVersionUID = 4425249765923293627L;

    /**
     * 无参构造方法，创建默认的同步动作失败异常。
     */
    public ActionFailureException() {
        super();
    }

    /**
     * 带错误描述信息的构造方法。
     *
     * @param paramString 异常原因描述字符串
     */
    public ActionFailureException(String paramString) {
        super(paramString);
    }

    /**
     * 带错误描述信息及底层根因异常的构造方法。
     *
     * @param paramString    异常原因描述字符串
     * @param paramThrowable 引发本次失败的原始异常对象
     */
    public ActionFailureException(String paramString, Throwable paramThrowable) {
        super(paramString, paramThrowable);
    }
}
