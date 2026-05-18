package com.atguigu.exam.service;

import com.atguigu.exam.entity.Paper;
import com.atguigu.exam.vo.AiPaperVo;
import com.atguigu.exam.vo.PaperVo;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 试卷服务接口
 */
public interface PaperService extends IService<Paper> {
    /**
     * 手动创建试卷
     * @param paperVo
     * @return
     */
    Paper createPaper(PaperVo paperVo);

    /**
     * 智能组卷
     * @param aiPaperVo
     * @return
     */
    Paper createPaperWithAI(AiPaperVo aiPaperVo);

    /**
     * 更新试卷
     * @param id
     * @param paperVo
     * @return
     */
    Paper updatePaper(Integer id, PaperVo paperVo);
    /**
     * 删除试卷
     * @param id
     */
    void removePaper(Integer id);
    /**
     * 根据id查询试卷
     * @param id
     * @return
     */
    Paper getPaperById(Integer id);
}