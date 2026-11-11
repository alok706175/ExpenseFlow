package com.expensetracker.service;

import com.expensetracker.dto.CategoryDTO;
import com.expensetracker.entity.User;

import java.util.List;

public interface CategoryService {

    List<CategoryDTO> getCategoriesForUser(User user);

    CategoryDTO createCategory(CategoryDTO categoryDTO, User user);

    void deleteCategory(Long categoryId, User user);
}
