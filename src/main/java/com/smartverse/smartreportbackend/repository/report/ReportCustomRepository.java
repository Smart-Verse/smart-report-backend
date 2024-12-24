package com.smartverse.smartreportbackend.repository.report;

import com.smartverse.smartreportbackend_gen.ReportRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Primary
@Repository
public interface ReportCustomRepository extends ReportRepository {

    @Query(nativeQuery = true, value = "select sum(amount) as quantidade from report ")
    Long sumAmount();
}
