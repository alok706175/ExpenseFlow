package com.expensetracker.controller;

import com.expensetracker.dto.CategoryDTO;
import com.expensetracker.entity.User;
import com.expensetracker.service.AuthService;
import com.expensetracker.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@Tag(name = "Categories", description = "Endpoints for managing default and custom spending categories")
public class CategoryController {

    private final CategoryService categoryService;
    private final AuthService authService;

    public CategoryController(CategoryService categoryService, AuthService authService) {
        this.categoryService = categoryService;
        this.authService = authService;
    }

    @GetMapping
    @Operation(summary = "Get all categories available to user", description = "Returns default categories plus user's custom categories")
    public ResponseEntity<List<CategoryDTO>> getCategories() {
        User user = authService.getCurrentAuthenticatedUser();
        return ResponseEntity.ok(categoryService.getCategoriesForUser(user));
    }

    @PostMapping
    @Operation(summary = "Create custom category", description = "Creates a new category for the current user")
    public ResponseEntity<CategoryDTO> createCategory(@Valid @RequestBody CategoryDTO categoryDTO) {
        User user = authService.getCurrentAuthenticatedUser();
        CategoryDTO created = categoryService.createCategory(categoryDTO, user);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete custom category", description = "Removes a user-created category")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        User user = authService.getCurrentAuthenticatedUser();
        categoryService.deleteCategory(id, user);
        return ResponseEntity.noContent().build();
    }
}
