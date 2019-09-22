package com.seckill.gateway.exception;

import com.seckill.common.result.CodeMsg;
import com.seckill.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@ControllerAdvice
@ResponseBody
@Slf4j
public class GlobalExceptionHandler
{
    /**
     * 处理异常，处理所有异常
     *
     * @param request
     * @param e
     * @return
     */
    @ExceptionHandler(value = Exception.class)
    public Result<String> exceptionHandler(HttpServletRequest request, Exception e)
    {
        log.info("出现异常");
        e.printStackTrace();// 打印原始的异常信息，方便调试

        // 如果所拦截的异常是自定义的全局异常，这按自定义异常的处理方式处理，否则按默认方式处理
        if (e instanceof GlobalException)
        {
            GlobalException exception = (GlobalException) e;
            // 向客户端返回异常信息
            return Result.error(exception.getCodeMsg());
        } else if (e instanceof BindException)
        {
            BindException bindException = (BindException) e;
            List<ObjectError> errors = bindException.getAllErrors();
            // 这里只获取了第一个错误对象
            ObjectError error = errors.get(0);
            // 获取其中的信息
            String message = error.getDefaultMessage();
            // 将错误信息动态地拼接到已定义的部分信息上
            return Result.error(CodeMsg.BIND_ERROR.fillArgs(message));
        } else
        {
            return Result.error(CodeMsg.SERVER_ERROR);
        }
    }
}
