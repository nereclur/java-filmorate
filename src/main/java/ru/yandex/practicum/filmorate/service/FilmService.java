package ru.yandex.practicum.filmorate.service;

import io.micrometer.common.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.filmorate.dal.FilmRepository;
import ru.yandex.practicum.filmorate.dal.LikeRepository;
import ru.yandex.practicum.filmorate.dal.MpaRepository;
import ru.yandex.practicum.filmorate.dal.UserRepository;
import ru.yandex.practicum.filmorate.dto.FilmDto;
import ru.yandex.practicum.filmorate.dto.request.NewFilmRequest;
import ru.yandex.practicum.filmorate.dto.request.UpdateFilmRequest;
import ru.yandex.practicum.filmorate.exceptions.NotFoundException;
import ru.yandex.practicum.filmorate.exceptions.ValidationException;
import ru.yandex.practicum.filmorate.mapper.FilmMapper;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.Genre;
import ru.yandex.practicum.filmorate.model.Mpa;
import ru.yandex.practicum.filmorate.model.User;
import ru.yandex.practicum.filmorate.storage.film.FilmStorage;
import ru.yandex.practicum.filmorate.storage.film.like.LikeStorage;
import ru.yandex.practicum.filmorate.storage.user.UserStorage;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class FilmService {
    private static final LocalDate MOVIE_BIRTHDAY = LocalDate.of(1895, 12, 28);
    private static final Logger log = LoggerFactory.getLogger(FilmService.class);
    private final FilmStorage fs;
    private final LikeStorage ls;
    private final UserStorage us;
    private final MpaRepository mpaRepository;
    private final JdbcTemplate jdbc;


    @Autowired
    public FilmService(FilmRepository filmRepository, LikeRepository likeRepository, UserRepository userRepository, MpaRepository mpaRepository, JdbcTemplate jdbc) {
        this.fs = filmRepository;
        this.ls = likeRepository;
        this.us = userRepository;
        this.mpaRepository = mpaRepository;
        this.jdbc = jdbc; // Инициализация
    }

    public List<FilmDto> findAll() {
        return fs.findAll().stream().map(FilmMapper::mapToFilmDto).toList();
    }

    public FilmDto create(NewFilmRequest request) {
        Film film = FilmMapper.mapToFilm(request);
        validateFilm(film);
        if (film.getMpa() != null) {
            Optional<Mpa> mpaOptional = mpaRepository.getRatingById(film.getMpa().getId());
            if (mpaOptional.isEmpty()) {
                throw new NotFoundException("Рейтинг MPA с id " + film.getMpa().getId() + " не найден");
            }
        }
        film = fs.create(film);
        return FilmMapper.mapToFilmDto(film);
    }

    public FilmDto update(Integer id, UpdateFilmRequest request) {
        Film film = fs.getFilmById(id).map(film1 -> FilmMapper.updateFilmFields(film1, request)).orElseThrow(() -> new NotFoundException("Фильм не найден"));
        validateFilm(film);
        film = fs.update(film);
        return FilmMapper.mapToFilmDto(film);
    }

    public FilmDto getFilmById(Integer id) {
        return fs.getFilmById(id).map(FilmMapper::mapToFilmDto).orElseThrow(() -> new NotFoundException("Не найден фильм с id" + id));
    }

    public void addLike(Integer userId, Integer filmId) {
        Optional<User> userOptional = us.getUserById(userId);
        if (userOptional.isEmpty()) {
            log.error("Ошибка при добавлении лайка: пользователь с id {}  не найден", userId);
            throw new NotFoundException("Пользователь с id " + userId + " не найден");
        }
        Film film = fs.getFilmById(filmId).orElseThrow(() -> new NotFoundException("Фильм с id " + filmId + " не найден"));
        ls.addLike(filmId, userId);
        log.trace("Фильму с id {} поставлен лайк пользователем с id {}", filmId, userId);
    }

    public void removeLike(Integer userId, Integer filmId) {
        Optional<User> userOptional = us.getUserById(userId);
        if (userOptional.isEmpty()) {
            log.error("Ошибка при удалении лайка: пользователь с id {}  не найден", userId);
            throw new NotFoundException("Пользователь с id " + userId + " не найден");
        }
        Optional<Film> filmOptional = fs.getFilmById(filmId);
        if (filmOptional.isPresent()) {
            ls.removeLike(filmId, userId);
            log.trace("Фильму с id {} удалён лайк пользователем с id {}", filmId, userId);
        } else {
            log.error("Ошибка при удалении лайка: фильм с id {}  не найден", filmId);
            throw new NotFoundException("Фильм с id " + filmId + " не найден");
        }
    }

    public List<FilmDto> getBestFilms(Integer count) {
        List<Film> allFilms = ls.findBestFilms(count);
        return allFilms.stream().map(FilmMapper::mapToFilmDto).toList();
    }

    private void validateFilm(Film film) {
        String name = film.getName();
        validate(() -> name.isEmpty() || StringUtils.isBlank(name), "Название не может быть пустым.");
        validate(() -> film.getDescription().length() > 200, "Максимальная длина строки - 200 символов.");
        validate(() -> film.getDuration() < 1, "Продолжительность не может быть отрицательной.");
        LocalDate releaseDate = film.getReleaseDate();
        validate(() -> releaseDate.isBefore(MOVIE_BIRTHDAY), "Релиз не может быть раньше 28 декабря 1985 года.");
    }

    private void validate(Supplier<Boolean> supplier, String massage) {
        if (supplier.get()) {
            log.error("Ошибка при валидации фильма: {}", massage);
            throw new ValidationException(massage);
        }
    }

    private void validateGenresExist(Collection<Genre> genres) {
        if (genres == null || genres.isEmpty()) {
            return;
        }

        Set<Integer> genreIds = genres.stream()
                .map(Genre::getId)
                .collect(Collectors.toSet());

        String sqlQuery = String.format("SELECT genre_id FROM Genres WHERE genre_id IN (%s)",
                genreIds.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(", ")));

        List<Integer> existingIds = jdbc.query(sqlQuery, (rs, rowNum) -> rs.getInt("genre_id"));

        if (existingIds.size() != genreIds.size()) {
            genreIds.removeAll(existingIds);
            throw new NotFoundException("Некоторые жанры не существуют: " + genreIds);
        }
    }
}
