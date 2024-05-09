package com.nekromant.telegram.service;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.nekromant.telegram.MentoringReviewBot;
import com.nekromant.telegram.commands.dto.ChequeDTO;
import com.nekromant.telegram.commands.dto.LifePayResponseDTO;
import com.nekromant.telegram.commands.feign.LifePayFeign;
import com.nekromant.telegram.config.LifePayProperties;
import com.nekromant.telegram.config.PriceProperties;
import com.nekromant.telegram.contants.PayStatus;
import com.nekromant.telegram.contants.ServiceType;
import com.nekromant.telegram.model.MentoringSubscriptionRequest;
import com.nekromant.telegram.model.PaymentDetails;
import com.nekromant.telegram.repository.MentoringSubscriptionRequestRepository;
import com.nekromant.telegram.repository.PaymentDetailsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Map;

import static com.nekromant.telegram.contants.MessageContants.MENTORING_OFFER_DESCRIPTION;
import static com.nekromant.telegram.contants.MessageContants.RESPONSE_FOR_MENTORING_SUBSCRIPTION;
import static java.rmi.server.LogStream.log;

@Slf4j
@Service
public class MentoringSubscriptionRequestService {
    @Autowired
    private MentoringSubscriptionRequestRepository mentoringSubscriptionRequestRepository;
    @Autowired
    private PaymentDetailsRepository paymentDetailsRepository;
    @Autowired
    private UserInfoService userInfoService;
    @Autowired
    private MentoringReviewBot mentoringReviewBot;
    @Autowired
    private LifePayFeign lifePayFeign;
    @Autowired
    private LifePayProperties lifePayProperties;
    @Autowired
    private PriceProperties priceProperties;
    @Value("${owner.userName}")
    private String ownerUserName;

    public ResponseEntity save(Map mentoringData) {
        MentoringSubscriptionRequest mentoringSubscriptionRequest = MentoringSubscriptionRequest.builder()
                .tgName(mentoringData.get("TG-NAME").toString())
                .customerPhone(mentoringData.get("PHONE").toString())
                .build();

        ResponseEntity responseEntity = new ResponseEntity(HttpStatus.OK);

        ChequeDTO chequeDTO = new ChequeDTO(lifePayProperties.getLogin(),
                lifePayProperties.getApikey(),
                priceProperties.getMentoringSubscription(),
                MENTORING_OFFER_DESCRIPTION,
                mentoringData.get("PHONE").toString(),
                lifePayProperties.getMethod());

        try {
            MentoringSubscriptionRequestService.log.info("Sending request to LifePay" + chequeDTO);
            LifePayResponseDTO lifePayResponse = new Gson().fromJson(lifePayFeign.payCheque(chequeDTO).getBody(), LifePayResponseDTO.class);
            MentoringSubscriptionRequestService.log.info("LifePay response: " + lifePayResponse);

            PaymentDetails paymentDetails = PaymentDetails.builder()
                    .number(lifePayResponse.getData().getNumber())
                    .status(PayStatus.UNREDEEMED)
                    .serviceType(ServiceType.MENTORING)
                    .build();
            paymentDetailsRepository.save(paymentDetails);
            MentoringSubscriptionRequestService.log.info("Unredeemed payment created: " + paymentDetails);

            mentoringSubscriptionRequest.setLifePayTransactionNumber(lifePayResponse.getData().getNumber());
            mentoringSubscriptionRequestRepository.save(mentoringSubscriptionRequest);
            MentoringSubscriptionRequestService.log.info("New mentoring subscription request created: " + mentoringSubscriptionRequest);
        } catch (JsonParseException jsonParseException) {
            log("Erorr while parsing Json: " + jsonParseException.getMessage());
            responseEntity = new ResponseEntity(HttpStatus.BAD_REQUEST);
        } catch (DataAccessException dataAccessException) {
            log("Error while accessing database: " + dataAccessException.getMessage());
            responseEntity = new ResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return responseEntity;
    }


    public void sendClientToMentorForSubscription(PaymentDetails paymentDetails) {
        paymentDetailsRepository.save(paymentDetails);
        MentoringSubscriptionRequestService.log.info("Payment details have been redeemed:" + paymentDetails);

        String receiverId = userInfoService.getUserInfo(ownerUserName).getChatId().toString();

        String text = String.format(RESPONSE_FOR_MENTORING_SUBSCRIPTION,
                paymentDetails.getNumber(),
                paymentDetails.getPhone(),
                mentoringSubscriptionRequestRepository.findByLifePayTransactionNumber(paymentDetails.getNumber()).getTgName());

        mentoringReviewBot.sendMessage(receiverId, text);
        MentoringSubscriptionRequestService.log.info(text);
    }

    public void RejectApplication(PaymentDetails paymentDetails) {
        paymentDetailsRepository.save(paymentDetails);
        MentoringSubscriptionRequestService.log.info("Payment failed: " + paymentDetails);
    }
}