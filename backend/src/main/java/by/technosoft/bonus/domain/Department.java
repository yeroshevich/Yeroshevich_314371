package by.technosoft.bonus.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** Подразделение (таблица department). */
@Entity
@Table(name = "department")
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Руководитель подразделения (связь 1:1 «возглавляет»). */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "head_employee_id")
    private Employee head;

    protected Department() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Employee getHead() {
        return head;
    }
}
