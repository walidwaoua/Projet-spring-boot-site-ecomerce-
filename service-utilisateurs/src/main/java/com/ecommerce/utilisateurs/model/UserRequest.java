package com.ecommerce.utilisateurs.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserRequest(
    @NotBlank String nom,
    @NotBlank @Email String email,
    @NotNull UserRole role,
    @NotNull Boolean actif
) {}
