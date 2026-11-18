package com.ecommerce.catalogue.repository;

import com.ecommerce.catalogue.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
