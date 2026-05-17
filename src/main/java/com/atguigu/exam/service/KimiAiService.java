package com.atguigu.exam.service;


import com.atguigu.exam.vo.AiGenerateRequestVo;

/**
 * Kimi AI服务接口
 * 用于调用Kimi API生成题目
 */
public interface KimiAiService {
    /**
     * 构建发送给AI的提示词
     */
    String buildPrompt(AiGenerateRequestVo request);
    /**
     * 调用Kimi AI生成题目
     */
    String callKimiAi(String  prompt);
} 