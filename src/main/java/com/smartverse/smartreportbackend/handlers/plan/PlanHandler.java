package com.smartverse.smartreportbackend.handlers.plan;

import com.smartverse.smartreportbackend.services.plan.PlanBusinessService;
import com.smartverse.smartreportbackend_gen.endpoints.GetApiUsageHistory;
import com.smartverse.smartreportbackend_gen.endpoints.GetApiUsageHistoryOutput;
import com.smartverse.smartreportbackend_gen.endpoints.GetPlanOverview;
import com.smartverse.smartreportbackend_gen.endpoints.GetPlanOverviewOutput;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
public class PlanHandler implements GetPlanOverview, GetApiUsageHistory {
    private final PlanBusinessService service;

    public PlanHandler(PlanBusinessService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<GetPlanOverviewOutput> getPlanOverview() {
        return ResponseEntity.ok(service.overview());
    }
    @Override
    public ResponseEntity<GetApiUsageHistoryOutput> getApiUsageHistory() {
        return ResponseEntity.ok(service.history());
    }
}
