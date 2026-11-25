package by.technosoft.bonus.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Программа мотивации, на которую тратятся баллы (таблица program, UC3). */
@Entity
@Table(name = "program")
public class Program {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "category", nullable = false, length = 15)
    private ProgramCategory category;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    /** Стоимость в баллах. */
    @Column(name = "cost", nullable = false)
    private int cost;

    @Column(name = "active", nullable = false)
    private boolean active;

    /** Пояснение, почему программа недоступна. */
    @Column(name = "inactive_reason", length = 200)
    private String inactiveReason;

    protected Program() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ProgramCategory getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public int getCost() {
        return cost;
    }

    public boolean isActive() {
        return active;
    }

    public String getInactiveReason() {
        return inactiveReason;
    }
}
