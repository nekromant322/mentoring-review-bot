package com.nekromant.telegram.commands;

import com.nekromant.telegram.commands.feign.LifePayFeign;
import com.nekromant.telegram.commands.dto.ChequeDTO;
import com.nekromant.telegram.exception.ParsingUrlException;
import com.nekromant.telegram.model.Contract;
import com.nekromant.telegram.service.ContractService;
import com.nekromant.telegram.utils.ValidationUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.bots.AbsSender;

import javax.management.InstanceNotFoundException;
import static com.nekromant.telegram.contants.Command.PAY;

@Component
@Slf4j
public class PayCommand extends MentoringReviewCommand {
    @Value("${pay-info.login}")
    private String login;
    @Value("${pay-info.apikey}")
    private String apikey;
    @Value("${pay-info.method}")
    private String method;
    @Autowired
    private ContractService contractService;
    @Autowired
    private LifePayFeign lifePayFeign;


    @Autowired
    public PayCommand() {
        super(PAY.getAlias(), PAY.getDescription());
    }

    @Override
    public void execute(AbsSender absSender, User user, Chat chat, String[] arguments) {
        SendMessage message = new SendMessage();
        String studentChatId = chat.getId().toString();
        message.setChatId(studentChatId);

        try {
            ValidationUtils.validateArgumentsNumber(arguments);
            ChequeDTO chequeDTO = new ChequeDTO(
                    login,
                    apikey,
                    parseAmount(arguments),
                    createDescription(user),
                    parseCustomerPhone(arguments),
                    method);

            log.info(String.valueOf(chequeDTO));
            log.info("Sending request to LifePay");
            String lifePayResponse = lifePayFeign.payCheque(chequeDTO).getBody();
            log.info("LifePay response: " + lifePayResponse);
            String paymentUrl = parseUrl(lifePayResponse);
            log.info("PaymentURL: " + paymentUrl);

            message.enableMarkdownV2(true);
            message.setText("Отправлено SMS\\-сообщение со счетом на номер " + chequeDTO.getCustomerPhone()
                    + " на сумму " + chequeDTO.getAmount() + "\n[Ссылка на оплату](" + paymentUrl + ")");

        } catch (InstanceNotFoundException e) {
            message.setText("У вас нет контракта, обратитесь к @Marandyuk_Anatolii");
            execute(absSender, message, user);
            return;
        } catch (ParsingUrlException e) {
            message.setText(e.getMessage());
            execute(absSender, message, user);
            return;
        } catch (Exception e) {
            message.setText(e.getMessage() + "\nПример: \n" +
                    "/pay <ваш номер в формате 79xxxxxxxxx> <сумма услуги в формате 5000.00>");
            execute(absSender, message, user);
            return;
        }
        execute(absSender, message, user);
    }

    private String createDescription(User user) throws InstanceNotFoundException {
        final Contract contract = contractService.getContractByUsername(user.getUserName());
        return "Оплата по договору " + contract.getContractId() + " от " +
                contract.getDate() + " за консультации по разработке ПО";
    }

    public String parseCustomerPhone(String[] arguments){
        return arguments[0];
    }

    public String parseAmount(String[] arguments){
        return validateAmount(arguments[1]);
    }

    private String validateAmount(String argument) {
        String replaced = argument.replace(",", ".");
        try {
            Double.parseDouble(replaced);
            return replaced;
        } catch (NumberFormatException e) {
            log.error("Неверный формат суммы: {}", argument);
            throw new NumberFormatException("Неверный формат суммы: + " + argument);
        }
    }

    private String parseUrl(String json) {
        int httpsIndex = json.lastIndexOf("https");
        if (httpsIndex == -1) {
            throw new ParsingUrlException("Не удалось найти ссылку на оплату в ответе от LifePay. Попробуйте ввести команду снова");
        }
        return json.substring(httpsIndex, json.lastIndexOf("\"")).replace("\\", "");
    }

}
