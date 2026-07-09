package com.carrental.security;

import com.carrental.entity.AuditLog;
import com.carrental.entity.Role;
import com.carrental.entity.Tenant;
import com.carrental.entity.User;
import com.carrental.repository.AuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditLoggingInterceptorTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordsAuthenticatedMutationWithActorTenantAndEntity() {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        AuditLoggingInterceptor interceptor = new AuditLoggingInterceptor(repository);
        Tenant tenant = Tenant.builder().id(7L).name("Atlas Rent").build();
        User user = User.builder().id(11L).email("owner@atlas.test")
                .role(Role.AGENCY_OWNER).tenant(tenant).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));

        MockHttpServletRequest request =
                new MockHttpServletRequest("DELETE", "/api/vehicles/55");
        request.setRemoteAddr("192.0.2.10");
        request.addHeader("User-Agent", "JUnit Browser");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(204);

        interceptor.afterCompletion(request, response, new Object(), null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog log = captor.getValue();
        assertThat(log.getAction()).isEqualTo("DELETE");
        assertThat(log.getEntityType()).isEqualTo("VEHICLES");
        assertThat(log.getEntityId()).isEqualTo(55L);
        assertThat(log.getPerformedById()).isEqualTo(11L);
        assertThat(log.getTenantId()).isEqualTo(7L);
        assertThat(log.getIsSuccess()).isTrue();
    }
}
