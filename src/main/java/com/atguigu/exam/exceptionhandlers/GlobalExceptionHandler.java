package com.atguigu.exam.exceptionhandlers;


import com.atguigu.exam.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    //定义日常处理的handler
    @ExceptionHandler(Exception.class)
    public Result exceptionHabdler(Exception e){
        e.printStackTrace();//错误的堆栈信息
        log.error("代码出现异常,{}",e.getMessage());
        return Result.error(e.getMessage());
    }
}
