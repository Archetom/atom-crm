package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.response.CustomerBusinessOverviewResponse;
import io.github.archetom.crm.api.facade.CustomerBusinessOverviewFacade;
import io.github.archetom.crm.application.service.CustomerBusinessOverviewService;
import io.github.archetom.crm.application.vo.CustomerBusinessOverviewVO;
import io.github.archetom.crm.shared.util.ResultUtil;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Maps the application-owned B25a projection to its public transport contract. */
@Service
@RequiredArgsConstructor
public class CustomerBusinessOverviewFacadeImpl implements CustomerBusinessOverviewFacade {

    private final CustomerBusinessOverviewService service;

    @Override
    public Result<CustomerBusinessOverviewResponse> get(AuthenticatedCaller caller, Long customerId) {
        return ResultUtil.map(service.get(caller, customerId), value ->
                new CustomerBusinessOverviewResponse()
                        .setCustomerId(value.customerId())
                        .setCards(value.cards().stream().map(this::card).toList()));
    }

    private CustomerBusinessOverviewResponse.CardResponse card(
            CustomerBusinessOverviewVO.CardVO value) {
        return new CustomerBusinessOverviewResponse.CardResponse()
                .setSource(value.source())
                .setStatus(value.status())
                .setCount(value.count())
                .setAmount(value.amount());
    }
}
