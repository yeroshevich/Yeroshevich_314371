package by.technosoft.bonus.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Роль ролевой модели (таблица role, SEC-3). */
@Entity
@Table(name = "role")
public class Role {

    @Id
    @Column(name = "code", length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    protected Role() {
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    /** Код роли как перечисление. */
    public RoleCode toRoleCode() {
        return PersistentEnum.fromDb(RoleCode.class, code);
    }
}
