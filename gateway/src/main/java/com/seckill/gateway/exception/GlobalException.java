package com.seckill.gateway.exception;

import com.seckill.common.result.CodeMsg;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
public class GlobalException extends RuntimeException
{
    private CodeMsg codeMsg;
}
