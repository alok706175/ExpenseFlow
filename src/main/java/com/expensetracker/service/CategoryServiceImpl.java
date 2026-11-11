package com.expensetracker.service;

import com.expensetracker.dto.CategoryDTO;
import com.expensetracker.entity.Category;
import com.expensetracker.entity.User;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryServiceImpl(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryDTO> getCategoriesForUser(User user) {
        return categoryRepository.findAllAvailableForUser(user)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public CategoryDTO createCategory(CategoryDTO categoryDTO, User user) {
        String name = categoryDTO.getName().trim();

        if (categoryRepository.existsByNameIgnoreCaseAndUser(name, user) ||
            categoryRepository.existsByNameIgnoreCaseAndUserIsNull(name)) {
            throw new IllegalArgumentException("A category named '" + name + "' already exists");
        }

        Category category = new Category(
                name,
                categoryDTO.getColor() != null ? categoryDTO.getColor() : "#64748b",
                categoryDTO.getIcon() != null ? categoryDTO.getIcon() : "fa-tag",
                categoryDTO.getType(),
                user
        );

        Category saved = categoryRepository.save(category);
        return mapToDTO(saved);
    }

    @Override
    public void deleteCategory(Long categoryId, User user) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + categoryId));

        if (category.getUser() == null || !category.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Cannot delete default system categories or categories belonging to other users");
        }

        categoryRepository.delete(category);
    }

    private CategoryDTO mapToDTO(Category c) {
        return new CategoryDTO(
                c.getId(),
                c.getName(),
                c.getColor(),
                c.getIcon(),
                c.getType(),
                c.getUser() != null
        );
    }
}
