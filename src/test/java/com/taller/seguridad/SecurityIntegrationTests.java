package com.taller.seguridad;

import com.taller.seguridad.model.Rol;
import com.taller.seguridad.model.Usuario;
import com.taller.seguridad.repository.RolRepository;
import com.taller.seguridad.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminAuthHeader;
    private String userAuthHeader;

    @BeforeEach
    void setUp() {
        // Eliminar usuarios de prueba (no tocar roles maestros del DataLoader)
        usuarioRepository.findByUsername("testadmin").ifPresent(usuarioRepository::delete);
        usuarioRepository.findByUsername("testuser").ifPresent(usuarioRepository::delete);
        usuarioRepository.findByUsername("testuser2").ifPresent(usuarioRepository::delete);

        // Reusar roles persistidos por DataLoader; crearlos solo si no existen
        Rol rolAdmin = rolRepository.findByNombre("ADMIN")
                .orElseGet(() -> rolRepository.save(new Rol("ADMIN")));
        Rol rolUser = rolRepository.findByNombre("USER")
                .orElseGet(() -> rolRepository.save(new Rol("USER")));

        // Usuario ADMIN de prueba
        Usuario admin = new Usuario();
        admin.setUsername("testadmin");
        admin.setPassword(passwordEncoder.encode("adminPass123"));
        admin.setEnabled(true);
        Set<Rol> rolesAdmin = new HashSet<>();
        rolesAdmin.add(rolAdmin);
        admin.setRoles(rolesAdmin);
        usuarioRepository.save(admin);

        // Usuario USER principal de prueba (autenticado en la mayoría de tests)
        Usuario user = new Usuario();
        user.setUsername("testuser");
        user.setPassword(passwordEncoder.encode("userPass123"));
        user.setEnabled(true);
        Set<Rol> rolesUser = new HashSet<>();
        rolesUser.add(rolUser);
        user.setRoles(rolesUser);
        usuarioRepository.save(user);

        // Segundo usuario USER de prueba (usado en test 8: USER modifica cuenta de otro USER)
        Usuario user2 = new Usuario();
        user2.setUsername("testuser2");
        user2.setPassword(passwordEncoder.encode("user2Pass123"));
        user2.setEnabled(true);
        Set<Rol> rolesUser2 = new HashSet<>();
        rolesUser2.add(rolUser);
        user2.setRoles(rolesUser2);
        usuarioRepository.save(user2);

        // Cabeceras Basic Auth con UTF-8 explícito
        adminAuthHeader = "Basic " + Base64.getEncoder()
                .encodeToString("testadmin:adminPass123".getBytes(StandardCharsets.UTF_8));
        userAuthHeader = "Basic " + Base64.getEncoder()
                .encodeToString("testuser:userPass123".getBytes(StandardCharsets.UTF_8));
    }

    // ─── Pruebas CRUD de Empleados ───────────────────────────────────────────

    // 1. GET /api/empleados sin autenticación → 200 OK (endpoint público)
    @Test
    void whenGetEmployeesWithoutAuth_thenOk() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/empleados"))
                .andExpect(status().isOk());
    }

    // 2. USER autenticado puede crear empleado → 201 Created
    @Test
    void whenUserCreatesEmployee_thenCreated() throws Exception {
        String payload = "{\"nombre\":\"Juan\",\"apellido\":\"Perez\"," +
                "\"email\":\"juan.perez@test.com\",\"cargo\":\"Developer\",\"salario\":3000000}";
        mockMvc.perform(MockMvcRequestBuilders.post("/api/empleados")
                        .header("Authorization", userAuthHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());
    }

    // 3. USER NO puede eliminar empleados → 403 Forbidden
    @Test
    void whenUserDeletesEmployee_thenForbidden() throws Exception {
        String payload = "{\"nombre\":\"Ana\",\"apellido\":\"Gomez\"," +
                "\"email\":\"ana.gomez@test.com\",\"cargo\":\"Tester\",\"salario\":2500000}";

        // ADMIN crea el empleado; verificamos que el header Location esté presente
        String location = mockMvc.perform(MockMvcRequestBuilders.post("/api/empleados")
                        .header("Authorization", adminAuthHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getHeader("Location");

        assertNotNull(location, "El header Location no debe ser null tras crear el empleado");
        String id = location.substring(location.lastIndexOf('/') + 1);

        // USER intenta eliminar → debe recibir 403
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/empleados/" + id)
                        .header("Authorization", userAuthHeader))
                .andExpect(status().isForbidden());
    }

    // 4. ADMIN puede eliminar empleados → 204 No Content
    @Test
    void whenAdminDeletesEmployee_thenNoContent() throws Exception {
        String payload = "{\"nombre\":\"Carlos\",\"apellido\":\"Ruiz\"," +
                "\"email\":\"carlos.ruiz@test.com\",\"cargo\":\"Manager\",\"salario\":5000000}";

        // ADMIN crea el empleado; verificamos que el header Location esté presente
        String location = mockMvc.perform(MockMvcRequestBuilders.post("/api/empleados")
                        .header("Authorization", adminAuthHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getHeader("Location");

        assertNotNull(location, "El header Location no debe ser null tras crear el empleado");
        String id = location.substring(location.lastIndexOf('/') + 1);

        // ADMIN elimina → 204 No Content
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/empleados/" + id)
                        .header("Authorization", adminAuthHeader))
                .andExpect(status().isNoContent());
    }

    // 5. POST /api/empleados sin autenticación → 401 Unauthorized
    @Test
    void whenPostEmpleadoWithoutAuth_thenUnauthorized() throws Exception {
        // No se envía autenticación; Spring Security debe rechazar antes de llegar al controlador
        String payload = "{\"nombre\":\"Luis\",\"apellido\":\"Torres\"," +
                "\"email\":\"luis.torres@test.com\",\"cargo\":\"Analyst\",\"salario\":2000000}";
        mockMvc.perform(MockMvcRequestBuilders.post("/api/empleados")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }

    // 6. Las contraseñas se almacenan como hash BCrypt (nunca en texto plano)
    @Test
    void whenUserSaved_passwordIsEncoded() {
        Usuario user = usuarioRepository.findByUsername("testuser").orElseThrow();
        assertTrue(passwordEncoder.matches("userPass123", user.getPassword()),
                "BCrypt debe validar la contraseña original contra el hash almacenado");
        assertTrue(user.getPassword().startsWith("$2a") || user.getPassword().startsWith("$2b"),
                "El hash BCrypt debe comenzar con $2a o $2b");
    }

    // ─── Pruebas de seguridad sobre Usuarios ─────────────────────────────────

    // 7. USER no puede crear una cuenta con rol ADMIN (escalamiento de privilegios) → 403
    @Test
    void whenUserCreatesAccountWithAdminRole_thenForbidden() throws Exception {
        String payload = "{\"username\":\"hacker\",\"password\":\"pass123\",\"roles\":[\"ADMIN\"]}";
        mockMvc.perform(MockMvcRequestBuilders.post("/api/usuarios")
                        .header("Authorization", userAuthHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }

    // 8. USER no puede modificar la cuenta de OTRO usuario con rol USER → 403
    //    (testuser intenta modificar testuser2, que también tiene rol USER)
    @Test
    void whenUserUpdatesAnotherUserAccount_thenForbidden() throws Exception {
        Long otroUserId = usuarioRepository.findByUsername("testuser2")
                .orElseThrow(() -> new IllegalStateException("testuser2 no fue creado en setUp"))
                .getId();

        String payload = "{\"password\":\"newpass123\"}";
        mockMvc.perform(MockMvcRequestBuilders.put("/api/usuarios/" + otroUserId)
                        .header("Authorization", userAuthHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }

    // 9. USER no puede modificar una cuenta que tiene rol ADMIN → 403
    @Test
    void whenUserUpdatesAdminAccount_thenForbidden() throws Exception {
        Long adminId = usuarioRepository.findByUsername("testadmin")
                .orElseThrow().getId();

        String payload = "{\"password\":\"hackpass123\"}";
        mockMvc.perform(MockMvcRequestBuilders.put("/api/usuarios/" + adminId)
                        .header("Authorization", userAuthHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }

    // 10. ADMIN puede asignar rol USER válido; se comprueba la persistencia en BD → 200
    @Test
    void whenAdminAssignsValidRole_thenOkAndPersistedInDb() throws Exception {
        // Quitar todos los roles de testuser directamente en BD para partir de estado conocido
        Usuario testuser = usuarioRepository.findByUsername("testuser").orElseThrow();
        testuser.setRoles(new HashSet<>());
        usuarioRepository.save(testuser);

        Long userId = testuser.getId();

        // ADMIN asigna el rol USER mediante la API
        String payload = "{\"roles\":[\"USER\"]}";
        mockMvc.perform(MockMvcRequestBuilders.put("/api/usuarios/" + userId)
                        .header("Authorization", adminAuthHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        // Verificar que el rol USER quedó realmente persistido en la base de datos
        Usuario actualizado = usuarioRepository.findById(userId).orElseThrow();
        boolean tieneRolUser = actualizado.getRoles().stream()
                .anyMatch(r -> "USER".equalsIgnoreCase(r.getNombre()));
        assertTrue(tieneRolUser,
                "El rol USER debe estar persistido en BD tras la asignación por ADMIN");
    }

    // 11. Se rechazan roles desconocidos en la creación de usuarios → 400 Bad Request
    @Test
    void whenUnknownRoleProvided_thenBadRequest() throws Exception {
        String payload = "{\"username\":\"newuser99\",\"password\":\"pass123\",\"roles\":[\"SUPERADMIN\"]}";
        mockMvc.perform(MockMvcRequestBuilders.post("/api/usuarios")
                        .header("Authorization", adminAuthHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    // 12. USER puede modificar su propia cuenta; la nueva contraseña se almacena con BCrypt → 200
    @Test
    void whenUserUpdatesOwnAccount_thenOkAndPasswordReencodedWithBCrypt() throws Exception {
        Long userId = usuarioRepository.findByUsername("testuser")
                .orElseThrow().getId();

        String payload = "{\"password\":\"newpassword456\"}";
        mockMvc.perform(MockMvcRequestBuilders.put("/api/usuarios/" + userId)
                        .header("Authorization", userAuthHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        // Confirmar que la nueva contraseña se almacenó correctamente con BCrypt
        Usuario updated = usuarioRepository.findByUsername("testuser").orElseThrow();
        assertTrue(passwordEncoder.matches("newpassword456", updated.getPassword()),
                "La nueva contraseña debe validarse correctamente con BCrypt");
        assertTrue(updated.getPassword().startsWith("$2a") || updated.getPassword().startsWith("$2b"),
                "El hash de la nueva contraseña debe ser BCrypt ($2a o $2b)");
    }
}
