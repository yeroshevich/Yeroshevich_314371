package by.technosoft.bonus.web.mapper;

import static by.technosoft.bonus.web.mapper.ApiValues.code;
import static by.technosoft.bonus.web.mapper.ApiValues.id;

import org.springframework.stereotype.Component;

import by.technosoft.bonus.domain.BonusAccount;
import by.technosoft.bonus.service.query.AccountQueryService.AccountOverview;
import by.technosoft.bonus.service.query.AccountQueryService.AccountState;
import by.technosoft.bonus.service.query.OperationQueryService.OperationEntry;
import by.technosoft.bonus.web.dto.AccountSummaryDto;
import by.technosoft.bonus.web.dto.BonusAccountDto;
import by.technosoft.bonus.web.dto.MonthBalanceDto;
import by.technosoft.bonus.web.dto.RedemptionDto;

/** Бонусный счёт → JSON (UC1, UC3). */
@Component
public class AccountMapper {

    private final OperationMapper operationMapper;

    public AccountMapper(OperationMapper operationMapper) {
        this.operationMapper = operationMapper;
    }

    public BonusAccountDto toDto(AccountState state) {
        BonusAccount account = state.account();
        return new BonusAccountDto(id(account.getEmployee().getId()), code(account.getStatus()), account.getBalance(),
                state.pending(), account.getUpdatedAt());
    }

    public AccountSummaryDto toDto(AccountOverview overview) {
        return new AccountSummaryDto(
                toDto(overview.state()),
                overview.accruedThisMonth(),
                overview.spentThisMonth(),
                operationMapper.toDtos(overview.recent()),
                overview.history().stream().map(m -> new MonthBalanceDto(m.month().toString(), m.balance())).toList());
    }

    public RedemptionDto toDto(OperationEntry operation, AccountState state) {
        return new RedemptionDto(operationMapper.toDto(operation), toDto(state));
    }
}
