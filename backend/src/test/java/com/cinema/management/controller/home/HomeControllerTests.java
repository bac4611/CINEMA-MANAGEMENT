package com.cinema.management.controller.home;

import com.cinema.management.model.viewmodel.home.MovieViewModel;
import com.cinema.management.service.home.HomeService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ConcurrentModel;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HomeControllerTests {
    private final HomeService home = mock(HomeService.class);
    private final HomeController controller = new HomeController(home);

    @Test
    void detailsOnlyCoordinatesServiceSessionAndViewModel() {
        MovieViewModel movie = new MovieViewModel();
        movie.setMovieId(7);
        when(home.getMovieDetails(7)).thenReturn(Optional.of(movie));
        when(home.isActiveUser(3)).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("UserID", 3);
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.details(7, request, model);

        assertEquals("home/details", view);
        assertSame(movie, model.getAttribute("movie"));
        assertEquals(true, model.getAttribute("canReview"));
        verify(home).getMovieDetails(7);
        verify(home).isActiveUser(3);
    }
}
