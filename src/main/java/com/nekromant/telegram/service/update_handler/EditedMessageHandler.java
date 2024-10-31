package com.nekromant.telegram.service.update_handler;

import com.nekromant.telegram.commands.report.ReportDateTimePicker;
import com.nekromant.telegram.contants.ChatType;
import com.nekromant.telegram.model.ChatMessage;
import com.nekromant.telegram.model.Report;
import com.nekromant.telegram.service.ChatMessageService;
import com.nekromant.telegram.service.ReportService;
import com.nekromant.telegram.service.SendMessageService;
import com.nekromant.telegram.service.SpecialChatService;
import com.nekromant.telegram.utils.EditMessageTextFactory;
import com.nekromant.telegram.utils.SendMessageFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.security.InvalidParameterException;
import java.time.LocalDate;

import static com.nekromant.telegram.contants.Command.REPORT;
import static com.nekromant.telegram.contants.MessageContants.REPORT_HELP_MESSAGE;
import static com.nekromant.telegram.utils.FormatterUtils.defaultDateFormatter;

@Slf4j
@Component
public class EditedMessageHandler {

    @Autowired
    private SendMessageService sendMessageService;
    @Autowired
    private ChatMessageService chatMessageService;
    @Autowired
    private ReportService reportService;
    @Autowired
    private SendMessageFactory sendMessageFactory;
    @Autowired
    private SpecialChatService specialChatService;
    @Autowired
    private ReportDateTimePicker reportDateTimePicker;
    @Autowired
    private EditMessageTextFactory editMessageTextFactory;


    public void handleEditedMessage(Message message) {
        if (isReportEdited(message)) {
            handleEditedReport(message);
        }
    }

    private static boolean isReportEdited(Message message) {
        return message.hasText() && message.getText().split(" ")[0].equals("/" + REPORT.getAlias());
    }

    private void handleEditedReport(Message message) {
        log.info("Сообщение с отчётом отредактировано пользователем: {} (user id: {})", message.getFrom().getUserName(), message.getFrom().getId());
        String editedText = message.getText();
        Integer reportMessageId = message.getMessageId();
        ChatMessage reportChatMessage = chatMessageService.findChatMessageByUserMessageId(reportMessageId);

        try {
            if (reportChatMessage != null) {
                handleExistingMessage(message, reportChatMessage, editedText);
            } else {
                handleNewMessage(message);
            }
        } catch (TelegramApiException e) {
            log.error("Не удалось обработать сообщение отредактированное пользователем: {} (user id: {}). Возникла ошибка при отправке сообщения пользователю {}", message.getFrom().getUserName(), message.getFrom().getId(), e.getMessage(), e);
        }
    }

    private void handleExistingMessage(Message message, ChatMessage reportChatMessage, String editedText) throws TelegramApiException {
        Report report = reportChatMessage.getReport();
        if (report != null) {
            log.info("К текущему сообщению (message id: {}) привязан отчёт", message.getMessageId());
            handleExistingReport(message, reportChatMessage, editedText, report);
        } else {
            log.info("К текущему сообщению (message id: {}) не привязан отчёт", message.getMessageId());
            handleNewReportForExistingMessage(message);
        }
    }

    private void handleNewMessage(Message message) throws TelegramApiException {
        try {
            Report temporaryReport = reportService.getTemporaryReport(message);
            reportService.save(temporaryReport);

            SendMessage sendDatePicker = reportDateTimePicker.getDatePickerSendMessage(
                    temporaryReport,
                    message.getMessageId());

            sendMessageService.sendMessage(sendMessageFactory.create(message.getChatId().toString(),
                    "Не удалось найти редактируемое сообщение-отчёт в БД.\n" +
                            "Нажмите \"отмена\" или укажите дату нового отчёта.\n" +
                            "Если отчёт с такой датой существует, то он будет обновлён согласно данным в отредактированном сообщении."));
            sendMessageService.sendMessage(sendDatePicker);
            log.info("Отправлено сообщение с выбором даты отчёта");
        }  catch (InvalidParameterException e) {
            log.error("Пользователем {} (user id: {}) был передан невалидный отчёт ({}) {}", message.getFrom().getUserName(), message.getFrom().getId(), message.getText(), e.getMessage(), e);
            sendMessageService.sendMessage(sendMessageFactory.create(message.getChatId().toString(), e.getMessage() + "\n" + REPORT_HELP_MESSAGE));
        }
    }

