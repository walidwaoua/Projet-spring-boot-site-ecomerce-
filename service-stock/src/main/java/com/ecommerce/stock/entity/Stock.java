package com.ecommerce.stock.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "stocks", uniqueConstraints = @UniqueConstraint(columnNames = "product_id"))
public class Stock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;
    @Column(nullable = false)
    private Integer quantite;
    protected Stock() {}
    public Stock(Long productId, Integer quantite) { this.productId = productId; this.quantite = quantite; }
    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public Integer getQuantite() { return quantite; }
    public void setQuantite(Integer quantite) { this.quantite = quantite; }
}
