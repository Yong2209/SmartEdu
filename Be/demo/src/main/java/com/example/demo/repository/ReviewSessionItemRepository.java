package com.example.demo.repository;
import com.example.demo.entity.ReviewSessionItem;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ReviewSessionItemRepository extends JpaRepository<ReviewSessionItem, Long> {
}
