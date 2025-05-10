package ru.yandex.practicum.filmorate.storage.user;

import ru.yandex.practicum.filmorate.model.User;

import java.util.List;
import java.util.Optional;

public interface UserStorage {
    List<User> findAll();

    User create(User user);

    User update(User newUser);

    boolean delete(Integer id);

    Optional<User> getUserById(Integer id);

    Optional<List<User>> getFriends(Integer id);
}

