package com.atguigu.exam.service;

import com.atguigu.exam.entity.Category;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface CategoryService extends IService<Category> {
    /**
     * 获取分类列表（包含题目数量）
     * @return 分类列表数据
     */
    List<Category> findCategoryList();

    /**
     * 获取分类树形结构
     * @return
     */
    List<Category> findCategoryListTree();
    /**
     * 保存分类
     * @param category
     */
    void saveCategory(Category category);
    /**
     * 修改分类
     * @param category
     */
    void updateCategory(Category category);
    /**
     * 删除分类
     * @param id
     */
    void deleteCategory(Long id);
}