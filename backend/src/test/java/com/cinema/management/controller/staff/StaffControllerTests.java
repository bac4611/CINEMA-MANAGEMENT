package com.cinema.management.controller.staff;

import com.cinema.management.model.dto.request.SaveScheduleRequest;
import com.cinema.management.model.viewmodel.staff.StaffScheduleViewModel;
import com.cinema.management.service.staff.StaffService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
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
class StaffControllerTests {
    @Autowired private WebApplicationContext context;
    @MockitoBean private StaffService staff;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void staffAreaAllowsStaffAndAdminButRejectsCustomer() throws Exception {
        when(staff.getSchedule(any())).thenReturn(List.of());

        mvc.perform(get("/Staff/GetSchedule").param("date", "2026-11-12")
                        .with(user("customer").roles("KhachHang")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/Staff/GetSchedule").param("date", "2026-11-12")
                        .with(user("staff").roles("Staff")))
                .andExpect(status().isOk());
        mvc.perform(get("/Staff/GetSchedule").param("date", "2026-11-12")
                        .with(user("admin").roles("Admin")))
                .andExpect(status().isOk());
    }

    @Test
    void scheduleWriteRequiresCsrfAndDelegatesValidJson() throws Exception {
        mvc.perform(post("/Staff/SaveSchedule").with(user("staff").roles("Staff"))
                        .contentType("application/json").content("{\"date\":\"2026-11-12\",\"items\":[]}"))
                .andExpect(status().isForbidden());

        when(staff.saveSchedule(any(SaveScheduleRequest.class))).thenReturn(
                new StaffService.OperationResult(StaffService.Status.SUCCESS, "saved", 0, 0, 0, 0));
        mvc.perform(post("/Staff/SaveSchedule").with(user("staff").roles("Staff")).with(csrf())
                        .contentType("application/json").content("{\"date\":\"2026-11-12\",\"items\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("saved"));
        verify(staff).saveSchedule(any(SaveScheduleRequest.class));
    }

    @Test
    void scheduleReadReturnsServicePayload() throws Exception {
        when(staff.getSchedule(any())).thenReturn(List.of());
        mvc.perform(get("/Staff/GetSchedule").with(user("admin").roles("Admin"))
                        .param("date", "2026-11-12"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }
}
