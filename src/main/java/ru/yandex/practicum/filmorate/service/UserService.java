package ru.yandex.practicum.filmorate.service;

import io.micrometer.common.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.filmorate.dal.FriendshipRepository;
import ru.yandex.practicum.filmorate.dal.UserRepository;
import ru.yandex.practicum.filmorate.dto.UserDto;
import ru.yandex.practicum.filmorate.dto.request.NewUserRequest;
import ru.yandex.practicum.filmorate.dto.request.UpdateUserRequest;
import ru.yandex.practicum.filmorate.exceptions.NotFoundException;
import ru.yandex.practicum.filmorate.exceptions.ValidationException;
import ru.yandex.practicum.filmorate.mapper.UserMapper;
import ru.yandex.practicum.filmorate.model.User;
import ru.yandex.practicum.filmorate.storage.user.UserStorage;
import ru.yandex.practicum.filmorate.storage.user.friendship.FriendshipStorage;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private final UserStorage userService;
    private final FriendshipStorage friendshipStorages;

    @Autowired
    public UserService(UserRepository userRepository, FriendshipRepository friendshipRepository) {
        this.friendshipStorages = friendshipRepository;
        this.userService = userRepository;
    }

    public List<UserDto> findAll() {
        return userService.findAll().stream()
                .map(UserMapper::mapToUserDto)
                .toList();
    }

    public UserDto create(NewUserRequest request) {
        User user = UserMapper.mapToUser(request);
        validateUser(user);
        user = userService.create(user);
        return UserMapper.mapToUserDto(user);
    }

    public void removeUser(Integer id) {
        userService.delete(id);
    }

    public UserDto update(Integer id, UpdateUserRequest request) {
        User updateUser = userService.getUserById(id)
                .map(user -> UserMapper.updateUserFields(user, request))
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        validateUser(updateUser);
        userService.update(updateUser);
        return UserMapper.mapToUserDto(updateUser);
    }

    public UserDto getUserById(Integer id) {
        return userService.getUserById(id)
                .map(UserMapper::mapToUserDto)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден с ID:" + id));
    }

    public Optional<List<UserDto>> getFriends(Integer id) {
        UserDto user = getUserById(id);
        List<UserDto> result = friendshipStorages.getFriends(id)
                .stream()
                .filter(Objects::nonNull)
                .map(UserMapper::mapToUserDto)
                .toList();
        return Optional.of(result);
    }

    public UserDto getFriendById(Integer userId, Integer friendId) {
        return friendshipStorages.getFriendById(userId, friendId)
                .map(UserMapper::mapToUserDto)
                .orElseThrow(() -> new NotFoundException("У пользователя нет такого друга"));
    }

    public void addFriend(Integer userId1, Integer userId2) {
        if (Objects.equals(userId1, userId2)) {
            log.error("Нельзя добавить в друзья самого себя");
            throw new ValidationException("Нельзя добавить в друзья самого себя");
        }
        Optional<User> user1 = userService.getUserById(userId1);
        Optional<User> user2 = userService.getUserById(userId2);
        if (user1.isEmpty() || user2.isEmpty()) {
            log.error("Ошибка при добавлении друзей: один из пользователей не найден. {}, {}", userId2, userId1);
            throw new NotFoundException("Один из пользователей не найден");
        }
        friendshipStorages.addFriend(userId1, userId2);
        log.info("Пользователь {} добавил пользователя {} в друзья", userId1, userId2); // Исправлено сообщение
    }

    public UserDto removeFriend(Integer userId1, Integer userId2) {
        Optional<User> userOptional1 = userService.getUserById(userId1);
        Optional<User> userOptional2 = userService.getUserById(userId2);
        if (userOptional1.isEmpty() || userOptional2.isEmpty()) {
            log.error("Ошибка при удалении друзей: один из пользователей не найден. {}, {}", userId2, userId1);
            throw new NotFoundException("Один из пользователей не найден");
        }
        friendshipStorages.deleteFriend(userId1, userId2);
        log.info("Пользователи {},{} удалены из друзей", userId1, userId2);
        return getUserById(userId1);
    }

    public Collection<UserDto> getMutualFriends(Integer userId1, Integer userId2) {
        Optional<User> userOptional1 = userService.getUserById(userId1);
        Optional<User> userOptional2 = userService.getUserById(userId2);
        if (userOptional1.isEmpty() || userOptional2.isEmpty()) {
            log.error("Ошибка при получении общих друзей");
            throw new NotFoundException("Один из пользователей не найден");
        }
        List<User> result = friendshipStorages.getMutualFriends(userId1, userId2);
        if (result.isEmpty()) {
            log.error("Нет общих друзей");
            throw new NotFoundException("Общие друзья не найдены");
        }
        log.info("Возвращён список общих друзей пользователей: {}, {}", userId2, userId1);
        return result.stream()
                .map(UserMapper::mapToUserDto)
                .toList();
    }

    private void validateUser(User user) {
        String email = user.getEmail();
        if (StringUtils.isBlank(email) || !email.contains("@")) {
            log.error("Ошибка при добавлении пользователя: некорректная почта - {}", email);
            throw new ValidationException("Некорректная электронная почта.");
        }
        String login = user.getLogin();
        if (login.isEmpty() || StringUtils.isBlank(login) || login.contains(" ")) {
            log.error("Ошибка при добавлении пользователя: некорректный логин - {}", login);
            throw new ValidationException("Некорректный логин.");
        }
        String name = user.getName();
        if (name == null || name.isEmpty() || StringUtils.isBlank(name)) {
            log.info("Пользователь использует логин - {} вместо имени", user.getLogin());
            user.setName(user.getLogin());
        }
        LocalDate birthday = user.getBirthday();
        if (birthday.isAfter(LocalDate.now())) {
            log.error("Ошибка при добавлении пользователя: некорректная дата рождения - {}", birthday);
            throw new ValidationException("Некорректная дата рождения.");
        }
    }

}
