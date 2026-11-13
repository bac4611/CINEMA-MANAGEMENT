package com.cinema.management.controller.admin;

import com.cinema.management.model.dto.request.AdminSettingsRequest;
import com.cinema.management.service.admin.AdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "security.jwt.key=12345678901234567890123456789012",
        "security.jwt.issuer=cosmos-cinema",
        "security.jwt.audience=cosmos-cinema-api"
})
class AdminControllerTests {
    @Autowired private WebApplicationContext context;
    @MockitoBean private AdminService admins;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void adminEndpointsRequireAdminRoleAndCsrf() throws Exception {
        mvc.perform(post("/Admin/CreateStaff").with(user("staff@example.com").roles("Staff")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/Admin/CreateStaff").with(user("admin@example.com").roles("Admin")))
                .andExpect(status().isForbidden());

        when(admins.createStaff(any())).thenReturn(AdminService.OperationResult.success("created"));
        mvc.perform(post("/Admin/CreateStaff").with(user("admin@example.com").roles("Admin")).with(csrf())
                        .param("fullName", "Nguyen Van A")
                        .param("email", "staff@example.com")
                        .param("phoneNumber", "0901234567")
                        .param("password", "Password!1")
                        .param("dateOfBirth", "2000-01-01")
                        .param("page", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/Admin/Staff?page=2"));
        verify(admins).createStaff(any());
    }

    @Test
    void updateSettingsUsesAuthenticatedSessionUserAndRefreshesSessionEmail() throws Exception {
        when(admins.updateSettings(eq(1), any(AdminSettingsRequest.class)))
                .thenReturn(AdminService.OperationResult.success("updated"));
        mvc.perform(post("/Admin/UpdateSettings")
                        .with(user("admin@example.com").roles("Admin")).with(csrf())
                        .sessionAttr("UserID", 1)
                        .param("fullName", "Admin")
                        .param("email", " NEW@EXAMPLE.COM "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/Admin/Settings"))
                .andExpect(request().sessionAttribute("UserEmail", "new@example.com"));
        verify(admins).updateSettings(eq(1), any(AdminSettingsRequest.class));
    }

    @Test
    void settingsWithoutSessionIdentityReturnsToLogin() throws Exception {
        mvc.perform(post("/Admin/UpdateSettings")
                        .with(user("admin@example.com").roles("Admin")).with(csrf())
                        .param("fullName", "Admin").param("email", "admin@example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/Account/Login"));
        verifyNoInteractions(admins);
    }
}
