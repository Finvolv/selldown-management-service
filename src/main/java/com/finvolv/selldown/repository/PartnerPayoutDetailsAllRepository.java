package com.finvolv.selldown.repository;

import com.finvolv.selldown.model.PartnerPayoutDetailsAll;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Repository
public interface PartnerPayoutDetailsAllRepository extends ReactiveCrudRepository<PartnerPayoutDetailsAll, Long> {
    
    Flux<PartnerPayoutDetailsAll> findByLmsId(Long lmsId);
    
    Flux<PartnerPayoutDetailsAll> findByDealStatusId(Long dealStatusId);
    
    Flux<PartnerPayoutDetailsAll> findByLmsIdAndDealStatusId(Long lmsId, Long dealStatusId);
    
    Flux<PartnerPayoutDetailsAll> findByLmsLan(String lmsLan);
    
    @Query("SELECT * FROM \"sd-partner_payout_details_all\" WHERE lms_id = :lmsId AND lms_lan = :lmsLan")
    Mono<PartnerPayoutDetailsAll> findByLmsIdAndLmsLan(Long lmsId, String lmsLan);
    
    @Query("SELECT * FROM \"sd-partner_payout_details_all\" WHERE lms_id = :lmsId AND lms_lan = :lmsLan ORDER BY id DESC")
    Flux<PartnerPayoutDetailsAll> findAllByLmsIdAndLmsLan(Long lmsId, String lmsLan);
    
    @Query("DELETE FROM \"sd-partner_payout_details_all\" WHERE lms_id = :lmsId")
    Mono<Void> deleteByLmsId(Long lmsId);
    
    @Query("DELETE FROM \"sd-partner_payout_details_all\" WHERE deal_status_id = :dealStatusId")
    Mono<Void> deleteByDealStatusId(Long dealStatusId);
    
    @Query("UPDATE \"sd-partner_payout_details_all\" SET deal_status_id = :dealStatusId WHERE lms_lan = :lmsLan")
    Mono<Integer> updateDealStatusIdByLmsLan(String lmsLan, Long dealStatusId);
    
    @Query("UPDATE \"sd-partner_payout_details_all\" SET deal_status_id = :dealStatusId WHERE id = :id")
    Mono<Integer> updateDealStatusIdById(Long id, Long dealStatusId);
    
    @Query("UPDATE \"sd-partner_payout_details_all\" SET deal_status_id = :dealStatusId WHERE id = ANY(:ids)")
    Mono<Integer> updateDealStatusIdByIds(List<Long> ids, Long dealStatusId);
    
    @Query("SELECT * FROM \"sd-partner_payout_details_all\" WHERE lms_lan = :lmsLan AND EXTRACT(YEAR FROM cycle_start_date) = :year AND EXTRACT(MONTH FROM cycle_start_date) = :month")
    Flux<PartnerPayoutDetailsAll> findByLmsLanAndYearAndMonth(String lmsLan, Integer year, Integer month);
}
