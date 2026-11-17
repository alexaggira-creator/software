package com.taller.seguridad.service;

import com.taller.seguridad.model.Empleado;
import com.taller.seguridad.repository.EmpleadoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class EmpleadoService {

    private final EmpleadoRepository empleadoRepository;

    public EmpleadoService(EmpleadoRepository empleadoRepository) {
        this.empleadoRepository = empleadoRepository;
    }

    @Transactional(readOnly = true)
    public List<Empleado> listarTodos() {
        return empleadoRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Empleado> buscarPorId(Long id) {
        return empleadoRepository.findById(id);
    }

    /**
     * Crea un nuevo empleado.
     * La validación de campos obligatorios (nombre, apellido, email, cargo, salario)
     * se realiza mediante las anotaciones @Valid en el controlador y @NotBlank/@NotNull
     * en la entidad Empleado, por lo que no se duplican aquí.
     * Solo se comprueba la unicidad del email.
     */
    @Transactional
    public Empleado guardar(Empleado empleado) {
        String email = empleado.getEmail();
        if (email != null && !email.isBlank()
                && empleadoRepository.existsByEmail(email)) {
            throw new IllegalArgumentException(
                    "Ya existe un empleado registrado con el email: " + email);
        }
        return empleadoRepository.save(empleado);
    }

    /**
     * Actualiza un empleado existente.
     *
     * @throws NoSuchElementException si no existe un empleado con el id indicado.
     * @throws IllegalArgumentException si el nuevo email ya pertenece a otro empleado.
     */
    @Transactional
    public Empleado actualizar(Long id, Empleado datosActualizados) {
        Empleado existente = empleadoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException(
                        "Empleado no encontrado con ID: " + id));

        // Comparar emails de forma segura (evitar NullPointerException)
        String emailActual = existente.getEmail();
        String emailNuevo  = datosActualizados.getEmail();

        boolean emailCambia = emailNuevo != null
                && !emailNuevo.equalsIgnoreCase(emailActual);

        if (emailCambia && empleadoRepository.existsByEmail(emailNuevo)) {
            throw new IllegalArgumentException(
                    "El email '" + emailNuevo + "' ya está registrado por otro empleado.");
        }

        existente.setNombre(datosActualizados.getNombre());
        existente.setApellido(datosActualizados.getApellido());
        existente.setEmail(emailNuevo != null ? emailNuevo : emailActual);
        existente.setCargo(datosActualizados.getCargo());
        existente.setSalario(datosActualizados.getSalario());

        return empleadoRepository.save(existente);
    }

    /**
     * Elimina un empleado por ID.
     *
     * @throws NoSuchElementException si no existe un empleado con el id indicado.
     */
    @Transactional
    public void eliminar(Long id) {
        if (!empleadoRepository.existsById(id)) {
            throw new NoSuchElementException(
                    "Empleado no encontrado con ID: " + id);
        }
        empleadoRepository.deleteById(id);
    }
}
