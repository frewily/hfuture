package top.hfuture.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    private Integer code;
    private String msg;
    private T data;

    public static <T> Result<T> success(T data) {
        return Result.<T>builder()
                .code(200)
                .msg("成功")
                .data(data)
                .build();
    }

    public static <T> Result<T> success(String msg, T data) {
        return Result.<T>builder()
                .code(200)
                .msg(msg)
                .data(data)
                .build();
    }

    public static <T> Result<T> error(String msg) {
        return Result.<T>builder()
                .code(500)
                .msg(msg)
                .build();
    }

    public static <T> Result<T> error(Integer code, String msg) {
        return Result.<T>builder()
                .code(code)
                .msg(msg)
                .build();
    }
}