    private void handleExistingReport(Message message, ChatMessage reportChatMessage, String editedText, Report report) {
        Integer userChatBotMessageId = reportChatMessage.getUserChatBotMessageId();
        Integer reportChatBotMessageId = reportChatMessage.getReportChatBotMessageId();

        reportService.updateReportFromEditedMessage(editedText, report);
        reportService.save(report);


        String updatedReportText = String.format("Отчёт обновлен %s:\n@%s\n%s\n%s\n%s",
                LocalDate.now().format(defaultDateFormatter()),
                report.getUserInfo().getUserName(),
                report.getDate().format(defaultDateFormatter()),
                report.getHours(),
                report.getTitle()
        );

        updateReportBotMessages(message, reportChatMessage, updatedReportText, userChatBotMessageId, reportChatBotMessageId);
    }

    private void handleNewReportForExistingMessage(Message message) throws TelegramApiException {
        try {
            Report temporaryReport = reportService.getTemporaryReport(message);
            reportService.save(temporaryReport);

            SendMessage sendDatePicker = reportDateTimePicker.getDatePickerSendMessage(
                    temporaryReport,
                    message.getMessageId());

            sendMessageService.sendMessage(sendMessageFactory.create(message.getChatId().toString(),
                    "К сообщению не привязан отчёт.\n" +
                            "Нажмите \"отмена\" или укажите дату нового отчёта.\n" +
                            "Если отчёт с такой датой существует, то он будет обновлён согласно данным в отредактированном сообщении."));
            sendMessageService.sendMessage(sendDatePicker);
            log.info("Отправлено сообщение с выбором даты отчёта");
        }  catch (InvalidParameterException e) {
            log.error("Пользователем {} (user id: {}) был передан невалидный отчёт ({}) {}", message.getFrom().getUserName(), message.getFrom().getId(), message.getText(), e.getMessage(), e);
            sendMessageService.sendMessage(sendMessageFactory.create(message.getChatId().toString(), e.getMessage() + "\n" + REPORT_HELP_MESSAGE));
        }
    }

    private void updateReportBotMessages(Message message, ChatMessage reportChatMessage, String updatedReportText, Integer userChatBotMessageId, Integer reportChatBotMessageId) {
        updateReportBotMessage(message, reportChatMessage, updatedReportText, userChatBotMessageId, ChatType.USER_CHAT);
        updateReportBotMessage(message, reportChatMessage, updatedReportText, reportChatBotMessageId, ChatType.REPORTS_CHAT);
    }

    private void updateReportBotMessage(Message message, ChatMessage chatMessage, String updatedReportText, Integer messageId, ChatType chatType) {
        try {
            sendMessageService.sendMessage(editMessageTextFactory.create(getChatId(message, chatType), messageId, updatedReportText));
        } catch (TelegramApiException e) {
            if (isMessageNotFound(e)) {
                sendNewMessage(message, chatMessage, updatedReportText, chatType);
            } else {
                log.error("Связанное с редактируемым отчётом сообщение (message id: {}) не было найдено в чате отчётов и не удалось отправить новое {}", messageId, e.getMessage(), e);
            }
        }
    }

    private String getChatId(Message message, ChatType chatType) {
        switch (chatType) {
            case USER_CHAT:
                return message.getChatId().toString();
            case REPORTS_CHAT:
                return specialChatService.getReportsChatId();
            default:
                throw new UnsupportedOperationException("Unsupported chat type");
        }
    }

    private boolean isMessageNotFound(TelegramApiException e) {
        return e.getMessage().contains("message to edit not found") || e.getMessage().contains("MessageId parameter can't be empty");
    }

    private void sendNewMessage(Message message, ChatMessage chatMessage, String updatedReportText, ChatType chatType) {
        try {
            SendMessage sendMessage = sendMessageFactory.create(getChatId(message, chatType), updatedReportText);
            Message newMessage = sendMessageService.sendMessage(sendMessage);
            updateChatMessageId(chatType, chatMessage, newMessage.getMessageId());
            chatMessageService.save(chatMessage);
        } catch (TelegramApiException e) {
            log.error("Связанное с редактируемым отчётом сообщение (message id: {}) не было найдено в чате отчётов и не удалось отправить новое {}", message.getMessageId(), e.getMessage(), e);
        }
    }

    private void updateChatMessageId(ChatType chatType, ChatMessage chatMessage, int newMessageId) {
        if (chatType == ChatType.REPORTS_CHAT) {
            chatMessage.setReportChatBotMessageId(newMessageId);
        } else if (chatType == ChatType.USER_CHAT) {
            chatMessage.setUserChatBotMessageId(newMessageId);
        }
    }
}
