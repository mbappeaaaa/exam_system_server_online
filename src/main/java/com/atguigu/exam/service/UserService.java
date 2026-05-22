package com.atguigu.exam.service;

import com.atguigu.exam.entity.User;
import com.atguigu.exam.vo.LoginRequestVo;
import com.atguigu.exam.vo.LoginResponseVo;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 用户Service接口
 * 定义用户相关的业务方法
 */
public interface UserService extends IService<User>    {

    LoginResponseVo login(LoginRequestVo loginRequestVo);

    boolean checkAdmin(Long userId);

} 