package ru.yandex.practicum.filmorate.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.filmorate.exceptions.ValidationException;
import ru.yandex.practicum.filmorate.model.Film;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;


@Slf4j
@RestController
@RequestMapping("/films")
public class FilmController {
    private static final LocalDate MOVIE_BIRTHDAY = LocalDate.of(1895, 12, 28);
    private final Map<Integer, Film> films = new HashMap<>();
    private int idGenerator = 0;

    @GetMapping
    public Collection<Film> getFilms() {
        log.info("Пришел запрос Get /films");
        Collection<Film> resFilms = films.values();
        log.info("Отправлен ответ Get /films : {}", resFilms);
        return resFilms;
    }

    @PostMapping
    public Film addFilm(@RequestBody Film film) {
        log.info("пришел Post запрос /films с фильмом: {}", film);
        validateFilm(film);
        film.setId(++idGenerator);
        films.put(film.getId(), film);
        log.info("Отправлен ответ Post /films с фильмом: {}", film);
        return film;
    }

    @PutMapping
    public Film updateFilm(@RequestBody Film film) {
        log.info("пришел Put запрос /films с фильмом: {}.", film);
        validateFilm(film);
        Film oldFilm = films.get(film.getId());
        if (oldFilm == null) {
            log.error("Фильм с id {} не найден.", film.getId());
            throw new ValidationException("Фильм не найден.");
        }
        films.put(film.getId(), film);
        log.info("Отправлен ответ Put /films с фильмом: {}.", film);
        return film;
    }

    private void validateFilm(Film film) {
        String name = film.getName();
        if (name == null || name.isBlank()) {
            log.error("Ошибка при добавлении фильма: введено пустое название.");
            throw new ValidationException("Название не может быть пустым.");
        }
        if (film.getDescription().length() > 200) {
            log.error("Ошибка при добавлении фильма: превышена максимальная длина описания.");
            throw new ValidationException("Максимальная длина строки - 200 символов.");
        }
        if (film.getDuration() < 1) {
            log.error("Ошибка при добавлении фильма: введена некорректная продолжительность - {}.", film.getDuration());
            throw new ValidationException("Продолжительность не может быть отрицательной.");
        }
        LocalDate releaseDate = film.getReleaseDate();
        if (releaseDate == null) {
            log.error("Ошибка при добавлении фильма: введена пустая дата релиза.");
            throw new ValidationException("Дата релиза не может быть пустой.");
        } else if (releaseDate.isBefore(MOVIE_BIRTHDAY)) {
            log.error("Ошибка при добавлении фильма: введена дата релиза раньше 28 декабря 1985 года - {}.", releaseDate);
            throw new ValidationException("Релиз не может быть раньше 28 декабря 1985 года.");
        }
    }
}