package by.technosoft.bonus.service.query;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import by.technosoft.bonus.domain.AppUser;
import by.technosoft.bonus.repository.AppUserRepository;
import by.technosoft.bonus.service.exception.ObjectNotFoundException;

/** Чтение учётных записей (UC12). */
@Service
@Transactional(readOnly = true)
public class UserQueryService {

    private final AppUserRepository userRepository;

    public UserQueryService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Все пользователи или, если задан email, совпадение по email (проверка дубля перед созданием). */
    public List<AppUser> list(String email) {
        if (email != null && !email.isBlank()) {
            return userRepository.findByEmployeeEmailIgnoreCase(email.trim()).stream().toList();
        }
        return userRepository.findAllWithDetails();
    }

    public AppUser get(Long id) {
        return userRepository.findWithRoleById(id)
                .orElseThrow(() -> new ObjectNotFoundException("Пользователь не найден. Обновите список."));
    }
}
