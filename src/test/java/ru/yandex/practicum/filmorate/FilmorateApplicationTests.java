package ru.yandex.practicum.filmorate;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import ru.yandex.practicum.filmorate.controller.FilmController;
import ru.yandex.practicum.filmorate.controller.UserController;
import ru.yandex.practicum.filmorate.exceptions.ValidationException;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.User;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class FilmorateApplicationTests {

	@Test
	void contextLoads() {
	}

	@Test
	void testFilmValidation() {
		Film film = new Film();
		film.setId(1);
		film.setName("");
		film.setDescription("Описание фильма");
		film.setReleaseDate(LocalDate.of(1895, 12, 27));
		film.setDuration(-120);

		assertThrows(ValidationException.class, () -> {
			new FilmController().addFilm(film);
		});
	}

	@Test
	void testUserValidation() {
		User user = new User();
		user.setId(1);
		user.setEmail("wrongEmail");
		user.setLogin("login with spaces");
		user.setBirthday(LocalDate.of(3000, 1, 1));

		assertThrows(ValidationException.class, () -> {
			new UserController().createUser(user);
		});
	}
}
