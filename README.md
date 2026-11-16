# TALLER FINAL: DESARROLLO DE SOFTWARE SEGURO
## API REST con Spring Boot 4 / Java 21, Spring Security y MySQL

Proyecto académico corregido y completado según los requerimientos del taller de **Desarrollo de Software Seguro**:
1. **Base de Datos Relacional:** 4 tablas normalizadas (`usuarios`, `roles`, `roles_usuarios`, `empleados`).
2. **Motor Oficial:** MySQL (incluye soporte inmediato con H2 en modo MySQL para pruebas locales sin requerir configuración previa).
3. **Autenticación y Cifrado:** Spring Security con `DaoAuthenticationProvider`, `UserDetailsService` dinámico y contraseñas cifradas con **BCrypt**.
4. **Control de Acceso Basado en Roles (RBAC):**
   - **Público (Sin autenticación):** Consulta de empleados (`GET /api/empleados`, `GET /api/empleados/{id}`).
   - **ADMIN:** Consulta, creación, edición y eliminación de empleados; gestión completa de usuarios.
   - **USER (Usuario normal):** Consulta, creación y edición de empleados. **Eliminación denegada (HTTP 403 Forbidden)**. Creación y edición de usuarios con prevención de escalamiento de privilegios.

---

## 1. Credenciales de Desarrollo

| Usuario | Contraseña | Rol Asignado | Alcance en Empleados | Alcance en Usuarios |
| :--- | :--- | :--- | :--- | :--- |
| **admin** | `admin123` | `ADMIN` | Consulta, Creación, Edición y **Eliminación** | Control total de usuarios y roles |
| **user** | `user123` | `USER` | Consulta, Creación y Edición (**Eliminación denegada**) | Creación y edición (rol asignado forzado a USER) |
| *(Anónimo)* | *(Ninguno)* | Ninguno | **Solo Consulta (`GET`)** | Acceso denegado (401 Unauthorized) |

---

## 2. Requisitos y Ejecución

- **Java:** JDK 21 instalado.
- **Maven:** 3.9+ instalado (o ejecutable `mvn`).

### Paso a paso para iniciar:
```powershell
# En la raíz del proyecto (c:\Users\laptop01\Downloads\Software):
mvn clean test
mvn spring-boot:run
```
La aplicación iniciará en `http://localhost:8080`.

---

## 3. Conexión a Base de Datos MySQL

Por defecto, la aplicación arranca con base de datos en memoria en modo MySQL para que compile y funcione inmediatamente sin depender de contraseñas de servicios locales.

Para activar tu servidor **MySQL local** (ej. puerto 3306):
1. Abre [database_schema.sql](file:///c:/Users/laptop01/Downloads/Software/database_schema.sql) y ejecútalo en MySQL Workbench o cliente SQL para crear las tablas y datos iniciales.
2. Abre [application.properties](file:///c:/Users/laptop01/Downloads/Software/src/main/resources/application.properties), comenta el bloque H2 y descomenta:
   ```properties
   spring.datasource.url=jdbc:mysql://localhost:3306/seguridaddb?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
   spring.datasource.username=root
   spring.datasource.password=TU_PASSWORD_MYSQL
   spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
   spring.jpa.hibernate.ddl-auto=update
   ```

---

## 4. Pruebas con Postman

Se incluye la colección [Postman_Collection_Taller_Seguridad.json](file:///c:/Users/laptop01/Downloads/Software/Postman_Collection_Taller_Seguridad.json) con **13 casos de prueba exhaustivos**, configurada con variables de colección (`baseUrl`, `admin_user`, `admin_pass`, `normal_user`, `normal_pass`):

1. **Importar:** Abre Postman -> Clic en **Import** -> Selecciona el archivo `.json`.
2. **Ejecución:** Cada solicitud ya tiene configurada su autenticación básica automática y cuerpo JSON.
3. **Casos Clave a Demostrar al Tutor:**
   - Petición 01: Consulta de empleados sin credenciales (Retorna `200 OK`).
   - Petición 02: Creación anónima rechazada (Retorna `401 Unauthorized`).
   - Petición 06: Eliminación de empleado como ADMIN (Retorna `204 No Content`).
   - Petición 10: Intento de eliminación como USER (Retorna `403 Forbidden`).
   - Petición 11: USER crea usuario; el sistema sanitiza la petición forzando rol `USER`.
   - Petición 13: USER intenta modificar la cuenta `admin` raíz (Retorna `403 Forbidden`).
