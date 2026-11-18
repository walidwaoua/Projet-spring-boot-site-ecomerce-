package com.ecommerce.utilisateurs.service;

import com.ecommerce.utilisateurs.exception.UserNotFoundException;
import com.ecommerce.utilisateurs.model.User;
import com.ecommerce.utilisateurs.model.UserRequest;
import com.ecommerce.utilisateurs.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class UserService {
    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    public List<User> findAll() { return repository.findAll(); }

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    public User create(UserRequest request) {
        User user = new User();
        apply(user, request);
        return repository.save(user);
    }

    public User update(Long id, UserRequest request) {
        User user = findById(id);
        apply(user, request);
        return repository.save(user);
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) throw new UserNotFoundException(id);
        repository.deleteById(id);
    }

    private void apply(User user, UserRequest request) {
        user.setNom(request.nom());
        user.setEmail(request.email());
        user.setRole(request.role());
        user.setActif(request.actif());
    }
}
