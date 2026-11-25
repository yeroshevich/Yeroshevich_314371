package by.technosoft.bonus.domain;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Сотрудник (таблица employee). */
@Entity
@Table(name = "employee")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Column(name = "position", nullable = false, length = 100)
    private String position;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    /** Непосредственный руководитель; по нему определяется «команда» руководителя (UC5). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private Employee manager;

    @Column(name = "hired_at", nullable = false)
    private LocalDate hiredAt;

    /** Идентификатор в HRIS (DATA-2). */
    @Column(name = "external_id", length = 50)
    private String externalId;

    protected Employee() {
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPosition() {
        return position;
    }

    public Department getDepartment() {
        return department;
    }

    public Employee getManager() {
        return manager;
    }

    public LocalDate getHiredAt() {
        return hiredAt;
    }

    public String getExternalId() {
        return externalId;
    }

    /** Является ли {@code managerEmployeeId} непосредственным руководителем сотрудника. */
    public boolean isManagedBy(Long managerEmployeeId) {
        return manager != null && manager.getId().equals(managerEmployeeId);
    }
}
