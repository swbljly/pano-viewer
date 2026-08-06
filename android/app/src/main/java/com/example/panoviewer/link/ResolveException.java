package com.example.panoviewer.link;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 链接解析失败异常（受检语义的运行时异常）。
 *
 * <p>由各解析器在 best-effort 流程（如夸克接口变更）失败时抛出，由上层捕获并转为
 * 对应 Toast 提示。</p>
 */
public class ResolveException extends RuntimeException {

    public ResolveException(@NonNull String message) {
        super(message);
    }

    public ResolveException(@NonNull String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
