package com.example.seatstats.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResponseDTO<T> {

    private Integer code;

    private String message;

    private T data;

    public static <T> ResponseDTO<T> success(T data) {
        return new ResponseDTO<>(200, "success", data);
    }

    public static <T> ResponseDTO<T> success(String message, T data) {
        return new ResponseDTO<>(200, message, data);
    }

    public static <T> ResponseDTO<T> error(Integer code, String message) {
        return new ResponseDTO<>(code, message, null);
    }

    public static <T> ResponseDTO<T> error(String message) {
        return new ResponseDTO<>(500, message, null);
    }
}