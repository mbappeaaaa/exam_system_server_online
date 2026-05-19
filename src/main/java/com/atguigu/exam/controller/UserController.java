package com.atguigu.exam.controller;


import com.atguigu.exam.common.Result;
import com.atguigu.exam.service.UserService;
import com.atguigu.exam.vo.LoginRequestVo;
import com.atguigu.exam.vo.LoginResponseVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


/**
 * 用户控制器 - 处理用户认证和权限管理相关的HTTP请求
 * 包括用户登录、权限验证等功能
 */
@RestController
@RequestMapping("/api/user")
@CrossOrigin(origins = "*")
@Tag(name = "用户管理", description = "用户相关操作，包括登录认证、权限验证等功能")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 用户登录
     * @param loginRequestVo 登录请求
     * @return 登录结果
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "用户通过用户名和密码进行登录验证，返回用户信息和token")
    public Result<LoginResponseVo> login(@RequestBody LoginRequestVo loginRequestVo) {
        LoginResponseVo loginResponseVo = userService.login(loginRequestVo);
        return Result.success(loginResponseVo);
    }

    /**
     * 检查用户权限
     * @param userId 用户ID
     * @return 权限检查结果
     */
    @GetMapping("/check-admin/{userId}")
    @Operation(summary = "检查管理员权限", description = "验证指定用户是否具有管理员权限")
    public Result<Boolean> checkAdmin(
            @Parameter(description = "用户ID") @PathVariable Long userId) {
        boolean isAdmin = userService.checkAdmin(userId);
        return Result.success(isAdmin);
    }
} 